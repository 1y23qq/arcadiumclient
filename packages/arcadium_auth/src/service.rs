use std::collections::HashMap;
use std::sync::{Arc, Mutex as StdMutex};

use arcadium_events::EventBus;
use arcadium_net::RequestClient;
use tokio::sync::Mutex;
use tokio_util::sync::CancellationToken;
use uuid::Uuid;

use crate::data::{MicrosoftLoginSession, MinecraftAccount};
use crate::error::{AuthError, AuthResult, MinecraftAuthError};
use crate::msa::{self, PendingBrowserLogin};
use crate::store::{self, CredentialsStore};

/// Stable id for the Microsoft login's progress so a front-end can recognise
/// it among everything else on the bus
pub const MICROSOFT_LOGIN_PROGRESS: Uuid =
    Uuid::from_u128(0x4D53_4141_5554_4800_0000_0000_0000_0001);

/// `finish_microsoft_login` takes the listener and runs with it while the
/// token stays here so a cancel from the UI has something to flip
struct PendingLogin {
    /// `None` once a flow owns the listener or once a cancel freed the port
    /// before any flow got that far
    browser: Option<PendingBrowserLogin>,
    cancel: CancellationToken,
    cookie_source: Option<Uuid>,
}

pub struct CookieLogin {
    cookies: crate::cookie::ImportedCookies,
    source: Uuid,
}

pub struct VerifiedCookieAccount(MinecraftAccount);

pub struct CookieBrowserLogin {
    session: MicrosoftLoginSession,
    source: Uuid,
}

impl CookieBrowserLogin {
    pub fn dedupe_key(&self) -> &str {
        self.session.dedupe_key()
    }
}

impl CookieLogin {
    pub fn consent_request(
        &self,
        session: &CookieBrowserLogin,
    ) -> AuthResult<crate::CookieConsentRequest> {
        if session.source != self.source {
            return Err(crate::CookieImportError::AuthorizationRejected.into());
        }
        Ok(self.cookies.consent_request(&session.session.browser)?)
    }
}

pub struct AuthService {
    store: Mutex<CredentialsStore>,
    /// Keyed by CSRF state token
    pending_logins: Mutex<HashMap<String, PendingLogin>>,
    /// Serialises token renewal per account Microsoft rotates the refresh token
    /// on every use so concurrent renewals would sign the account out
    refresh_guards: StdMutex<HashMap<Uuid, Arc<Mutex<()>>>>,
    net: RequestClient,
    events: EventBus,
}

impl AuthService {
    pub async fn import_cookie_account(
        &self,
        path: &std::path::Path,
        cancel: &CancellationToken,
    ) -> AuthResult<MinecraftAccount> {
        let pending = tokio::select! {
            biased;
            () = cancel.cancelled() => return Err(AuthError::LoginCancelled),
            result = self.begin_cookie_login(path) => result?,
        };
        self.finish_cookie_login(&pending, cancel).await
    }

    pub async fn begin_cookie_login(&self, path: &std::path::Path) -> AuthResult<CookieLogin> {
        Ok(CookieLogin {
            cookies: crate::cookie::ImportedCookies::read(path).await?,
            source: Uuid::new_v4(),
        })
    }

    async fn cookie_account(
        &self,
        pending: &CookieLogin,
        cancel: &CancellationToken,
    ) -> AuthResult<MinecraftAccount> {
        let events = self.events.clone();
        let flow = async {
            msa::login_with_cookies(
                self.net.http(),
                pending.cookies.clone(),
                |label, current, total| {
                    events.progress(MICROSOFT_LOGIN_PROGRESS, label, current, total);
                },
            )
            .await
        };
        tokio::select! {
            biased;
            () = cancel.cancelled() => Err(AuthError::LoginCancelled),
            result = flow => result,
        }
    }

    async fn commit_cookie_account(
        &self,
        account: MinecraftAccount,
        cancel: &CancellationToken,
    ) -> AuthResult<MinecraftAccount> {
        if cancel.is_cancelled() {
            return Err(AuthError::LoginCancelled);
        }
        let mut store = self.store.lock().await;
        if cancel.is_cancelled() {
            return Err(AuthError::LoginCancelled);
        }
        store.commit_account(account, &self.events).await
    }

