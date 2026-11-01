use arcadium_auth::{AuthService, CredentialsStore};
use arcadium_events::EventBus;
use arcadium_net::{NetConfig, RequestClient};
use std::io::Read;
use tokio_util::sync::CancellationToken;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let directory = std::env::var_os("ARCADIUM_TEST_AUTH_DIR")
        .map(std::path::PathBuf::from)
        .ok_or_else(|| std::io::Error::other("Set an isolated test credential directory"))?;
    arcadium_common::paths::set_launcher_dir(directory);
    let mut input = String::new();
    std::io::stdin().read_to_string(&mut input)?;
    let (events, _receiver) = EventBus::channel();
    let service = AuthService::load(RequestClient::new(NetConfig::default())?, events).await?;
    match service
        .add_session_account(input.trim(), &CancellationToken::new())
        .await
    {
        Ok(account) => {
            let saved = CredentialsStore::load()
                .await?
                .get_account(account.id)
                .is_some();
            let protected = tokio::fs::read(arcadium_common::paths::auth_file()?).await?;
            let safe = !protected.starts_with(b"{")
                && !protected
                    .windows(account.access_token.len())
                    .any(|v| v == account.access_token.as_bytes());
            println!(
                "{}",
                serde_json::json!({"login":true,"saved":saved,"protected":safe,
                "refreshable":!account.refresh_token.is_empty(),"clientId":account.oauth_client_id})
            );
            if !saved || !safe {
                return Err(std::io::Error::other("Credential persistence checks failed").into());
            }
            Ok(())
        }
        Err(_) => {
            println!("{{\"login\":false}}");
            Err(std::io::Error::other("Session import failed; no credential data logged").into())
        }
    }
}
