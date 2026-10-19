use std::io::{BufRead, Write};
use std::sync::{Arc, Mutex};

use anyhow::{Context, Result};
use arcadium_auth::{
    AuthError, AuthService, CredentialsStore, MinecraftAccount, MinecraftAuthError,
};
use arcadium_events::EventBus;
use arcadium_net::{NetConfig, RequestClient};
use serde_json::{Value, json};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt, BufReader};
use tokio::task::JoinHandle;
use tokio_util::sync::CancellationToken;
use uuid::Uuid;

pub fn requested() -> bool {
    std::env::args().any(|argument| argument == "--account-service")
}

pub fn run() -> Result<()> {
    if let Some(directory) = std::env::var_os("ARCADIUM_DATA_DIR") {
        arcadium_common::paths::set_launcher_dir(directory.into());
    }
    let runtime = tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .build()?;
    runtime.block_on(serve())
}

type Output = Arc<Mutex<std::io::Stdout>>;

struct ActiveLogin {
    state: Arc<Mutex<Option<String>>>,
    cancel: CancellationToken,
    task: JoinHandle<()>,
}

async fn cancel_login(auth: &AuthService, login: ActiveLogin) {
    login.cancel.cancel();
    let state = login.state.lock().ok().and_then(|state| state.clone());
    if let Some(state) = state {
        auth.cancel_microsoft_login(&state).await;
    }
    login.task.abort();
}

fn progress(output: &Output, id: u64, stage: &str) {
    if let Ok(mut writer) = output.lock() {
        let _ = serde_json::to_writer(
            &mut *writer,
            &json!({"id": id, "event": "progress", "stage": stage}),
        );
        let _ = writer.write_all(b"\n");
        let _ = writer.flush();
    }
}

struct ConsentWindow {
    child: Option<tokio::process::Child>,
    directory: std::path::PathBuf,
}

fn remove_consent_directory(directory: &std::path::Path) -> bool {
    if !directory.is_absolute() {
        return false;
    }
    let Ok(root) = std::env::temp_dir().canonicalize() else {
        return false;
    };
    let metadata = match std::fs::symlink_metadata(directory) {
        Ok(metadata) => metadata,
        Err(error) => return error.kind() == std::io::ErrorKind::NotFound,
    };
    if metadata.file_type().is_symlink() || !metadata.is_dir() {
        return false;
    }
    let Ok(target) = directory.canonicalize() else {
        return false;
    };
    let Some(name) = target.file_name().and_then(|name| name.to_str()) else {
        return false;
    };
    let Some(id) = name.strip_prefix("arcadium-cookie-webview-") else {
        return false;
    };
    if target.parent() != Some(root.as_path())
        || target.file_name() != directory.file_name()
        || Uuid::parse_str(id).is_err()
    {
        return false;
    }
    std::fs::remove_dir_all(target).is_ok()
        || matches!(std::fs::symlink_metadata(directory), Err(error) if error.kind() == std::io::ErrorKind::NotFound)
}

fn cleanup_consent_directory(directory: &std::path::Path) -> bool {
    for _ in 0..50 {
        if remove_consent_directory(directory) {
            return true;
        }
        std::thread::sleep(std::time::Duration::from_millis(100));
    }
    false
}

impl ConsentWindow {
    async fn open(request: &arcadium_auth::CookieConsentRequest) -> Result<Self> {
        request
            .validate()
            .map_err(|_| anyhow::anyhow!("Could not prepare the Microsoft confirmation window"))?;
        let payload = serde_json::to_vec(request)
            .map_err(|_| anyhow::anyhow!("Could not prepare Microsoft confirmation"))?;
        if payload.len() > 2 * 1024 * 1024 {
            anyhow::bail!("Cookie export is too large for Microsoft confirmation");
        }
        let directory =
            std::env::temp_dir().join(format!("arcadium-cookie-webview-{}", Uuid::new_v4()));
        std::fs::create_dir(&directory)
            .context("Could not create the isolated Microsoft confirmation window")?;
        let mut command = tokio::process::Command::new(std::env::current_exe()?);
        command
            .arg("--cookie-consent")
            .stdin(std::process::Stdio::piped())
            .stdout(std::process::Stdio::piped())
            .stderr(std::process::Stdio::null())
            .kill_on_drop(true)
            .env("ARCADIUM_COOKIE_WEBVIEW_DIR", &directory);
        for (key, _) in std::env::vars_os() {
            if key
                .to_string_lossy()
                .to_ascii_uppercase()
                .starts_with("WEBVIEW2_")
            {
                command.env_remove(key);
            }
        }
        #[cfg(windows)]
        command.creation_flags(0x08000000);
        let child = match command.spawn() {
            Ok(child) => child,
            Err(_) => {
                remove_consent_directory(&directory);
                anyhow::bail!("Could not open the Microsoft confirmation window");
            }
        };
        let mut owned = Self {
            child: Some(child),
            directory,
        };
        let stdin = owned
            .child
            .as_mut()
            .and_then(|child| child.stdin.as_mut())
            .context("Microsoft confirmation input is unavailable")?;
        stdin
            .write_all(&payload)
            .await
            .context("Could not prepare Microsoft confirmation")?;
        stdin
            .write_all(b"\n")
            .await
            .context("Could not prepare Microsoft confirmation")?;
        stdin
            .flush()
            .await
            .context("Could not prepare Microsoft confirmation")?;
        Ok(owned)
    }