    pub async fn finish_cookie_login(
        &self,
        pending: &CookieLogin,
        cancel: &CancellationToken,
    ) -> AuthResult<MinecraftAccount> {
        let account = self.cookie_account(pending, cancel).await?;
        self.commit_cookie_account(account, cancel).await
    }

    pub async fn authorize_cookie_browser_login(
        &self,
        pending: &CookieLogin,
        session: CookieBrowserLogin,
        cancel: &CancellationToken,
        on_verifying: impl FnOnce(),
    ) -> AuthResult<VerifiedCookieAccount> {
        if session.source != pending.source {
            return Err(crate::CookieImportError::AuthorizationRejected.into());
        }
        let state = session.dedupe_key().to_string();
        let browser = tokio::select! {
            biased;
            () = cancel.cancelled() => {
                self.cancel_microsoft_login(&state).await;
                self.pending_logins.lock().await.remove(&state);
                return Err(AuthError::LoginCancelled);
            }
            result = self.authorize_microsoft_session(session.session, Some(pending.source)) => result?,
        };
        on_verifying();
        if browser.id.is_nil() {
            return Err(crate::CookieImportError::AuthorizationRejected.into());
        }
        Ok(VerifiedCookieAccount(browser))
    }

    pub async fn commit_cookie_browser_login(
        &self,
        verified: VerifiedCookieAccount,
        cancel: &CancellationToken,
    ) -> AuthResult<MinecraftAccount> {
        self.commit_cookie_account(verified.0, cancel).await
    }

    /// A missing or unreadable `auth.json` yields an empty store rather than
    /// failing so a corrupt file means "sign in again" not "will not start"
    pub async fn load(net: RequestClient, events: EventBus) -> AuthResult<Self> {
        Ok(Self::with_store(
            CredentialsStore::load().await?,
            net,
            events,
        ))
    }

    #[must_use]
    pub fn with_store(store: CredentialsStore, net: RequestClient, events: EventBus) -> Self {
        Self {
            store: Mutex::new(store),
            pending_logins: Mutex::new(HashMap::new()),
            refresh_guards: StdMutex::new(HashMap::new()),
            net,
            events,
        }
    }

    #[tracing::instrument(skip_all)]
    pub async fn begin_microsoft_login(&self) -> AuthResult<MicrosoftLoginSession> {
        tracing::info!("beginning Microsoft login");
        let client = self.net.http();

        let (browser, pending) = msa::begin_browser_login().await?;
        let device = if arcadium_common::constants::MICROSOFT_CLIENT_ID
            == "42a60a84-599d-44b2-a7c6-b00cdef1d6a2"
        {
            crate::DeviceCodeLogin::browser_only()
        } else {
            match msa::begin_device_login(client).await {
                Ok(device) => device,
                Err(error) => {
                    tracing::warn!(%error, "Device-code fallback unavailable; continuing browser sign-in");
                    crate::DeviceCodeLogin::browser_only()
                }
            }
        };

        let mut logins = self.pending_logins.lock().await;
        // A login cancelled before its flow ever started has nobody left to remove
        // it so the next login sweeps it up
        logins.retain(|_, login| !login.cancel.is_cancelled());
        logins.insert(
            browser.state.clone(),
            PendingLogin {
                browser: Some(pending),
                cancel: CancellationToken::new(),
                cookie_source: None,
            },
        );

        Ok(MicrosoftLoginSession { browser, device })
    }

    pub async fn begin_cookie_browser_login(
        &self,
        source: &CookieLogin,
    ) -> AuthResult<CookieBrowserLogin> {
        let (mut browser, pending) = msa::begin_browser_login().await?;
        let mut url = url::Url::parse(&browser.auth_url)
            .map_err(|_| crate::CookieImportError::AuthorizationRejected)?;
        let pairs: Vec<_> = url
            .query_pairs()
            .filter(|(key, _)| key != "prompt")
            .map(|(key, value)| (key.into_owned(), value.into_owned()))
            .collect();
        url.set_query(None);
        url.query_pairs_mut().extend_pairs(pairs);
        browser.auth_url = url.to_string();
        let mut logins = self.pending_logins.lock().await;
        logins.retain(|_, login| !login.cancel.is_cancelled());
        logins.insert(
            browser.state.clone(),
            PendingLogin {
                browser: Some(pending),
                cancel: CancellationToken::new(),
                cookie_source: Some(source.source),
            },
        );
        Ok(CookieBrowserLogin {
            session: MicrosoftLoginSession {
                browser,
                device: crate::DeviceCodeLogin::browser_only(),
            },
            source: source.source,
        })
    }

