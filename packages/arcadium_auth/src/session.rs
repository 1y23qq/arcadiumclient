use base64::Engine;
use chrono::{DateTime, TimeDelta, Utc};
use reqwest::{StatusCode, header::HeaderValue};
use serde::Deserialize;
use uuid::Uuid;

use crate::{AccountKind, MinecraftAccount};

const PROFILE_URL: &str = "https://api.minecraftservices.com/minecraft/profile";
const MAX_TOKEN_BYTES: usize = 8192;
const MAX_PROFILE_BYTES: usize = 64 * 1024;

#[derive(Debug, thiserror::Error)]
pub enum SessionLoginError {
    #[error("Enter a valid token and select its token type")]
    InvalidToken,
    #[error("This session has expired or was revoked. Add a new token or sign in with Microsoft")]
    Expired,
    #[error("This session does not have a Minecraft Java profile")]
    NoProfile,
    #[error("Minecraft could not verify this session. Check your connection and try again")]
    Unavailable,
    #[error("Minecraft returned an invalid profile")]
    InvalidProfile,
    #[error("Enter the Microsoft OAuth client ID that issued this refresh token")]
    InvalidClientId,
}

#[derive(Deserialize)]
struct SessionProfile {
    id: Uuid,
    name: String,
}

pub(crate) async fn validate_session(token: &str) -> Result<MinecraftAccount, SessionLoginError> {
    let client = reqwest::Client::builder()
        .redirect(reqwest::redirect::Policy::none())
        .tls_backend_rustls()
        .connect_timeout(std::time::Duration::from_secs(10))
        .timeout(std::time::Duration::from_secs(20))
        .build()
        .map_err(|_| SessionLoginError::Unavailable)?;
    validate_session_at(&client, token, PROFILE_URL).await
}

async fn validate_session_at(
    client: &reqwest::Client,
    raw: &str,
    endpoint: &str,
) -> Result<MinecraftAccount, SessionLoginError> {
    let token = normalize_token(raw)?;
    let mut authorization = HeaderValue::from_str(&format!("Bearer {token}"))
        .map_err(|_| SessionLoginError::InvalidToken)?;
    authorization.set_sensitive(true);
    let mut response = client
        .get(endpoint)
        .header(reqwest::header::AUTHORIZATION, authorization)
        .send()
        .await
        .map_err(|_| SessionLoginError::Unavailable)?;
    match response.status() {
        StatusCode::OK => {}
        StatusCode::UNAUTHORIZED | StatusCode::FORBIDDEN => return Err(SessionLoginError::Expired),
        StatusCode::NOT_FOUND => return Err(SessionLoginError::NoProfile),
        _ => return Err(SessionLoginError::Unavailable),
    }
    if response
        .content_length()
        .is_some_and(|len| len > MAX_PROFILE_BYTES as u64)
    {
        return Err(SessionLoginError::InvalidProfile);
    }
    let mut body = Vec::new();
    while let Some(chunk) = response
        .chunk()
        .await
        .map_err(|_| SessionLoginError::Unavailable)?
    {
        if chunk.len() > MAX_PROFILE_BYTES - body.len() {
            return Err(SessionLoginError::InvalidProfile);
        }
        body.extend_from_slice(&chunk);
    }
    let profile: SessionProfile =
        serde_json::from_slice(&body).map_err(|_| SessionLoginError::InvalidProfile)?;
    if profile.id.is_nil() || crate::validate_offline_username(&profile.name).is_err() {
        return Err(SessionLoginError::InvalidProfile);
    }
    let expires = expiry(token);
    if expires <= Utc::now() {
        return Err(SessionLoginError::Expired);
    }
    Ok(MinecraftAccount {
        id: profile.id,
        username: profile.name,
        access_token: token.to_string(),
        refresh_token: String::new(),
        oauth_client_id: None,
        expires,
        kind: AccountKind::Session,
    })
}

fn normalize_token(raw: &str) -> Result<&str, SessionLoginError> {
    let raw = raw.trim();
    let token = raw.strip_prefix("Bearer ").unwrap_or(raw).trim();
    if token.is_empty()
        || token.eq_ignore_ascii_case("Bearer")
        || token.len() > MAX_TOKEN_BYTES
        || !token
            .bytes()
            .all(|byte| byte.is_ascii_alphanumeric() || b"._~-+/=".contains(&byte))
    {
        return Err(SessionLoginError::InvalidToken);
    }
    Ok(token)
}

