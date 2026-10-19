use security_framework::passwords::{
    delete_generic_password, get_generic_password, set_generic_password,
};
use sha2::{Digest, Sha256};
use std::path::Path;

use crate::{AuthError, AuthResult};

const SERVICE: &str = "io.github.arcadiumclient.launcher.credentials";
const MARKER: &[u8] = b"ARCADIUM_KEYCHAIN_V1\n";

fn error(error: impl std::fmt::Display) -> AuthError {
    AuthError::ProtectedStore(format!("macOS Keychain: {error}"))
}

fn profile_reference(path: &Path) -> String {
    let digest = Sha256::digest(path.to_string_lossy().as_bytes());
    digest.iter().map(|byte| format!("{byte:02x}")).collect()
}

pub fn encrypt(data: &[u8]) -> AuthResult<Vec<u8>> {
    let path = arcadium_common::paths::auth_file().map_err(error)?;
    let account = format!(
        "{}{}",
        profile_reference(&path),
        uuid::Uuid::new_v4().simple()
    );
    let marker = save(&account, data)?;
    match get_generic_password(SERVICE, &account) {
        Ok(stored) if stored == data => Ok(marker),
        result => {
            let _ = delete_generic_password(SERVICE, &account);
            match result {
                Err(cause) => Err(error(cause)),
                _ => Err(error("Credential verification failed")),
            }
        }
    }
}

fn save(account: &str, data: &[u8]) -> AuthResult<Vec<u8>> {
    set_generic_password(SERVICE, account, data).map_err(error)?;
    Ok([MARKER, account.as_bytes()].concat())
}

fn reference(data: &[u8]) -> AuthResult<Option<&str>> {
    let Some(account) = data.strip_prefix(MARKER) else {
        return Ok(None);
    };
    let account = std::str::from_utf8(account).map_err(error)?;
    if !matches!(account.len(), 64 | 96) || !account.bytes().all(|byte| byte.is_ascii_hexdigit()) {
        return Err(error("Invalid credential reference"));
    }
    Ok(Some(account))
}

fn delete_owned_reference(marker: &[u8], profile: &str) {
    if let Ok(Some(account)) = reference(marker)
        && account.starts_with(profile)
    {
        let _ = delete_generic_password(SERVICE, account);
    }
}

pub async fn write_credentials(path: &Path, data: &[u8]) -> AuthResult<()> {
    let previous = match tokio::fs::read(path).await {
        Ok(marker) => marker,
        Err(cause) if cause.kind() == std::io::ErrorKind::NotFound => Vec::new(),
        Err(cause) => return Err(error(cause)),
    };
    let next = encrypt(data)?;
    let profile = profile_reference(path);
    if let Err(cause) = arcadium_io::write_atomic(path, &next).await {
        delete_owned_reference(&next, &profile);
        return Err(cause.into());
    }
    // Readers and writers hold the credential file lock through this commit.
    delete_owned_reference(&previous, &profile);
    Ok(())
}

pub fn decrypt(data: &[u8]) -> AuthResult<Vec<u8>> {
    if let Some(account) = reference(data)? {
        get_generic_password(SERVICE, account).map_err(error)
    } else if data.first() == Some(&b'{') {
        Ok(data.to_vec())
    } else {
        Err(error("Unrecognized credential format"))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn credentials_are_in_the_keychain_and_the_file_has_only_a_reference() {
        let account = profile_reference(Path::new(&uuid::Uuid::new_v4().to_string()));
        let secret = b"{\"refresh_token\":\"test-only-keychain-token\"}";
        let marker = save(&account, secret).unwrap();
        let token = b"test-only-keychain-token";
        assert!(!marker.windows(token.len()).any(|slice| slice == token));
        assert_eq!(decrypt(&marker).unwrap(), secret);
        save(&account, b"updated").unwrap();
        assert_eq!(decrypt(&marker).unwrap(), b"updated");
        delete_generic_password(SERVICE, &account).unwrap();
        assert!(decrypt(&marker).is_err());
    }

    #[test]
    fn references_accept_existing_and_versioned_records_only() {
        for length in [64, 96] {
            let marker = [MARKER, "a".repeat(length).as_bytes()].concat();
            assert!(reference(&marker).unwrap().is_some());
        }
        for account in ["", "a", &"a".repeat(65), &"g".repeat(64)] {
            assert!(reference(&[MARKER, account.as_bytes()].concat()).is_err());
        }
        assert!(reference(b"{\"users\":{}}").unwrap().is_none());
    }
}