    /// Only one caller can ever claim a login
    async fn claim_pending_login(
        &self,
        state_token: &str,
        cookie_source: Option<Uuid>,
    ) -> AuthResult<(PendingBrowserLogin, CancellationToken)> {
        let mut logins = self.pending_logins.lock().await;

        // Cancelled between `begin` and here the listener is already gone and
        // there is nothing to run
        if logins
            .get(state_token)
            .is_some_and(|login| login.cancel.is_cancelled())
        {
            logins.remove(state_token);
            return Err(AuthError::LoginCancelled);
        }

        let login = logins.get_mut(state_token).ok_or(AuthError::Minecraft(
            MinecraftAuthError::BrowserLoginNotFound,
        ))?;
        if login.cookie_source != cookie_source {
            return Err(MinecraftAuthError::BrowserLoginNotFound.into());
        }
        let cancel = login.cancel.clone();
        let browser = login.browser.take().ok_or(AuthError::Minecraft(
            MinecraftAuthError::BrowserLoginNotFound,
        ))?;

        Ok((browser, cancel))
    }

    #[tracing::instrument(skip_all)]
    pub async fn finish_microsoft_login(
        &self,
        session: MicrosoftLoginSession,
    ) -> AuthResult<MinecraftAccount> {
        let account = self.authorize_microsoft_session(session, None).await?;
        tracing::info!(username = %account.username, "Microsoft login succeeded");
        self.store
            .lock()
            .await
            .commit_account(account, &self.events)
            .await
    }

    async fn authorize_microsoft_session(
        &self,
        session: MicrosoftLoginSession,
        cookie_source: Option<Uuid>,
    ) -> AuthResult<MinecraftAccount> {
        tracing::info!("finishing Microsoft login");

        let (pending, cancel) = self
            .claim_pending_login(&session.browser.state, cookie_source)
            .await?;

        let events = self.events.clone();
        let flow = msa::finish_dual_login(
            self.net.http(),
            pending,
            &session.device,
            |label, current, total| {
                events.progress(MICROSOFT_LOGIN_PROGRESS, label, current, total);
            },
        );

        // Losing the race drops `flow` and with it the device-code poll the
        // loopback listener and whichever request was mid-flight
        let result = tokio::select! {
            biased;
            () = cancel.cancelled() => Err(AuthError::LoginCancelled),
            res = flow => res.map_err(AuthError::from),
        };

        // Whoever runs the flow owns the cleanup a cancel only flips the token
        self.pending_logins
            .lock()
            .await
            .remove(&session.browser.state);

        // Drives the card to 100% so the front-end can clear it a cancelled flow
        // needs this as much as a successful one
        self.events
            .progress(MICROSOFT_LOGIN_PROGRESS, "Signed in", 1, 1);

        // Re-read rather than trusting `result` the last handshake step can
        // complete in the same breath as a cancel
        if cancel.is_cancelled() {
            tracing::info!("Microsoft login cancelled by the user");
            return Err(AuthError::LoginCancelled);
        }

        if let Err(err) = &result {
            let mut chain = String::new();
            let mut source = std::error::Error::source(err);
            while let Some(cause) = source {
                chain.push_str(&format!("\n  caused by: {cause}"));
                source = cause.source();
            }
            tracing::warn!("Microsoft login failed: {err}{chain}");
        }

        result
    }

    /// The entry is deliberately left in the map `finish_microsoft_login` may
    /// not have claimed it yet and must find the cancelled token not a hole
    #[tracing::instrument(level = "debug", skip_all)]
    pub async fn cancel_microsoft_login(&self, state_token: &str) {
        let mut logins = self.pending_logins.lock().await;
        let Some(login) = logins.get_mut(state_token) else {
            return;
        };

        login.cancel.cancel();
        // Frees the port now if no flow ever took the listener once one has the
        // flow drops it as it unwinds
        login.browser = None;
    }

    #[tracing::instrument(skip(self), fields(username = %username))]
    pub async fn add_offline_account(&self, username: String) -> AuthResult<MinecraftAccount> {
        self.store
            .lock()
            .await
            .add_offline_account_and_save(username)
            .await
    }