    async fn lifecycle(&mut self) -> Result<()> {
        let stdout = self
            .child
            .as_mut()
            .and_then(|child| child.stdout.take())
            .context("Microsoft confirmation did not open")?;
        let mut lines = BufReader::new(stdout).lines();
        while let Some(line) = lines
            .next_line()
            .await
            .context("Microsoft confirmation closed")?
        {
            match line.as_str() {
                "READY" => {}
                "COMPLETE" => return self.wait_for_exit_and_cleanup().await,
                "CANCELLED" => anyhow::bail!("Sign-in was cancelled"),
                "UNAVAILABLE" => {
                    #[cfg(windows)]
                    anyhow::bail!(
                        "Microsoft confirmation needs an updated Microsoft Edge WebView2 Runtime. Install or update it, then retry Cookie"
                    );
                    #[cfg(not(windows))]
                    anyhow::bail!(
                        "The protected Microsoft confirmation window is unavailable. Update your system and retry Cookie"
                    );
                }
                "FAILED" => anyhow::bail!(
                    "Microsoft could not confirm this cookie session. Export a fresh cookie file and retry Cookie"
                ),
                _ => anyhow::bail!("Microsoft confirmation returned an invalid response"),
            }
        }
        anyhow::bail!("Microsoft confirmation closed before finishing")
    }

    async fn wait_for_exit_and_cleanup(&mut self) -> Result<()> {
        let child = self
            .child
            .as_mut()
            .context("Microsoft confirmation did not open")?;
        child.stdin.take();
        let status = tokio::time::timeout(std::time::Duration::from_secs(10), child.wait())
            .await
            .map_err(|_| anyhow::anyhow!("Microsoft confirmation did not finish closing"))?
            .map_err(|_| anyhow::anyhow!("Microsoft confirmation did not finish closing"))?;
        if !status.success() {
            anyhow::bail!("Microsoft confirmation did not finish successfully");
        }
        let directory = self.directory.clone();
        let cleaned = tokio::task::spawn_blocking(move || cleanup_consent_directory(&directory))
            .await
            .unwrap_or(false);
        if !cleaned {
            anyhow::bail!(
                "Could not remove the isolated Microsoft confirmation data. Retry Cookie"
            );
        }
        Ok(())
    }
}

impl Drop for ConsentWindow {
    fn drop(&mut self) {
        let mut child = self.child.take();
        if let Some(child) = child.as_mut() {
            child.stdin.take();
            let _ = child.start_kill();
        }
        let directory = self.directory.clone();
        std::thread::spawn(move || {
            if let Some(child) = child.as_mut() {
                for _ in 0..40 {
                    if child.try_wait().ok().flatten().is_some() {
                        break;
                    }
                    std::thread::sleep(std::time::Duration::from_millis(100));
                }
            }
            cleanup_consent_directory(&directory);
        });
    }
}

async fn await_cookie_confirmation<T>(
    identity: impl std::future::Future<Output = Result<T>>,
    completion: impl std::future::Future<Output = Result<()>>,
    cancel: &CancellationToken,
) -> Result<T> {
    tokio::select! {
        biased;
        () = cancel.cancelled() => Err(auth_error(AuthError::LoginCancelled)),
        result = async {
            let (identity, ()) = tokio::try_join!(identity, completion)?;
            Ok(identity)
        } => result,
    }
}

