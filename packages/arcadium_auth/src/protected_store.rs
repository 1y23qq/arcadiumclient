// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: MIT
use crate::{AuthError, AuthResult};

#[cfg(target_os = "macos")]
mod macos_keychain;

#[cfg(target_os = "macos")]
pub(crate) use macos_keychain::write_credentials;

#[cfg(windows)]
fn transform(data: &[u8], protect: bool) -> AuthResult<Vec<u8>> {
    use windows_sys::Win32::Foundation::LocalFree;
    use windows_sys::Win32::Security::Cryptography::{
        CRYPT_INTEGER_BLOB, CRYPTPROTECT_UI_FORBIDDEN, CryptProtectData, CryptUnprotectData,
    };
    let input = CRYPT_INTEGER_BLOB {
        cbData: data.len() as u32,
        pbData: data.as_ptr() as *mut u8,
    };
    let mut output = CRYPT_INTEGER_BLOB {
        cbData: 0,
        pbData: std::ptr::null_mut(),
    };
    let succeeded = unsafe {
        if protect {
            CryptProtectData(
                &input,
                std::ptr::null(),
                std::ptr::null(),
                std::ptr::null_mut(),
                std::ptr::null(),
                CRYPTPROTECT_UI_FORBIDDEN,
                &mut output,
            )
        } else {
            CryptUnprotectData(
                &input,
                std::ptr::null_mut(),
                std::ptr::null(),
                std::ptr::null_mut(),
                std::ptr::null(),
                CRYPTPROTECT_UI_FORBIDDEN,
                &mut output,
            )
        }
    };
    if succeeded == 0 {
        return Err(AuthError::ProtectedStore(
            std::io::Error::last_os_error().to_string(),
        ));
    }
    let bytes =
        unsafe { std::slice::from_raw_parts(output.pbData, output.cbData as usize).to_vec() };
    unsafe { LocalFree(output.pbData as _) };
    Ok(bytes)
}

pub fn encrypt(data: &[u8]) -> AuthResult<Vec<u8>> {
    #[cfg(windows)]
    {
        transform(data, true)
    }
    #[cfg(target_os = "macos")]
    {
        macos_keychain::encrypt(data)
    }
    #[cfg(not(any(windows, target_os = "macos")))]
    {
        Ok(data.to_vec())
    }
}
pub fn decrypt(data: &[u8]) -> AuthResult<Vec<u8>> {
    #[cfg(windows)]
    {
        transform(data, false)
    }
    #[cfg(target_os = "macos")]
    {
        macos_keychain::decrypt(data)
    }
    #[cfg(not(any(windows, target_os = "macos")))]
    {
        Ok(data.to_vec())
    }
}

pub fn needs_migration(data: &[u8]) -> bool {
    cfg!(target_os = "macos") && data.first() == Some(&b'{')
}

#[cfg(all(test, windows))]
mod tests {
    #[test]
    fn protects_credentials_for_the_current_windows_user() {
        let original = b"{\"refresh_token\":\"test-only-token\"}";
        let encrypted = super::encrypt(original).unwrap();
        assert!(
            !encrypted
                .windows(15)
                .any(|window| window == b"test-only-token")
        );
        assert_eq!(super::decrypt(&encrypted).unwrap(), original);
    }
}