    pub async fn add_session_account(
        &self,
        access_token: &str,
        cancel: &CancellationToken,
    ) -> AuthResult<MinecraftAccount> {
        let account = tokio::select! {
            biased;
            () = cancel.cancelled() => return Err(AuthError::LoginCancelled),
            result = crate::session::validate_session(access_token) => result?,
        };
        if cancel.is_cancelled() {
            return Err(AuthError::LoginCancelled);
        }
        let mut store = self.store.lock().await;
        if cancel.is_cancelled() {
            return Err(AuthError::LoginCancelled);
        }
        store.commit_account(account, &self.events).await
    }

    pub async fn list_accounts(&self) -> Vec<MinecraftAccount> {
        let mut store = self.store.lock().await;
        if let Ok(current) = CredentialsStore::load().await {
            *store = current;
        }
        store.list_accounts()
    }

    pub async fn get_account(&self, id: Uuid) -> Option<MinecraftAccount> {
        let mut store = self.store.lock().await;
        if let Ok(current) = CredentialsStore::load().await {
            *store = current;
        }
        store.get_account(id).cloned()
    }

    #[tracing::instrument(level = "debug", skip_all)]
    pub async fn default_account(&self) -> AuthResult<Option<MinecraftAccount>> {
        let mut store = self.store.lock().await;
        *store = CredentialsStore::load().await?;
        store.default_account().await
    }

    #[tracing::instrument(level = "debug", skip(self), fields(?id))]
    pub async fn set_default_account(&self, id: Option<Uuid>) -> AuthResult<()> {
        self.store.lock().await.set_default_user(id).await
    }

    #[tracing::instrument(skip(self), fields(%id))]
    pub async fn remove_account(&self, id: Uuid) -> AuthResult<()> {
        self.store.lock().await.remove_account(id).await?;
        Ok(())
    }

    pub async fn has_microsoft_account(&self) -> bool {
        self.list_accounts()
            .await
            .iter()
            .any(MinecraftAccount::is_microsoft)
    }

    fn refresh_guard(&self, id: Uuid) -> Arc<Mutex<()>> {
        Arc::clone(
            self.refresh_guards
                .lock()
                .expect("refresh guard registry poisoned")
                .entry(id)
                .or_default(),
        )
    }

    async fn account_snapshot(&self, id: Uuid) -> AuthResult<MinecraftAccount> {
        let mut store = self.store.lock().await;
        *store = CredentialsStore::load().await?;
        store
            .get_account(id)
            .cloned()
            .ok_or(AuthError::AccountNotFound(id))
    }

    /// The store lock is never held across the Microsoft handshake only around
    /// the reads and the final write
    #[tracing::instrument(level = "debug", skip(self), fields(%id))]
    async fn renew_token(&self, id: Uuid, force: bool) -> AuthResult<MinecraftAccount> {
        let existing = self.account_snapshot(id).await?;
        if existing.kind == crate::AccountKind::Session {
            let verified = crate::session::validate_session(&existing.access_token).await?;
            if verified.id != existing.id {
                return Err(crate::SessionLoginError::InvalidProfile.into());
            }
            self.store
                .lock()
                .await
                .commit_refreshed_account(verified.clone())
                .await?;
            return Ok(verified);
        }
        if !existing.is_microsoft() || (!force && !existing.is_expired()) {
            return Ok(existing);
        }

        let guard = self.refresh_guard(id);
        let _serialised = guard.lock().await;
        let _cross_process = store::refresh_lock(id).await?;

        // Re-read under the guard whoever held it may have just refreshed
        let existing = self.account_snapshot(id).await?;
        if !existing.is_microsoft() || (!force && !existing.is_expired()) {
            return Ok(existing);
        }

        tracing::info!(username = %existing.username, "renewing Microsoft access token");
        match msa::refresh_microsoft_account(self.net.http(), &existing).await {
            Ok(refreshed) => {
                self.store
                    .lock()
                    .await
                    .commit_refreshed_account(refreshed.clone())
                    .await?;
                Ok(refreshed)
            }
            Err(err) => {
                let err = AuthError::from(err);
                if store::is_transient_auth_error(&err) {
                    tracing::warn!("keeping existing token after transient renewal failure: {err}");
                    Ok(existing)
                } else {
                    Err(err)
                }
            }
        }
    }