fn expiry(token: &str) -> DateTime<Utc> {
    let fallback = Utc::now() + TimeDelta::hours(24);
    // Claims only bound local expiry. The authenticated profile response supplies identity.
    token
        .split('.')
        .nth(1)
        .and_then(|claims| {
            base64::engine::general_purpose::URL_SAFE_NO_PAD
                .decode(claims)
                .ok()
        })
        .and_then(|claims| serde_json::from_slice::<serde_json::Value>(&claims).ok())
        .and_then(|claims| claims["exp"].as_i64())
        .and_then(|exp| DateTime::from_timestamp(exp, 0))
        .map_or(fallback, |expiry| expiry.min(fallback))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::{Read, Write};
    use std::net::TcpListener;

    async fn reply(status: &str, body: &str) -> Result<MinecraftAccount, SessionLoginError> {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let endpoint = format!(
            "http://{}/minecraft/profile",
            listener.local_addr().unwrap()
        );
        let response = format!(
            "HTTP/1.1 {status}\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}",
            body.len()
        );
        let server = std::thread::spawn(move || {
            let (mut stream, _) = listener.accept().unwrap();
            let mut request = [0; 16384];
            let count = stream.read(&mut request).unwrap();
            let request = String::from_utf8_lossy(&request[..count]);
            assert!(
                request
                    .to_ascii_lowercase()
                    .contains("authorization: bearer fixture-access")
            );
            stream.write_all(response.as_bytes()).unwrap();
        });
        let client = reqwest::Client::builder()
            .redirect(reqwest::redirect::Policy::none())
            .build()
            .unwrap();
        let result = validate_session_at(&client, "Bearer fixture-access", &endpoint).await;
        server.join().unwrap();
        result
    }

    #[tokio::test]
    async fn authenticated_profile_supplies_identity_and_keeps_token_out_of_debug() {
        let account = reply(
            "200 OK",
            r#"{"id":"b50ad385829d3141a2167e7d7539ba7f","name":"Steve"}"#,
        )
        .await
        .unwrap();
        assert_eq!(account.kind, AccountKind::Session);
        assert_eq!(account.username, "Steve");
        assert_eq!(account.access_token, "fixture-access");
        assert!(account.refresh_token.is_empty());
        assert!(!format!("{account:?}").contains("fixture-access"));
    }

    #[tokio::test]
    async fn rejects_expired_missing_malformed_and_unavailable_profiles_without_echoing_body() {
        for (status, body, expected) in [
            ("401 Unauthorized", "private-response", "expired"),
            ("403 Forbidden", "private-response", "expired"),
            ("404 Not Found", "private-response", "profile"),
            (
                "500 Internal Server Error",
                "private-response",
                "connection",
            ),
            ("200 OK", "private-response", "invalid profile"),
            (
                "200 OK",
                r#"{"id":"00000000000000000000000000000000","name":"Steve"}"#,
                "invalid profile",
            ),
        ] {
            let message = reply(status, body).await.unwrap_err().to_string();
            assert!(message.contains(expected), "{message}");
            assert!(!message.contains("private-response"));
        }
    }

    #[test]
    fn rejects_empty_oversized_and_header_injection_tokens() {
        for token in [
            "".to_string(),
            "Bearer ".to_string(),
            "bad\r\nX-Token: private".to_string(),
            "x".repeat(MAX_TOKEN_BYTES + 1),
        ] {
            assert!(normalize_token(&token).is_err());
        }
        assert_eq!(
            normalize_token("  Bearer fixture-access  ").unwrap(),
            "fixture-access"
        );
    }

    #[test]
    fn local_expiry_is_bounded_even_for_untrusted_claims() {
        let exp = (Utc::now() + TimeDelta::minutes(30)).timestamp();
        let claims = base64::engine::general_purpose::URL_SAFE_NO_PAD
            .encode(format!(r#"{{"exp":{exp},"name":"Untrusted"}}"#));
        assert_eq!(
            expiry(&format!("header.{claims}.signature")).timestamp(),
            exp
        );
        assert!(expiry("opaque") <= Utc::now() + TimeDelta::hours(24));
    }
}
