#![cfg(target_os = "macos")]

use std::collections::BTreeSet;
use std::path::{Path, PathBuf};
use std::process::Command;

use arcadium_auth::{AccountKind, CredentialsStore, offline_account};
use security_framework::item::{ItemClass, ItemSearchOptions, Limit};
use security_framework::passwords::{
    delete_generic_password, get_generic_password, set_generic_password,
};
use sha2::{Digest, Sha256};
use uuid::Uuid;

const SERVICE: &str = "io.github.arcadiumclient.launcher.credentials";
const MARKER: &[u8] = b"ARCADIUM_KEYCHAIN_V1\n";

fn records(profile: &str) -> BTreeSet<String> {
    let mut search = ItemSearchOptions::new();
    search
        .class(ItemClass::generic_password())
        .service(SERVICE)
        .load_attributes(true)
        .load_data(false)
        .limit(Limit::All);
    match search.search() {
        Ok(found) => found
            .into_iter()
            .filter_map(|item| item.simplify_dict()?.remove("acct"))
            .filter(|account| account.starts_with(profile))
            .collect(),
        Err(error) if error.code() == -25300 => BTreeSet::new(),
        Err(error) => panic!("Could not inspect fixture Keychain metadata: {error}"),
    }
}

fn reference(marker: &[u8]) -> String {
    std::str::from_utf8(
        marker
            .strip_prefix(MARKER)
            .expect("Protected marker missing"),
    )
    .unwrap()
    .to_string()
}

fn flags(path: &Path, immutable: bool) -> bool {
    Command::new("/usr/bin/chflags")
        .arg(if immutable { "uchg" } else { "nouchg" })
        .arg(path)
        .status()
        .is_ok_and(|status| status.success())
}

struct Fixture {
    directory: PathBuf,
    profile: String,
}

impl Drop for Fixture {
    fn drop(&mut self) {
        let _ = flags(&self.directory.join("auth.dat"), false);
        if !std::thread::panicking() {
            for account in records(&self.profile) {
                let _ = delete_generic_password(SERVICE, &account);
            }
        }
        let _ = std::fs::remove_dir_all(&self.directory);
    }
}

#[tokio::test]
async fn legacy_records_upgrade_and_failed_atomic_commit_preserves_credentials() {
    let directory =
        std::env::temp_dir().join(format!("arcadium-keychain-regression-{}", Uuid::new_v4()));
    std::fs::create_dir_all(&directory).unwrap();
    arcadium_common::paths::set_launcher_dir(directory.clone());
    let path = arcadium_common::paths::auth_file().unwrap();
    assert_eq!(path, directory.join("auth.dat"));
    let profile: String = Sha256::digest(path.to_string_lossy().as_bytes())
        .iter()
        .map(|byte| format!("{byte:02x}"))
        .collect();
    let _fixture = Fixture {
        directory,
        profile: profile.clone(),
    };
    let mut account = offline_account("ArcadiumFixture".into());
    account.kind = AccountKind::Microsoft;
    account.access_token = "macos-test-only-access".into();
    account.refresh_token = "macos-test-only-refresh".into();
    let mut seed = CredentialsStore::default();
    seed.default_user = Some(account.id);
    seed.users.insert(account.id, account.clone());
    let original_json = serde_json::to_vec(&seed).unwrap();
    set_generic_password(SERVICE, &profile, &original_json).unwrap();
    std::fs::write(&path, [MARKER, profile.as_bytes()].concat()).unwrap();
    let mut store = CredentialsStore::load().await.unwrap();
    assert_eq!(store.default_user, Some(account.id));
    assert_eq!(
        store.users[&account.id].refresh_token,
        account.refresh_token
    );
    store.save().await.unwrap();
    let committed = std::fs::read(&path).unwrap();
    let current = reference(&committed);
    assert_eq!(current.len(), 96);
    assert!(current.starts_with(&profile));
    let token = b"macos-test-only-access";
    assert!(!committed.windows(token.len()).any(|bytes| bytes == token));
    assert_eq!(
        get_generic_password(SERVICE, &profile).unwrap_err().code(),
        -25300
    );
    assert_eq!(records(&profile), BTreeSet::from([current.clone()]));

    assert!(
        flags(&path, true),
        "Could not lock the fixture marker against atomic replacement"
    );
    store.users.get_mut(&account.id).unwrap().refresh_token = "macos-test-only-uncommitted".into();
    assert!(
        store.save().await.is_err(),
        "Atomic replacement unexpectedly succeeded on an immutable file"
    );
    assert!(flags(&path, false), "Could not unlock the fixture marker");
    assert_eq!(std::fs::read(&path).unwrap(), committed);
    assert_eq!(records(&profile), BTreeSet::from([current.clone()]));
    let restored = CredentialsStore::load().await.unwrap();
    assert_eq!(restored.default_user, Some(account.id));
    assert_eq!(
        restored.users[&account.id].refresh_token,
        account.refresh_token
    );

    for step in 0..32 {
        store.users.get_mut(&account.id).unwrap().refresh_token =
            format!("macos-test-only-rotation-{step}");
        let previous = reference(&std::fs::read(&path).unwrap());
        store.save().await.unwrap();
        let next = reference(&std::fs::read(&path).unwrap());
        assert_ne!(next, previous);
        assert_eq!(
            get_generic_password(SERVICE, &previous).unwrap_err().code(),
            -25300
        );
        assert_eq!(records(&profile), BTreeSet::from([next]));
        let actual = CredentialsStore::load().await.unwrap();
        assert_eq!(actual.default_user, Some(account.id));
        assert_eq!(
            actual.users[&account.id].refresh_token,
            store.users[&account.id].refresh_token
        );
    }
}