    #[tracing::instrument(level = "debug", skip(self), fields(%id))]
    pub async fn refresh_account(&self, id: Uuid) -> AuthResult<MinecraftAccount> {
        self.renew_token(id, true).await
    }

    #[tracing::instrument(level = "debug", skip_all)]
    pub async fn refresh_all_accounts(&self) -> AuthResult<Vec<MinecraftAccount>> {
        let ids: Vec<Uuid> = self.store.lock().await.users.keys().copied().collect();
        let mut refreshed = Vec::with_capacity(ids.len());

        for id in ids {
            refreshed.push(self.renew_token(id, true).await?);
        }

        Ok(refreshed)
    }

    #[tracing::instrument(level = "debug", skip(self), fields(%id))]
    pub async fn account_for_launch(&self, id: Uuid) -> AuthResult<MinecraftAccount> {
        let account = self.renew_token(id, false).await?;

        Ok(account)
    }

    #[tracing::instrument(level = "debug", skip_all)]
    pub async fn default_account_for_launch(&self) -> AuthResult<Option<MinecraftAccount>> {
        let Some(id) = self.default_account().await?.map(|account| account.id) else {
            return Ok(None);
        };
        Ok(Some(self.account_for_launch(id).await?))
    }
}

#[cfg(test)]
mod tests {
    use arcadium_net::NetConfig;

    use super::*;
    use crate::data::DeviceCodeLogin;

    fn service(events: EventBus) -> AuthService {
        let net = RequestClient::new(NetConfig::default()).expect("net client");
        AuthService::with_store(CredentialsStore::default(), net, events)
    }

    fn cookie_fixture() -> CookieLogin {
        CookieLogin {
            cookies: crate::cookie::ImportedCookies::parse(
                "login.live.com\tFALSE\t/\tTRUE\t0\tsession\tfixture",
            )
            .unwrap(),
            source: Uuid::new_v4(),
        }
    }

    #[tokio::test]
    async fn cancelled_session_import_never_validates_or_commits() {
        let (events, _receiver) = EventBus::channel();
        let auth = service(events);
        let cancel = CancellationToken::new();
        cancel.cancel();
        assert!(matches!(
            auth.add_session_account("fixture-access", &cancel).await,
            Err(AuthError::LoginCancelled)
        ));
        assert!(auth.store.lock().await.users.is_empty());
    }

    #[tokio::test]
    async fn native_cookie_request_and_state_are_bound_to_the_imported_source() {
        let (events, _rx) = EventBus::channel();
        let service = service(events);
        let source = cookie_fixture();
        let session = service.begin_cookie_browser_login(&source).await.unwrap();
        source
            .consent_request(&session)
            .unwrap()
            .validate()
            .unwrap();
        assert!(matches!(
            cookie_fixture().consent_request(&session),
            Err(AuthError::Cookie(
                crate::CookieImportError::AuthorizationRejected
            ))
        ));
        assert!(
            service
                .claim_pending_login(session.dedupe_key(), None)
                .await
                .is_err()
        );
        assert!(
            service
                .claim_pending_login(session.dedupe_key(), Some(source.source))
                .await
                .is_ok()
        );
        assert!(service.store.lock().await.users.is_empty());
    }

    #[tokio::test]
    async fn normal_browser_state_cannot_authorize_a_cookie_window() {
        let (events, _rx) = EventBus::channel();
        let service = service(events);
        let session = register_login(&service).await;
        assert!(
            service
                .claim_pending_login(session.dedupe_key(), Some(Uuid::new_v4()))
                .await
                .is_err()
        );
        assert!(
            service
                .claim_pending_login(session.dedupe_key(), None)
                .await
                .is_ok()
        );
        assert!(service.store.lock().await.users.is_empty());
    }

    #[tokio::test]
    async fn cancelled_native_cookie_confirmation_never_authorizes_or_commits() {
        let (events, _rx) = EventBus::channel();
        let service = service(events);
        let source = cookie_fixture();
        let session = service.begin_cookie_browser_login(&source).await.unwrap();
        let cancel = CancellationToken::new();
        cancel.cancel();
        let result = service
            .authorize_cookie_browser_login(&source, session, &cancel, || {
                panic!("cancelled flow must not verify")
            })
            .await;
        assert!(matches!(result, Err(AuthError::LoginCancelled)));
        assert!(service.pending_logins.lock().await.is_empty());
        assert!(service.store.lock().await.users.is_empty());
    }

