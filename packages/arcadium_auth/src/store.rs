use std::collections::HashMap;
use std::fs::{File, OpenOptions};
use std::path::Path;

use uuid::Uuid;

use arcadium_common::paths;
use arcadium_events::EventBus;

use crate::data::{AccountKind, MinecraftAccount};
use crate::error::{AuthError, AuthResult};
use crate::offline::{offline_account, validate_offline_username};

#[derive(Debug, serde::Serialize, serde::Deserialize, Default)]
pub struct CredentialsStore {
    pub users: HashMap<Uuid, MinecraftAccount>,
    pub default_user: Option<Uuid>,
}

impl CredentialsStore {
    #[tracing::instrument(level = "debug")]
    pub async fn load() -> AuthResult<Self> {
        let _guard = credentials_lock().await?;
        Self::load_unlocked().await
    }

    async fn load_unlocked() -> AuthResult<Self> {
        let path = paths::auth_file()?;
        if !path.exists() {
            return Ok(Self::default());
        }

        let stored = arcadium_io::read(&path).await?;
        let migrate = crate::protected_store::needs_migration(&stored);
        let data = crate::protected_store::decrypt(&stored)?;
        match serde_json::from_slice(&data) {
            Ok(store) => {
                if migrate {
                    #[cfg(target_os = "macos")]
                    crate::protected_store::write_credentials(&path, &data).await?;
                    #[cfg(not(target_os = "macos"))]
                    {
                        let protected = crate::protected_store::encrypt(&data)?;
                        arcadium_io::write_atomic(&path, protected).await?;
                    }
                }
                Ok(store)
            }
            Err(_) => Err(AuthError::ProtectedStore("Invalid credential data".into())),
        }
    }

    #[tracing::instrument(level = "debug", skip_all)]
    pub async fn save(&self) -> AuthResult<()> {
        let _guard = credentials_lock().await?;
        self.save_unlocked().await
    }

    async fn save_unlocked(&self) -> AuthResult<()> {
        let path = paths::auth_file()?;

        let data = serde_json::to_vec(self)
            .map_err(|error| AuthError::ProtectedStore(error.to_string()))?;
        #[cfg(target_os = "macos")]
        crate::protected_store::write_credentials(&path, &data).await?;
        #[cfg(not(target_os = "macos"))]
        {
            let data = crate::protected_store::encrypt(&data)?;
            arcadium_io::write_atomic(&path, data).await?;
        }
        Ok(())
    }

    pub fn has_microsoft_account(&self) -> bool {
        self.users
            .values()
            .any(|account| account.kind == AccountKind::Microsoft)
    }

    pub fn list_accounts(&self) -> Vec<MinecraftAccount> {
        self.users.values().cloned().collect()
    }

    pub fn get_account(&self, id: Uuid) -> Option<&MinecraftAccount> {
        self.users.get(&id)
    }

    #[tracing::instrument(level = "debug", skip_all, fields(username = %account.username))]
    pub async fn commit_account(
        &mut self,
        account: MinecraftAccount,
        events: &EventBus,
    ) -> AuthResult<MinecraftAccount> {
        let _guard = self.reload_for_write().await?;
        let id = account.id;
        let account = if account.kind == AccountKind::Session {
            preserve_refresh_credentials(account, self.users.get(&id))
        } else {
            account
        };
        self.users.insert(account.id, account.clone());

        if self.default_user.is_none() {
            self.default_user = Some(account.id);
        }

        self.save_unlocked().await?;
        tracing::debug!("committed account to credentials store");
        events
            .notify("Account added")
            .body(format!("Signed in as {}", account.username))
            .send();
        Ok(account)
    }

    pub fn add_offline_account(&mut self, username: String) -> AuthResult<MinecraftAccount> {
        let account = self.insert_offline_account(username)?;
        Ok(account)
    }

    #[tracing::instrument(level = "debug", skip(self), fields(username = %username))]
    pub async fn add_offline_account_and_save(
        &mut self,
        username: String,
    ) -> AuthResult<MinecraftAccount> {
        let _guard = self.reload_for_write().await?;
        let account = self.insert_offline_account(username)?;
        self.save_unlocked().await?;
        Ok(account)
    }

    fn insert_offline_account(&mut self, username: String) -> AuthResult<MinecraftAccount> {
        validate_offline_username(&username)?;

        if self
            .users
            .values()
            .any(|u| u.is_offline() && u.username.eq_ignore_ascii_case(&username))
        {
            return Err(AuthError::DuplicateUsername { username });
        }

        let account = offline_account(username);
        self.users.insert(account.id, account.clone());
        if self.default_user.is_none() {
            self.default_user = Some(account.id);
        }
        Ok(account)
    }

    #[tracing::instrument(level = "debug", skip_all, fields(%account.id))]
    pub async fn commit_refreshed_account(&mut self, account: MinecraftAccount) -> AuthResult<()> {
        let _guard = self.reload_for_write().await?;
        if !self.users.contains_key(&account.id) {
            return Err(AuthError::AccountNotFound(account.id));
        }
        let id = account.id;
        let account = if account.kind == AccountKind::Session {
            preserve_refresh_credentials(account, self.users.get(&id))
        } else {
            account
        };
        self.users.insert(id, account);
        self.save_unlocked().await?;
        tracing::debug!("stored refreshed account");
        Ok(())
    }