async fn cookie_login(
    auth: &AuthService,
    path: &std::path::Path,
    cancel: &CancellationToken,
    state: &Arc<Mutex<Option<String>>>,
    output: &Output,
    id: u64,
) -> Result<Value> {
    let pending = tokio::select! {
        biased;
        () = cancel.cancelled() => return Err(auth_error(AuthError::LoginCancelled)),
        result = auth.begin_cookie_login(path) => result.map_err(auth_error)?,
    };
    match auth.finish_cookie_login(&pending, cancel).await {
        Ok(account) => return Ok(metadata(&account)),
        Err(AuthError::Cookie(error)) if error.needs_browser() => {}
        Err(error) => return Err(auth_error(error)),
    }
    progress(output, id, "cookie_browser_confirmation");
    let browser = auth
        .begin_cookie_browser_login(&pending)
        .await
        .map_err(auth_error)?;
    if let Ok(mut state) = state.lock() {
        *state = Some(browser.dedupe_key().to_string());
    }
    if cancel.is_cancelled() {
        auth.cancel_microsoft_login(browser.dedupe_key()).await;
        return Err(auth_error(AuthError::LoginCancelled));
    }
    let browser_state = browser.dedupe_key().to_string();
    let request = match pending.consent_request(&browser) {
        Ok(request) => request,
        Err(error) => {
            auth.cancel_microsoft_login(&browser_state).await;
            return Err(auth_error(error));
        }
    };
    let mut window = match ConsentWindow::open(&request).await {
        Ok(window) => window,
        Err(error) => {
            auth.cancel_microsoft_login(&browser_state).await;
            return Err(error);
        }
    };
    let flow = async {
        auth.authorize_cookie_browser_login(&pending, browser, cancel, || {
            progress(output, id, "cookie_verifying_identity");
        })
        .await
        .map_err(auth_error)
    };
    let verified = match await_cookie_confirmation(flow, window.lifecycle(), cancel).await {
        Ok(verified) => verified,
        Err(error) => {
            auth.cancel_microsoft_login(&browser_state).await;
            return Err(error);
        }
    };
    let account = auth
        .commit_cookie_browser_login(verified, cancel)
        .await
        .map_err(auth_error)?;
    Ok(metadata(&account))
}

fn response(output: &Output, id: u64, result: Result<Value>) {
    let value = match result {
        Ok(value) => json!({"id": id, "ok": true, "result": value}),
        Err(error) => json!({"id": id, "ok": false, "error": error.to_string()}),
    };
    if let Ok(mut writer) = output.lock() {
        let _ = serde_json::to_writer(&mut *writer, &value);
        let _ = writer.write_all(b"\n");
        let _ = writer.flush();
    }
}

fn metadata(account: &MinecraftAccount) -> Value {
    json!({
        "id": account.id, "username": account.username,
        "kind": account.kind, "expires": account.expires,
        "refreshable": account.is_microsoft() && !account.refresh_token.is_empty(),
    })
}

fn session(account: &MinecraftAccount) -> Value {
    let mut value = metadata(account);
    value["access_token"] = json!(account.access_token);
    value
}

fn auth_error(error: AuthError) -> anyhow::Error {
    let message = match error {
        AuthError::Cookie(error) => error.to_string(),
        AuthError::LoginCancelled => "Sign-in was cancelled".to_string(),
        AuthError::AccountNotFound(_) => "Account is no longer saved".to_string(),
        AuthError::Session(error) => error.to_string(),
        AuthError::InvalidOfflineUsername { reason } => reason,
        AuthError::DuplicateUsername { .. } => {
            "An account with that name is already saved".to_string()
        }
        AuthError::ProtectedStore(_) => "Could not open the protected account store".to_string(),
        AuthError::Minecraft(
            MinecraftAuthError::BrowserAuthorizationExpired
            | MinecraftAuthError::DeviceAuthorizationExpired,
        ) => "Sign-in timed out. Add the account again to retry".to_string(),
        AuthError::Minecraft(MinecraftAuthError::XboxError { error_code, .. }) => {
            match error_code {
                2_148_916_233 => {
                    "Create an Xbox profile at xbox.com, then sign in again".to_string()
                }
                2_148_916_229 | 2_148_916_238 => {
                    "This account needs to join a Microsoft Family group before signing in"
                        .to_string()
                }
                2_148_916_227 => "This account has been suspended from Xbox".to_string(),
                _ => format!(
                    "Xbox sign-in failed (error {error_code}). Check the account at xbox.com"
                ),
            }
        }
        _ => "Microsoft sign-in failed. Check your connection and try signing in again".to_string(),
    };
    anyhow::anyhow!(message)
}

