use arcadium_auth::{
    AuthError, AuthService, CookieImportError, CredentialsStore, MinecraftAuthError,
};
use arcadium_events::EventBus;
use arcadium_net::{NetConfig, RequestClient};
use tokio_util::sync::CancellationToken;

fn failure_stage(error: &AuthError) -> String {
    match error {
        AuthError::Cookie(CookieImportError::ConsentRequired) => "oauth_consent_required".into(),
        AuthError::Cookie(CookieImportError::InteractiveRequired) => {
            "browser_confirmation_required".into()
        }
        AuthError::Cookie(CookieImportError::NoMicrosoftCookies) => {
            "no_unexpired_microsoft_cookies".into()
        }
        AuthError::Cookie(CookieImportError::Connection) => "microsoft_connection".into(),
        AuthError::Cookie(CookieImportError::ServiceUnavailable) => "microsoft_unavailable".into(),
        AuthError::Cookie(CookieImportError::AuthorizationRejected) => {
            "cookie_authorization_rejected".into()
        }
        AuthError::Cookie(_) => "cookie_parse".into(),
        AuthError::Minecraft(
            MinecraftAuthError::RequestError { step, .. }
            | MinecraftAuthError::DeserializeError { step, .. }
            | MinecraftAuthError::ServiceError { step, .. }
            | MinecraftAuthError::XboxError { step, .. },
        ) => format!("{step:?}"),
        AuthError::ProtectedStore(_) => "protected_store".into(),
        _ => "account_authentication".into(),
    }
}

#[tokio::main]
async fn main() {
    let Some(file) = std::env::args_os().nth(1).map(std::path::PathBuf::from) else {
        println!("{{\"login\":false,\"stage\":\"missing_file\"}}");
        return;
    };
    let directory =
        std::env::temp_dir().join(format!("arcadium-cookie-smoke-{}", uuid::Uuid::new_v4()));
    arcadium_common::paths::set_launcher_dir(directory.clone());
    let (events, _receiver) = EventBus::channel();
    let result = async {
        let net = RequestClient::new(NetConfig::default()).map_err(AuthError::from)?;
        let service = AuthService::load(net, events).await?;
        let account = service.import_cookie_account(&file, &CancellationToken::new()).await?;
        let checks = async {
            let path = arcadium_common::paths::auth_file()?;
            let protected = tokio::fs::read(&path).await.map_err(|_| AuthError::ProtectedStore("read failed".into()))?;
            let is_protected = !protected.starts_with(b"{")
                && !account.access_token.is_empty() && !account.refresh_token.is_empty()
                && !protected.windows(account.access_token.len()).any(|bytes| bytes == account.access_token.as_bytes())
                && !protected.windows(account.refresh_token.len()).any(|bytes| bytes == account.refresh_token.as_bytes());
            let saved = CredentialsStore::load().await?.get_account(account.id).is_some();
            let refreshed = service.refresh_account(account.id).await?;
            let valid = !refreshed.is_expired() && !refreshed.access_token.is_empty() && !refreshed.refresh_token.is_empty();
            Ok::<_, AuthError>(serde_json::json!({"login": true, "protected_store": is_protected, "reopened": saved, "refresh": valid}))
        }.await;
        let _ = service.remove_account(account.id).await;
        checks
    }.await;
    match result {
        Ok(result) => println!("{result}"),
        Err(error) => println!(
            "{}",
            serde_json::json!({"login": false, "stage": failure_stage(&error)})
        ),
    }
    if directory.parent() == Some(std::env::temp_dir().as_path())
        && directory
            .file_name()
            .is_some_and(|name| name.to_string_lossy().starts_with("arcadium-cookie-smoke-"))
    {
        let _ = tokio::fs::remove_dir_all(directory).await;
    }
}
