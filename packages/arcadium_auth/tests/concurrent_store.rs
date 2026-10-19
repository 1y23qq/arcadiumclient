use arcadium_auth::{AccountKind, CredentialsStore, offline_account};
use arcadium_events::EventBus;
use uuid::Uuid;

#[tokio::test]
async fn independent_services_preserve_additions_removals_and_rotated_tokens() {
    let directory =
        std::env::temp_dir().join(format!("arcadium-account-concurrency-{}", Uuid::new_v4()));
    arcadium_common::paths::set_launcher_dir(directory.clone());
    let (events, _receiver) = EventBus::channel();
    let mut initial = CredentialsStore::default();
    let mut first = offline_account("ArcadiumTestOne".into());
    first.kind = AccountKind::Microsoft;
    first.access_token = "test-only-access".into();
    first.refresh_token = "test-only-refresh".into();
    initial
        .commit_account(first.clone(), &events)
        .await
        .unwrap();
    let mut launcher = CredentialsStore::load().await.unwrap();
    let mut game = CredentialsStore::load().await.unwrap();
    let mut second = offline_account("ArcadiumTestTwo".into());
    second.kind = AccountKind::Microsoft;
    game.commit_account(second.clone(), &events).await.unwrap();
    first.refresh_token = "test-only-rotated-refresh".into();
    launcher
        .commit_refreshed_account(first.clone())
        .await
        .unwrap();
    let current = CredentialsStore::load().await.unwrap();
    assert_eq!(current.users.len(), 2);
    assert_eq!(current.users[&first.id].refresh_token, first.refresh_token);
    game.remove_account(first.id).await.unwrap();
    assert!(
        launcher
            .commit_refreshed_account(first.clone())
            .await
            .is_err()
    );
    let current = CredentialsStore::load().await.unwrap();
    assert!(!current.users.contains_key(&first.id));
    assert!(current.users.contains_key(&second.id));
    let stored = std::fs::read(directory.join("auth.dat")).unwrap();
    #[cfg(any(windows, target_os = "macos"))]
    assert!(!stored.windows(16).any(|bytes| bytes == b"test-only-access"));
    #[cfg(not(any(windows, target_os = "macos")))]
    let _ = stored;
}