async fn serve() -> Result<()> {
    let (events, _receiver) = EventBus::channel();
    let net = RequestClient::new(NetConfig::default())?;
    let auth = Arc::new(AuthService::load(net, events).await.map_err(auth_error)?);
    let output = Arc::new(Mutex::new(std::io::stdout()));
    let (sender, mut input) = tokio::sync::mpsc::channel::<String>(16);
    std::thread::spawn(move || {
        for line in std::io::stdin().lock().lines() {
            let Ok(line) = line else { break };
            if line.len() > 16384 || sender.blocking_send(line).is_err() {
                break;
            }
        }
    });
    let mut login: Option<ActiveLogin> = None;
    while let Some(line) = input.recv().await {
        let Ok(request) = serde_json::from_str::<Value>(&line) else {
            continue;
        };
        let Some(id) = request["id"].as_u64() else {
            continue;
        };
        let operation = request["op"].as_str().unwrap_or("");
        if operation == "shutdown" {
            break;
        }
        if operation == "cancel" {
            if let Some(login) = login.take() {
                cancel_login(&auth, login).await;
            }
            response(&output, id, Ok(json!(null)));
            continue;
        }
        if operation == "login" || operation == "cookie" || operation == "session" {
            if login
                .as_ref()
                .is_some_and(|login| !login.task.is_finished())
            {
                response(
                    &output,
                    id,
                    Err(anyhow::anyhow!("Sign-in is already running")),
                );
                continue;
            }
            if operation == "session" {
                let Some(token) = request["access_token"].as_str().map(str::to_string) else {
                    response(
                        &output,
                        id,
                        Err(anyhow::anyhow!("Minecraft access token is missing")),
                    );
                    continue;
                };
                let cancel = CancellationToken::new();
                let flow_cancel = cancel.clone();
                let auth = auth.clone();
                let output = output.clone();
                login = Some(ActiveLogin {
                    state: Arc::new(Mutex::new(None)),
                    cancel,
                    task: tokio::spawn(async move {
                        let result = auth
                            .add_session_account(&token, &flow_cancel)
                            .await
                            .map(|account| metadata(&account))
                            .map_err(auth_error);
                        response(&output, id, result);
                    }),
                });
                continue;
            }
            if operation == "cookie" {
                let picker = rfd::AsyncFileDialog::new()
                    .set_title("Import Microsoft cookies")
                    .add_filter("Cookie exports", &["txt", "json", "cookies"])
                    .pick_file();
                let cancel = CancellationToken::new();
                let flow_cancel = cancel.clone();
                let state = Arc::new(Mutex::new(None));
                let flow_state = state.clone();
                let auth = auth.clone();
                let output = output.clone();
                login = Some(ActiveLogin {
                    state,
                    cancel,
                    task: tokio::spawn(async move {
                        let result = match picker.await {
                            Some(file) => {
                                cookie_login(
                                    &auth,
                                    file.path(),
                                    &flow_cancel,
                                    &flow_state,
                                    &output,
                                    id,
                                )
                                .await
                            }
                            None => Ok(json!(null)),
                        };
                        response(&output, id, result);
                    }),
                });
                continue;
            }
            match auth.begin_microsoft_login().await {
                Ok(pending) => {
                    let state = pending.dedupe_key().to_string();
                    if open::that(pending.auth_url()).is_err() {
                        auth.cancel_microsoft_login(&state).await;
                        response(
                            &output,
                            id,
                            Err(anyhow::anyhow!("Could not open the Microsoft sign-in page")),
                        );
                        continue;
                    }
                    let auth = auth.clone();
                    let output = output.clone();
                    login = Some(ActiveLogin {
                        state: Arc::new(Mutex::new(Some(state))),
                        cancel: CancellationToken::new(),
                        task: tokio::spawn(async move {
                            let result = auth
                                .finish_microsoft_login(pending)
                                .await
                                .map(|account| metadata(&account))
                                .map_err(auth_error);
                            response(&output, id, result);
                        }),
                    });
                }
                Err(error) => response(&output, id, Err(auth_error(error))),
            }
            continue;
        }
        response(&output, id, handle(&auth, operation, &request).await);
    }
    if let Some(login) = login {
        cancel_login(&auth, login).await;
    }
    Ok(())
}