    #[test]
    fn only_normal_microsoft_interaction_requests_start_cookie_browser_continuation() {
        assert!(crate::CookieImportError::ConsentRequired.needs_browser());
        assert!(crate::CookieImportError::InteractiveRequired.needs_browser());
        assert!(!crate::CookieImportError::ReadFile.needs_browser());
        assert!(!crate::CookieImportError::AuthorizationRejected.needs_browser());
    }

    #[tokio::test]
    async fn cancelled_cookie_import_never_reads_or_commits_an_account() {
        let (events, _rx) = EventBus::channel();
        let service = service(events);
        let cancel = CancellationToken::new();
        cancel.cancel();
        let result = service
            .import_cookie_account(std::path::Path::new("missing-cookie-export.txt"), &cancel)
            .await;
        assert!(matches!(result, Err(AuthError::LoginCancelled)));
        assert!(service.store.lock().await.users.is_empty());
    }

    /// Registers a login the way `begin_microsoft_login` does minus the device
    /// code request which is the only half that needs the network
    async fn register_login(service: &AuthService) -> MicrosoftLoginSession {
        let (browser, pending) = msa::begin_browser_login().await.expect("loopback bind");

        service.pending_logins.lock().await.insert(
            browser.state.clone(),
            PendingLogin {
                browser: Some(pending),
                cancel: CancellationToken::new(),
                cookie_source: None,
            },
        );

        MicrosoftLoginSession {
            browser,
            device: DeviceCodeLogin {
                user_code: "ABCD-EFGH".to_string(),
                device_code: "device-code".to_string(),
                verification_uri: "https://microsoft.com/link".to_string(),
                expires_in: 900,
                interval: 5,
                message: String::new(),
            },
        }
    }

    #[tokio::test]
    async fn a_cancel_that_beats_the_flow_is_still_a_cancel() {
        // A quick cancel can land before the flow claims the login
        let (events, _rx) = EventBus::channel();
        let service = service(events);
        let session = register_login(&service).await;

        service.cancel_microsoft_login(session.dedupe_key()).await;
        let err = service.finish_microsoft_login(session).await.unwrap_err();

        assert!(matches!(err, AuthError::LoginCancelled), "{err}");
        assert!(service.pending_logins.lock().await.is_empty());
        assert!(service.store.lock().await.list_accounts().is_empty());
    }

    #[tokio::test]
    async fn a_cancel_frees_the_loopback_port_it_was_holding() {
        let (events, _rx) = EventBus::channel();
        let service = service(events);
        let session = register_login(&service).await;

        service.cancel_microsoft_login(session.dedupe_key()).await;

        let logins = service.pending_logins.lock().await;
        let login = logins
            .get(session.dedupe_key())
            .expect("the entry is what a flow starting late reads the cancel from");
        assert!(login.cancel.is_cancelled());
        assert!(
            login.browser.is_none(),
            "the listener should be dropped rather than left bound"
        );
    }

    #[tokio::test]
    async fn a_cancel_reaches_a_flow_that_has_already_claimed_the_login() {
        // Once claimed the entry is the only channel a cancel has to the flow
        let (events, _rx) = EventBus::channel();
        let service = service(events);
        let session = register_login(&service).await;

        let (_listener, cancel) = service
            .claim_pending_login(session.dedupe_key(), None)
            .await
            .expect("claim");
        service.cancel_microsoft_login(session.dedupe_key()).await;

        assert!(cancel.is_cancelled());
    }

    #[tokio::test]
    async fn a_login_can_only_be_claimed_once() {
        let (events, _rx) = EventBus::channel();
        let service = service(events);
        let session = register_login(&service).await;

        service
            .claim_pending_login(session.dedupe_key(), None)
            .await
            .expect("claim");
        let claimed_again = service
            .claim_pending_login(session.dedupe_key(), None)
            .await;

        assert!(matches!(
            claimed_again,
            Err(AuthError::Minecraft(
                MinecraftAuthError::BrowserLoginNotFound
            ))
        ));
    }
}