    #[tracing::instrument(level = "debug", skip(self), fields(%id))]
    pub async fn remove_account(&mut self, id: Uuid) -> AuthResult<Option<MinecraftAccount>> {
        let _guard = self.reload_for_write().await?;
        let removed = self.users.remove(&id);

        if self.default_user == Some(id) {
            self.default_user = self.users.keys().copied().next();
        }

        self.save_unlocked().await?;
        Ok(removed)
    }

    #[tracing::instrument(level = "debug", skip(self), fields(?id))]
    pub async fn set_default_user(&mut self, id: Option<Uuid>) -> AuthResult<()> {
        let _guard = self.reload_for_write().await?;
        if let Some(id) = id
            && !self.users.contains_key(&id)
        {
            return Err(AuthError::AccountNotFound(id));
        }

        self.default_user = id;
        self.save_unlocked().await?;
        Ok(())
    }

    #[tracing::instrument(level = "debug", skip_all)]
    pub async fn resolve_default_id(&mut self) -> AuthResult<Option<Uuid>> {
        let id = self
            .default_user
            .or_else(|| self.users.keys().copied().next());

        let Some(id) = id else {
            return Ok(None);
        };

        if !self.users.contains_key(&id) {
            return Ok(None);
        }

        if self.default_user != Some(id) {
            self.set_default_user(Some(id)).await?;
        }

        Ok(Some(id))
    }

    async fn reload_for_write(&mut self) -> AuthResult<File> {
        let guard = credentials_lock().await?;
        *self = Self::load_unlocked().await?;
        Ok(guard)
    }

    /// Never touches the network callers that need a usable token go through
    /// [`crate::AuthService::account_for_launch`]
    #[tracing::instrument(level = "debug", skip_all)]
    pub async fn default_account(&mut self) -> AuthResult<Option<MinecraftAccount>> {
        let Some(id) = self.resolve_default_id().await? else {
            return Ok(None);
        };
        let Some(account) = self.users.get(&id).cloned() else {
            return Ok(None);
        };

        Ok(Some(account))
    }
}

async fn lock_file(path: impl AsRef<Path>) -> AuthResult<File> {
    let path = path.as_ref().to_path_buf();
    tokio::task::spawn_blocking(move || {
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let file = OpenOptions::new()
            .read(true)
            .write(true)
            .create(true)
            .truncate(false)
            .open(path)?;
        file.lock()?;
        Ok::<_, std::io::Error>(file)
    })
    .await
    .map_err(|_| AuthError::ProtectedStore("Credential lock was interrupted".into()))?
    .map_err(|_| AuthError::ProtectedStore("Could not lock credentials".into()))
}

async fn credentials_lock() -> AuthResult<File> {
    lock_file(paths::auth_file()?.with_extension("lock")).await
}

pub(crate) async fn refresh_lock(id: Uuid) -> AuthResult<File> {
    lock_file(paths::config_dir()?.join(format!(".auth-refresh-{id}.lock"))).await
}

/// A transient failure must keep the existing token discarding it because
/// Wi-Fi dropped would sign the user out of a working account
pub(crate) fn is_transient_auth_error(err: &AuthError) -> bool {
    matches!(
        err,
        AuthError::Minecraft(crate::error::MinecraftAuthError::RequestError { source, .. })
            if source.is_connect() || source.is_timeout()
    )
}

fn preserve_refresh_credentials(
    mut session: MinecraftAccount,
    existing: Option<&MinecraftAccount>,
) -> MinecraftAccount {
    if let Some(existing) =
        existing.filter(|account| account.is_microsoft() && !account.refresh_token.is_empty())
    {
        session.kind = AccountKind::Microsoft;
        session.refresh_token = existing.refresh_token.clone();
        session.oauth_client_id = existing.oauth_client_id.clone();
    }
    session
}

#[cfg(test)]
mod session_store_tests {
    use super::*;

    #[test]
    fn importing_session_keeps_existing_microsoft_refresh_credentials() {
        let mut existing = offline_account("Steve".into());
        existing.kind = AccountKind::Microsoft;
        existing.refresh_token = "refresh-fixture".into();
        existing.oauth_client_id = Some("00000000402b5328".into());
        let mut imported = existing.clone();
        imported.kind = AccountKind::Session;
        imported.access_token = "access-fixture".into();
        imported.refresh_token.clear();
        imported.oauth_client_id = None;
        let merged = preserve_refresh_credentials(imported, Some(&existing));
        assert_eq!(merged.kind, AccountKind::Microsoft);
        assert_eq!(merged.refresh_token, "refresh-fixture");
        assert_eq!(merged.oauth_client_id.as_deref(), Some("00000000402b5328"));
        assert_eq!(merged.access_token, "access-fixture");
        assert!(!format!("{merged:?}").contains("fixture"));
    }
}