async fn handle(auth: &AuthService, operation: &str, request: &Value) -> Result<Value> {
    match operation {
        "list" => {
            let store = CredentialsStore::load().await.map_err(auth_error)?;
            let accounts = store.list_accounts();
            let default = store.default_user.filter(|id| store.users.contains_key(id));
            Ok(
                json!({"users": accounts.iter().map(metadata).collect::<Vec<_>>(), "default_user": default}),
            )
        }
        "switch" | "refresh" => {
            let id = account_id(request)?;
            let account = if operation == "refresh" {
                auth.refresh_account(id).await
            } else {
                auth.account_for_launch(id).await
            }
            .map_err(auth_error)?;
            if !account.is_offline() && account.is_expired() {
                anyhow::bail!("Session has expired. Sign in again before switching accounts");
            }
            if operation == "switch" {
                auth.set_default_account(Some(id))
                    .await
                    .map_err(auth_error)?;
            }
            Ok(session(&account))
        }
        "remove" => {
            auth.remove_account(account_id(request)?)
                .await
                .map_err(auth_error)?;
            Ok(json!(null))
        }
        "offline" => {
            let username = request["username"]
                .as_str()
                .context("Username is missing")?
                .trim()
                .to_string();
            let account = auth
                .add_offline_account(username)
                .await
                .map_err(auth_error)?;
            Ok(metadata(&account))
        }
        _ => anyhow::bail!("Unknown account request"),
    }
}

fn account_id(request: &Value) -> Result<Uuid> {
    Uuid::parse_str(request["account"].as_str().context("Account is missing")?)
        .map_err(|_| anyhow::anyhow!("Invalid account"))
}

#[cfg(test)]
mod tests {
    use super::*;
    use arcadium_auth::AccountKind;

    fn owned_test_directory() -> std::path::PathBuf {
        let directory =
            std::env::temp_dir().join(format!("arcadium-cookie-webview-{}", Uuid::new_v4()));
        std::fs::create_dir(&directory).unwrap();
        directory
    }

    #[test]
    fn cleanup_rejects_unowned_and_nested_directories() {
        let owned = owned_test_directory();
        assert!(remove_consent_directory(&owned));
        let boundary =
            std::env::temp_dir().join(format!("arcadium-cookie-boundary-{}", Uuid::new_v4()));
        std::fs::create_dir(&boundary).unwrap();
        let nested = boundary.join(format!("arcadium-cookie-webview-{}", Uuid::new_v4()));
        std::fs::create_dir(&nested).unwrap();
        let marker = nested.join("fixture");
        std::fs::write(&marker, b"fixture").unwrap();
        assert!(!remove_consent_directory(&boundary));
        assert!(!remove_consent_directory(&nested));
        assert!(!remove_consent_directory(std::path::Path::new(".")));
        assert_eq!(std::fs::read(&marker).unwrap(), b"fixture");
        std::fs::remove_file(marker).unwrap();
        std::fs::remove_dir(nested).unwrap();
        std::fs::remove_dir(boundary).unwrap();
    }

    #[cfg(windows)]
    async fn locked_child_window(exit_code: u8) -> ConsentWindow {
        let directory = owned_test_directory();
        let mut command = tokio::process::Command::new("powershell.exe");
        command.args(["-NoLogo", "-NoProfile", "-NonInteractive", "-Command"])
            .arg(format!("$fixtureLock = [IO.File]::Open((Join-Path $env:ARCADIUM_COOKIE_GATE_DIRECTORY 'fixture.lock'), [IO.FileMode]::Create, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None); [Console]::Out.WriteLine('READY'); Start-Sleep -Milliseconds 250; exit {exit_code}"))
            .env("ARCADIUM_COOKIE_GATE_DIRECTORY", &directory)
            .stdin(std::process::Stdio::null()).stdout(std::process::Stdio::piped())
            .stderr(std::process::Stdio::null()).creation_flags(0x08000000).kill_on_drop(true);
        let mut child = command.spawn().unwrap();
        let mut output = BufReader::new(child.stdout.take().unwrap()).lines();
        assert_eq!(output.next_line().await.unwrap().as_deref(), Some("READY"));
        ConsentWindow {
            child: Some(child),
            directory,
        }
    }

    #[cfg(windows)]
    #[tokio::test]
    async fn confirmation_waits_for_child_exit_and_releases_its_windows_file_lock() {
        let mut window = locked_child_window(0).await;
        let directory = window.directory.clone();
        assert!(!remove_consent_directory(&directory));
        let task = tokio::spawn(async move { window.wait_for_exit_and_cleanup().await });
        tokio::task::yield_now().await;
        assert!(!task.is_finished());
        task.await.unwrap().unwrap();
        assert!(!directory.exists());
    }

