use arcadium_auth::{AccountKind, CredentialsStore, offline_account, offline_uuid};
use chrono::{TimeDelta, Utc};
use uuid::Uuid;

fn isolate_launcher_dir() {
    arcadium_common::paths::set_launcher_dir(
        std::env::temp_dir().join(format!("arcadium-auth-test-{}", Uuid::new_v4())),
    );
}

fn fake_microsoft_account(username: &str) -> arcadium_auth::MinecraftAccount {
    let mut account = offline_account(username.to_string());
    account.kind = AccountKind::Microsoft;
    account.access_token = "access".into();
    account.refresh_token = "refresh".into();
    account
}

#[test]
fn offline_uuid_matches_vanilla_algorithm() {
    let id = offline_uuid("Notch");

    assert_eq!(
        id,
        Uuid::parse_str("b50ad385-829d-3141-a216-7e7d7539ba7f").unwrap()
    );
    assert_ne!(id, offline_uuid("Steve"));
}

#[tokio::test]
async fn offline_account_works_without_microsoft() {
    isolate_launcher_dir();

    let mut store = CredentialsStore::default();
    let account = store.add_offline_account("Steve".into()).unwrap();
    assert_eq!(account.id, offline_uuid("Steve"));
    assert_eq!(account.access_token, "0");
    assert_eq!(store.default_user, Some(account.id));
}

#[tokio::test]
async fn offline_account_allowed_when_microsoft_exists() {
    isolate_launcher_dir();

    let mut store = CredentialsStore::default();
    let msa = fake_microsoft_account("MsaUser");
    store.users.insert(msa.id, msa);

    let offline = store.add_offline_account("Steve".into()).unwrap();
    assert_eq!(offline.kind, AccountKind::Offline);
    assert_eq!(offline.username, "Steve");
}

#[tokio::test]
async fn default_offline_remains_usable_when_last_microsoft_removed() {
    isolate_launcher_dir();

    let mut store = CredentialsStore::default();
    let msa = fake_microsoft_account("MsaUser");
    let msa_id = msa.id;
    store.users.insert(msa_id, msa);

    let offline = store.add_offline_account("Steve".into()).unwrap();
    store.default_user = Some(offline.id);
    store.users.remove(&msa_id);

    let account = store.default_account().await.unwrap().unwrap();
    assert_eq!(account.id, offline.id);
    assert!(account.is_offline());
}

#[test]
fn token_expiring_imminently_counts_as_expired() {
    let mut account = fake_microsoft_account("MsaUser");

    account.expires = Utc::now() + TimeDelta::hours(1);
    assert!(!account.is_expired(), "a token with an hour left is usable");

    account.expires = Utc::now() + TimeDelta::seconds(5);
    assert!(
        account.is_expired(),
        "a token about to lapse must be renewed before launching"
    );

    account.expires = Utc::now() - TimeDelta::hours(1);
    assert!(account.is_expired());
}

#[tokio::test]
async fn default_account_returns_expired_token_without_refreshing() {
    isolate_launcher_dir();

    let mut store = CredentialsStore::default();
    let mut msa = fake_microsoft_account("MsaUser");
    msa.expires = Utc::now() - TimeDelta::hours(5);
    let msa_id = msa.id;
    store.users.insert(msa_id, msa);

    store.default_user = Some(msa_id);

    let account = store
        .default_account()
        .await
        .expect("an expired token must not fail the read path")
        .expect("the only account should be the default");

    assert_eq!(account.id, msa_id);
    assert!(account.is_expired());
}

#[test]
fn offline_names_are_validated_and_online_names_do_not_conflict() {
    let mut store = CredentialsStore::default();
    for username in ["", "a", "more_than_16_chars", "bad name", "bad/name"] {
        assert!(store.add_offline_account(username.into()).is_err());
    }
    let mut online = fake_microsoft_account("Steve");
    online.id = Uuid::new_v4();
    store.users.insert(online.id, online);
    let offline = store.add_offline_account("Steve".into()).unwrap();
    assert!(offline.is_offline());
    assert!(store.add_offline_account("steve".into()).is_err());
}
