use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum AccountKind {
    Microsoft,
    Offline,
    Session,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(from = "StoredAccount", into = "StoredAccount")]
pub struct MinecraftAccount {
    pub id: Uuid,
    pub username: String,
    pub access_token: String,
    pub refresh_token: String,
    pub oauth_client_id: Option<String>,
    pub expires: DateTime<Utc>,
    #[serde(default = "default_account_kind")]
    pub kind: AccountKind,
}

// Keep stored account kinds readable by older launchers.
#[derive(Serialize, Deserialize)]
struct StoredAccount {
    id: Uuid,
    username: String,
    access_token: String,
    refresh_token: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    oauth_client_id: Option<String>,
    expires: DateTime<Utc>,
    #[serde(default = "default_account_kind")]
    kind: AccountKind,
    #[serde(default, skip_serializing_if = "is_false")]
    session_token: bool,
}

fn is_false(value: &bool) -> bool {
    !value
}

impl From<MinecraftAccount> for StoredAccount {
    fn from(account: MinecraftAccount) -> Self {
        let session_token = account.kind == AccountKind::Session;
        Self {
            id: account.id,
            username: account.username,
            access_token: account.access_token,
            refresh_token: account.refresh_token,
            oauth_client_id: account.oauth_client_id,
            expires: account.expires,
            kind: if session_token {
                AccountKind::Microsoft
            } else {
                account.kind
            },
            session_token,
        }
    }
}

impl From<StoredAccount> for MinecraftAccount {
    fn from(account: StoredAccount) -> Self {
        let session = account.session_token
            && account.kind == AccountKind::Microsoft
            && account.refresh_token.is_empty();
        Self {
            id: account.id,
            username: account.username,
            access_token: account.access_token,
            refresh_token: account.refresh_token,
            oauth_client_id: account.oauth_client_id,
            expires: account.expires,
            kind: if session {
                AccountKind::Session
            } else {
                account.kind
            },
        }
    }
}

fn default_account_kind() -> AccountKind {
    AccountKind::Microsoft
}

impl std::fmt::Debug for MinecraftAccount {
    fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        formatter
            .debug_struct("MinecraftAccount")
            .field("id", &self.id)
            .field("username", &self.username)
            .field("expires", &self.expires)
            .field("kind", &self.kind)
            .finish_non_exhaustive()
    }
}

impl MinecraftAccount {
    pub fn is_microsoft(&self) -> bool {
        self.kind == AccountKind::Microsoft
    }

    pub fn is_offline(&self) -> bool {
        self.kind == AccountKind::Offline
    }

    pub fn is_expired(&self) -> bool {
        self.expires <= Utc::now() + chrono::TimeDelta::seconds(60)
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct DeviceCodeLogin {
    pub user_code: String,
    pub device_code: String,
    pub verification_uri: String,
    pub expires_in: u64,
    pub interval: u64,
    pub message: String,
}

impl DeviceCodeLogin {
    pub fn browser_only() -> Self {
        Self {
            user_code: String::new(),
            device_code: String::new(),
            verification_uri: String::new(),
            expires_in: 900,
            interval: 5,
            message: String::new(),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct BrowserLogin {
    pub auth_url: String,
    pub state: String,
    pub redirect_uri: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct MicrosoftLoginSession {
    pub browser: BrowserLogin,
    pub device: DeviceCodeLogin,
}

impl MicrosoftLoginSession {
    pub fn dedupe_key(&self) -> &str {
        &self.browser.state
    }

    pub fn auth_url(&self) -> &str {
        &self.browser.auth_url
    }

    pub fn user_code(&self) -> &str {
        &self.device.user_code
    }

    pub fn verification_uri(&self) -> &str {
        &self.device.verification_uri
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[derive(Deserialize)]
    #[serde(rename_all = "snake_case")]
    enum LegacyKind {
        Microsoft,
        Offline,
    }

    #[derive(Deserialize)]
    struct LegacyAccount {
        id: Uuid,
        kind: LegacyKind,
        access_token: String,
        refresh_token: String,
    }

    #[test]
    fn session_storage_is_readable_by_older_launchers() {
        let mut session = crate::offline_account("SessionUser".into());
        session.kind = AccountKind::Session;
        session.access_token = "access-fixture".into();
        let stored = serde_json::to_value(&session).unwrap();
        assert_eq!(stored["kind"], "microsoft");
        assert_eq!(stored["session_token"], true);
        let legacy: LegacyAccount = serde_json::from_value(stored.clone()).unwrap();
        assert_eq!(legacy.id, session.id);
        assert!(matches!(legacy.kind, LegacyKind::Microsoft));
        assert_eq!(legacy.access_token, "access-fixture");
        assert!(legacy.refresh_token.is_empty());
        let loaded: MinecraftAccount = serde_json::from_value(stored).unwrap();
        assert_eq!(loaded.kind, AccountKind::Session);
    }

    #[test]
    fn storage_marker_does_not_change_existing_microsoft_refresh_credentials() {
        let mut account = crate::offline_account("MicrosoftUser".into());
        account.kind = AccountKind::Microsoft;
        account.access_token = "access-fixture".into();
        account.refresh_token = "refresh-fixture".into();
        let mut stored = serde_json::to_value(&account).unwrap();
        assert!(stored.get("session_token").is_none());
        stored["session_token"] = serde_json::json!(true);
        let loaded: MinecraftAccount = serde_json::from_value(stored).unwrap();
        assert_eq!(loaded.kind, AccountKind::Microsoft);
        assert_eq!(loaded.refresh_token, "refresh-fixture");
        let offline = serde_json::to_value(crate::offline_account("OfflineUser".into())).unwrap();
        assert_eq!(offline["kind"], "offline");
        assert!(offline.get("session_token").is_none());
    }

    #[test]
    fn imported_refresh_registration_survives_storage_without_changing_legacy_accounts() {
        let mut account = crate::offline_account("MicrosoftUser".into());
        account.kind = AccountKind::Microsoft;
        account.refresh_token = "refresh-fixture".into();
        account.oauth_client_id = Some("00000000402b5328".into());
        let stored = serde_json::to_value(&account).unwrap();
        let legacy: LegacyAccount = serde_json::from_value(stored.clone()).unwrap();
        assert!(matches!(legacy.kind, LegacyKind::Microsoft));
        let loaded: MinecraftAccount = serde_json::from_value(stored.clone()).unwrap();
        assert_eq!(loaded.oauth_client_id.as_deref(), Some("00000000402b5328"));
        let mut old_stored = stored;
        old_stored
            .as_object_mut()
            .unwrap()
            .remove("oauth_client_id");
        let old: MinecraftAccount = serde_json::from_value(old_stored).unwrap();
        assert!(old.oauth_client_id.is_none());
    }
}