    #[cfg(windows)]
    #[tokio::test]
    async fn confirmation_rejects_failed_child_exit_before_commit() {
        let mut window = locked_child_window(9).await;
        let directory = window.directory.clone();
        assert!(window.wait_for_exit_and_cleanup().await.is_err());
        drop(window);
        assert!(cleanup_consent_directory(&directory));
    }

    #[cfg(windows)]
    #[tokio::test]
    async fn confirmation_rejects_cleanup_failure_after_successful_child_exit() {
        use std::os::windows::fs::OpenOptionsExt;
        let mut window = locked_child_window(0).await;
        let directory = window.directory.clone();
        let marker = directory.join("parent-held.lock");
        let lock = std::fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .share_mode(0)
            .open(marker)
            .unwrap();
        assert!(window.wait_for_exit_and_cleanup().await.is_err());
        assert!(directory.exists());
        drop(lock);
        drop(window);
        assert!(cleanup_consent_directory(&directory));
    }

    #[tokio::test]
    async fn cookie_confirmation_waits_for_both_identity_and_window_in_either_order() {
        for identity_first in [true, false] {
            let (identity_tx, identity_rx) = tokio::sync::oneshot::channel::<Result<u8>>();
            let (completion_tx, completion_rx) = tokio::sync::oneshot::channel::<Result<()>>();
            let cancel = CancellationToken::new();
            let task = tokio::spawn(async move {
                await_cookie_confirmation(
                    async { identity_rx.await.unwrap() },
                    async { completion_rx.await.unwrap() },
                    &cancel,
                )
                .await
            });
            if identity_first {
                identity_tx.send(Ok(7)).unwrap();
                tokio::task::yield_now().await;
                assert!(!task.is_finished());
                completion_tx.send(Ok(())).unwrap();
            } else {
                completion_tx.send(Ok(())).unwrap();
                tokio::task::yield_now().await;
                assert!(!task.is_finished());
                identity_tx.send(Ok(7)).unwrap();
            }
            assert_eq!(task.await.unwrap().unwrap(), 7);
        }
    }

    #[tokio::test]
    async fn failed_window_cannot_release_verified_cookie_account_for_commit() {
        for identity_first in [true, false] {
            let identity = async { Ok(7) };
            let completion = async { Err(anyhow::anyhow!("fixture cleanup failure")) };
            let cancel = CancellationToken::new();
            let result = if identity_first {
                await_cookie_confirmation(identity, completion, &cancel).await
            } else {
                await_cookie_confirmation(
                    async { std::future::pending::<Result<u8>>().await },
                    completion,
                    &cancel,
                )
                .await
            };
            assert!(result.is_err());
        }
    }

    #[tokio::test]
    async fn failed_identity_and_cancellation_never_release_an_account_for_commit() {
        let cancel = CancellationToken::new();
        assert!(
            await_cookie_confirmation(
                async { Err::<u8, _>(anyhow::anyhow!("fixture identity mismatch")) },
                async { Ok(()) },
                &cancel,
            )
            .await
            .is_err()
        );
        cancel.cancel();
        assert!(
            await_cookie_confirmation(async { Ok(7) }, async { Ok(()) }, &cancel)
                .await
                .is_err()
        );
    }

    #[test]
    fn list_metadata_never_serializes_credentials() {
        let account = MinecraftAccount {
            id: Uuid::new_v4(),
            username: "ArcadiumTest".into(),
            access_token: "test-access-secret".into(),
            refresh_token: "test-refresh-secret".into(),
            expires: chrono::Utc::now(),
            kind: AccountKind::Microsoft,
        };
        let data = metadata(&account).to_string();
        assert!(!data.contains("secret"));
        assert!(!data.contains("access_token"));
        assert!(!data.contains("refresh_token"));
        let mut imported = account.clone();
        imported.kind = AccountKind::Session;
        imported.refresh_token.clear();
        let wire = metadata(&imported);
        assert_eq!(wire["kind"], "session");
        assert_eq!(wire["refreshable"], false);
        assert!(wire.get("access_token").is_none());
        assert!(wire.get("session_token").is_none());
        assert!(
            !session(&account)
                .to_string()
                .contains("test-refresh-secret")
        );
    }
}
