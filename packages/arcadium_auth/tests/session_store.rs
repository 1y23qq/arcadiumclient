use arcadium_auth::{AccountKind, AuthService, CredentialsStore, offline_account, offline_uuid};
use arcadium_events::EventBus;
use arcadium_net::{NetConfig, RequestClient};
use uuid::Uuid;

#[tokio::test]
async fn fresh_cracked_account_and_session_survive_protected_storage() {
    let directory =
        std::env::temp_dir().join(format!("arcadium-auth-session-test-{}", Uuid::new_v4()));
    arcadium_common::paths::set_launcher_dir(directory.clone());
    let (events, _receiver) = EventBus::channel();
    let auth = AuthService::with_store(
        CredentialsStore::default(),
        RequestClient::new(NetConfig::default()).unwrap(),
        events.clone(),
    );
    let offline = auth
        .add_offline_account("OfflineUser".into())
        .await
        .unwrap();
    assert_eq!(offline.id, offline_uuid("OfflineUser"));
    assert_eq!(
        auth.default_account_for_launch().await.unwrap().unwrap().id,
        offline.id
    );
    assert!(!auth.has_microsoft_account().await);

    let mut session = offline_account("SessionUser".into());
    session.id = Uuid::new_v4();
    session.kind = AccountKind::Session;
    session.access_token = "private-access-fixture".into();
    let mut store = CredentialsStore::load().await.unwrap();
    store
        .commit_account(session.clone(), &events)
        .await
        .unwrap();
    let protected = std::fs::read(directory.join("auth.dat")).unwrap();
    assert!(!String::from_utf8_lossy(&protected).contains("private-access-fixture"));
    let loaded = CredentialsStore::load().await.unwrap();
    assert_eq!(loaded.users[&session.id].kind, AccountKind::Session);
    assert_eq!(
        loaded.users[&session.id].access_token,
        "private-access-fixture"
    );
    assert!(!format!("{loaded:?}").contains("private-access-fixture"));

    auth.remove_account(offline.id).await.unwrap();
    auth.remove_account(session.id).await.unwrap();
    assert!(auth.list_accounts().await.is_empty());
    std::fs::remove_file(directory.join("auth.dat")).unwrap();
    std::fs::remove_file(directory.join("auth.lock")).unwrap();
    std::fs::remove_dir(directory).unwrap();
}
