use std::path::Path;
use std::time::Duration;

use chrono::{DateTime, Utc};
use reqwest::header::{COOKIE, HeaderValue, LOCATION, SET_COOKIE};
use serde::{Deserialize, Serialize};
use tokio::io::AsyncReadExt;
use url::Url;

use crate::BrowserLogin;

const MAX_FILE_SIZE: u64 = 1024 * 1024;
const MAX_COOKIES: usize = 1024;
const MAX_REDIRECTS: usize = 20;

#[derive(Clone, Copy, Serialize, Deserialize)]
pub enum CookieSameSite {
    Lax,
    Strict,
    None,
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct CookieConsentCookie {
    pub name: String,
    pub value: String,
    pub domain: String,
    pub path: String,
    pub include_subdomains: bool,
    pub secure: bool,
    pub http_only: bool,
    pub expires_unix: Option<i64>,
    pub same_site: Option<CookieSameSite>,
}

impl CookieConsentCookie {
    pub fn native_domain(&self) -> String {
        if self.include_subdomains {
            format!(".{}", self.domain)
        } else {
            self.domain.clone()
        }
    }
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct CookieConsentRequest {
    pub authorize_url: String,
    pub redirect_uri: String,
    pub state: String,
    pub cookies: Vec<CookieConsentCookie>,
}

impl CookieConsentRequest {
    pub fn validate(&self) -> Result<(), CookieImportError> {
        use arcadium_common::constants::{MICROSOFT_CLIENT_ID, MINECRAFT_SCOPES};
        let invalid = || CookieImportError::AuthorizationRejected;
        if self.authorize_url.len() > 8192
            || self.state.len() != 43
            || !self
                .state
                .bytes()
                .all(|byte| byte.is_ascii_alphanumeric() || b"_-".contains(&byte))
        {
            return Err(invalid());
        }
        let url = Url::parse(&self.authorize_url).map_err(|_| invalid())?;
        let legacy = MICROSOFT_CLIENT_ID == "42a60a84-599d-44b2-a7c6-b00cdef1d6a2";
        let expected = if legacy {
            ("login.live.com", "/oauth20_authorize.srf")
        } else {
            (
                "login.microsoftonline.com",
                "/consumers/oauth2/v2.0/authorize",
            )
        };
        if !trusted_endpoint(&url)
            || url.host_str() != Some(expected.0)
            || url.path() != expected.1
            || url.fragment().is_some()
        {
            return Err(invalid());
        }
        let redirect = Url::parse(&self.redirect_uri).map_err(|_| invalid())?;
        if redirect.scheme() != "http"
            || redirect.host_str() != Some("localhost")
            || !redirect.username().is_empty()
            || redirect.password().is_some()
            || redirect.query().is_some()
            || redirect.fragment().is_some()
            || redirect.port().is_none_or(|port| port == 0)
            || (legacy && (redirect.port() != Some(25575) || redirect.path() != "/callback"))
            || (!legacy && redirect.path() != "/")
        {
            return Err(invalid());
        }
        let mut query = std::collections::HashMap::new();
        for (key, value) in url.query_pairs() {
            if query.insert(key.into_owned(), value.into_owned()).is_some() {
                return Err(invalid());
            }
        }
        for (key, expected) in [
            ("client_id", MICROSOFT_CLIENT_ID),
            ("response_type", "code"),
            ("response_mode", "query"),
            ("scope", MINECRAFT_SCOPES),
            ("state", self.state.as_str()),
            ("redirect_uri", self.redirect_uri.as_str()),
            ("code_challenge_method", "S256"),
        ] {
            if query.get(key).map(String::as_str) != Some(expected) {
                return Err(invalid());
            }
        }
        let challenge = query.get("code_challenge").ok_or_else(invalid)?;
        if challenge.len() != 43
            || !challenge
                .bytes()
                .all(|byte| byte.is_ascii_alphanumeric() || b"_-".contains(&byte))
            || query.get("prompt").is_some_and(|value| value != "consent")
            || query.len() != 8 + usize::from(query.contains_key("prompt"))
        {
            return Err(invalid());
        }
        if self.cookies.is_empty() || self.cookies.len() > MAX_COOKIES {
            return Err(CookieImportError::NoMicrosoftCookies);
        }
        if !self.cookies.iter().any(|cookie| {
            !cookie.value.is_empty()
                && cookie
                    .expires_unix
                    .is_none_or(|expiry| expiry > Utc::now().timestamp())
        }) {
            return Err(CookieImportError::NoMicrosoftCookies);
        }
        let mut size = 0usize;
        for (index, cookie) in self.cookies.iter().enumerate() {
            size = size
                .checked_add(
                    cookie.name.len()
                        + cookie.value.len()
                        + cookie.path.len()
                        + cookie.domain.len(),
                )
                .ok_or(CookieImportError::FileTooLarge)?;
            if size > MAX_FILE_SIZE as usize {
                return Err(CookieImportError::FileTooLarge);
            }
            let value = Cookie {
                domain: cookie.domain.clone(),
                host_only: !cookie.include_subdomains,
                path: cookie.path.clone(),
                secure: cookie.secure,
                http_only: cookie.http_only,
                expires: cookie.expires_unix,
                same_site: cookie.same_site,
                name: cookie.name.clone(),
                value: cookie.value.clone(),
            };
            if !microsoft_domain(&cookie.domain)
                || !value.validate()
                || !value.live(Utc::now().timestamp())
                || cookie.expires_unix.is_some_and(|value| value < 0)
            {
                return Err(CookieImportError::InvalidEntry { line: index + 1 });
            }
        }
        Ok(())
    }
}

#[derive(Debug, thiserror::Error)]
pub enum CookieImportError {
    #[error("Could not read the cookie file")]
    ReadFile,
    #[error("The cookie file is too large")]
    FileTooLarge,
    #[error("Choose a Netscape or JSON cookie export")]
    InvalidFormat,
    #[error("The cookie export contains an invalid entry at line {line}")]
    InvalidEntry { line: usize },
    #[error("The cookie file has no unexpired Microsoft sign-in cookies")]
    NoMicrosoftCookies,
    #[error("Microsoft needs you to confirm this cookie session in your browser")]
    InteractiveRequired,
    #[error("Microsoft needs permission to sign in with Arcadium")]
    ConsentRequired,
    #[error("Microsoft could not authorize this cookie session. Choose Microsoft to sign in again")]
    AuthorizationRejected,
    #[error("Could not connect to Microsoft. Check your connection and try again")]
    Connection,
    #[error("Microsoft sign-in is temporarily unavailable. Try again later")]
    ServiceUnavailable,
    #[error("Could not open the protected Microsoft confirmation window")]
    ConsentWindowUnavailable,
}

#[derive(Clone)]
struct Cookie {
    domain: String,
    host_only: bool,
    path: String,
    secure: bool,
    http_only: bool,
    expires: Option<i64>,
    same_site: Option<CookieSameSite>,
    name: String,
    value: String,
}

#[derive(Clone)]
pub(crate) struct ImportedCookies {
    cookies: Vec<Cookie>,
}

impl CookieImportError {
    pub fn needs_browser(&self) -> bool {
        matches!(self, Self::ConsentRequired | Self::InteractiveRequired)
    }
}

#[derive(Deserialize)]
struct JsonCookie {
    domain: String,
    #[serde(default, rename = "hostOnly")]
    host_only: Option<bool>,
    #[serde(default = "root_path")]
    path: String,
    #[serde(default)]
    secure: bool,
    #[serde(default, rename = "httpOnly")]
    http_only: bool,
    #[serde(default, rename = "sameSite")]
    same_site: Option<String>,
    #[serde(
        default,
        rename = "expirationDate",
        alias = "expires",
        alias = "expiry"
    )]
    expires: Option<f64>,
    name: String,
    value: String,
}

#[derive(Deserialize)]
#[serde(untagged)]
enum JsonExport {
    Array(Vec<JsonCookie>),
    Object { cookies: Vec<JsonCookie> },
}

fn root_path() -> String {
    "/".into()
}

fn microsoft_domain(domain: &str) -> bool {
    matches!(
        domain,
        "live.com" | "login.live.com" | "microsoftonline.com" | "login.microsoftonline.com"
    )
}

fn domain_matches(host: &str, domain: &str, host_only: bool) -> bool {
    host == domain
        || (!host_only
            && host
                .strip_suffix(domain)
                .is_some_and(|prefix| prefix.ends_with('.')))
}

fn path_matches(request: &str, cookie: &str) -> bool {
    request == cookie
        || request
            .strip_prefix(cookie)
            .is_some_and(|suffix| cookie.ends_with('/') || suffix.starts_with('/'))
}

impl Cookie {
    fn validate(&self) -> bool {
        !self.name.is_empty()
            && self
                .name
                .bytes()
                .all(|byte| byte.is_ascii_alphanumeric() || b"!#$%&'*+-.^_`|~".contains(&byte))
            && self.value.bytes().all(|byte| {
                byte.is_ascii() && !byte.is_ascii_control() && !b";\"\\".contains(&byte)
            })
            && self.path.starts_with('/')
            && !self.path.bytes().any(|byte| byte.is_ascii_control())
            && (!self.name.starts_with("__Secure-") || self.secure)
            && (!self.name.starts_with("__Host-")
                || (self.secure && self.host_only && self.path == "/"))
    }

    fn live(&self, now: i64) -> bool {
        self.expires.is_none_or(|expiry| expiry > now)
    }

    fn matches(&self, url: &Url, now: i64) -> bool {
        self.live(now)
            && (!self.secure || url.scheme() == "https")
            && url
                .host_str()
                .is_some_and(|host| domain_matches(host, &self.domain, self.host_only))
            && path_matches(url.path(), &self.path)
    }
}

impl ImportedCookies {
    pub(crate) async fn read(path: &Path) -> Result<Self, CookieImportError> {
        let metadata = tokio::fs::metadata(path)
            .await
            .map_err(|_| CookieImportError::ReadFile)?;
        if !metadata.is_file() {
            return Err(CookieImportError::ReadFile);
        }
        if metadata.len() > MAX_FILE_SIZE {
            return Err(CookieImportError::FileTooLarge);
        }
        let file = tokio::fs::File::open(path)
            .await
            .map_err(|_| CookieImportError::ReadFile)?;
        let mut data = Vec::new();
        file.take(MAX_FILE_SIZE + 1)
            .read_to_end(&mut data)
            .await
            .map_err(|_| CookieImportError::ReadFile)?;
        if data.len() as u64 > MAX_FILE_SIZE {
            return Err(CookieImportError::FileTooLarge);
        }
        let text = std::str::from_utf8(&data).map_err(|_| CookieImportError::InvalidFormat)?;
        Self::parse(text)
    }

    pub(crate) fn parse(text: &str) -> Result<Self, CookieImportError> {
        let text = text.trim_start_matches('\u{feff}').trim_start();
        let mut jar = Self {
            cookies: Vec::new(),
        };
        if text.starts_with('[') || text.starts_with('{') {
            let export: JsonExport =
                serde_json::from_str(text).map_err(|_| CookieImportError::InvalidFormat)?;
            let cookies = match export {
                JsonExport::Array(cookies) | JsonExport::Object { cookies } => cookies,
            };
            if cookies.len() > MAX_COOKIES {
                return Err(CookieImportError::FileTooLarge);
            }
            for (index, cookie) in cookies.into_iter().enumerate() {
                let domain = cookie.domain.trim_start_matches('.').to_ascii_lowercase();
                if !microsoft_domain(&domain) {
                    continue;
                }
                let expires = match cookie.expires {
                    Some(expiry)
                        if expiry.is_finite() && expiry >= 0.0 && expiry < i64::MAX as f64 =>
                    {
                        (expiry > 0.0).then_some(expiry as i64)
                    }
                    None => None,
                    _ => return Err(CookieImportError::InvalidEntry { line: index + 1 }),
                };
                let same_site = match cookie.same_site.as_deref() {
                    Some("lax" | "Lax") => Some(CookieSameSite::Lax),
                    Some("strict" | "Strict") => Some(CookieSameSite::Strict),
                    Some("none" | "None" | "no_restriction") => Some(CookieSameSite::None),
                    None | Some("unspecified") => None,
                    _ => return Err(CookieImportError::InvalidEntry { line: index + 1 }),
                };
                jar.insert(
                    Cookie {
                        domain,
                        host_only: cookie.host_only.unwrap_or(!cookie.domain.starts_with('.')),
                        path: cookie.path,
                        secure: cookie.secure,
                        http_only: cookie.http_only,
                        expires,
                        same_site,
                        name: cookie.name,
                        value: cookie.value,
                    },
                    index + 1,
                )?;
            }
        } else {
            let mut count = 0;
            for (index, line) in text.lines().enumerate() {
                let line = line.trim_end_matches('\r');
                if line.trim().is_empty()
                    || (line.starts_with('#') && !line.starts_with("#HttpOnly_"))
                {
                    continue;
                }
                count += 1;
                if count > MAX_COOKIES {
                    return Err(CookieImportError::FileTooLarge);
                }
                let fields: Vec<_> = line
                    .strip_prefix("#HttpOnly_")
                    .unwrap_or(line)
                    .splitn(7, '\t')
                    .collect();
                if fields.len() != 7 {
                    return Err(CookieImportError::InvalidEntry { line: index + 1 });
                }
                let domain = fields[0].trim_start_matches('.').to_ascii_lowercase();
                if !microsoft_domain(&domain) {
                    continue;
                }
                let host_only = match fields[1] {
                    "TRUE" => false,
                    "FALSE" => true,
                    _ => return Err(CookieImportError::InvalidEntry { line: index + 1 }),
                };
                let secure = match fields[3] {
                    "TRUE" => true,
                    "FALSE" => false,
                    _ => return Err(CookieImportError::InvalidEntry { line: index + 1 }),
                };
                let expiry = fields[4]
                    .parse::<i64>()
                    .map_err(|_| CookieImportError::InvalidEntry { line: index + 1 })?;
                jar.insert(
                    Cookie {
                        domain,
                        host_only,
                        path: fields[2].into(),
                        secure,
                        http_only: line.starts_with("#HttpOnly_"),
                        expires: (expiry != 0).then_some(expiry),
                        same_site: None,
                        name: fields[5].into(),
                        value: fields[6].into(),
                    },
                    index + 1,
                )?;
            }
        }
        if !jar
            .cookies
            .iter()
            .any(|cookie| !cookie.value.is_empty() && cookie.live(Utc::now().timestamp()))
        {
            return Err(CookieImportError::NoMicrosoftCookies);
        }
        Ok(jar)
    }

    fn insert(&mut self, cookie: Cookie, line: usize) -> Result<(), CookieImportError> {
        if !cookie.validate() {
            return Err(CookieImportError::InvalidEntry { line });
        }
        self.cookies.retain(|existing| {
            existing.domain != cookie.domain
                || existing.path != cookie.path
                || existing.name != cookie.name
        });
        self.cookies.push(cookie);
        Ok(())
    }

    fn header(&self, url: &Url) -> Option<HeaderValue> {
        let mut cookies: Vec<_> = self
            .cookies
            .iter()
            .filter(|cookie| cookie.matches(url, Utc::now().timestamp()))
            .collect();
        cookies.sort_by_key(|cookie| std::cmp::Reverse(cookie.path.len()));
        if cookies.is_empty() {
            return None;
        }
        let mut header = HeaderValue::from_str(
            &cookies
                .iter()
                .map(|cookie| format!("{}={}", cookie.name, cookie.value))
                .collect::<Vec<_>>()
                .join("; "),
        )
        .ok()?;
        header.set_sensitive(true);
        Some(header)
    }

    fn receive(&mut self, url: &Url, header: &str) {
        let Some(host) = url.host_str() else { return };
        let mut parts = header.split(';');
        let Some((name, value)) = parts.next().and_then(|pair| pair.split_once('=')) else {
            return;
        };
        let mut cookie = Cookie {
            domain: host.into(),
            host_only: true,
            path: url
                .path()
                .rsplit_once('/')
                .map_or(
                    "/",
                    |(parent, _)| if parent.is_empty() { "/" } else { parent },
                )
                .into(),
            secure: false,
            http_only: false,
            expires: None,
            same_site: None,
            name: name.trim().into(),
            value: value.trim().into(),
        };
        let mut max_age = None;
        for part in parts {
            let (key, value) = part.trim().split_once('=').unwrap_or((part.trim(), ""));
            match key.to_ascii_lowercase().as_str() {
                "domain" => {
                    cookie.domain = value.trim_start_matches('.').to_ascii_lowercase();
                    cookie.host_only = false;
                }
                "path" if value.starts_with('/') => cookie.path = value.into(),
                "secure" => cookie.secure = true,
                "httponly" => cookie.http_only = true,
                "samesite" => {
                    cookie.same_site = match value.to_ascii_lowercase().as_str() {
                        "lax" => Some(CookieSameSite::Lax),
                        "strict" => Some(CookieSameSite::Strict),
                        "none" => Some(CookieSameSite::None),
                        _ => None,
                    }
                }
                "max-age" => max_age = value.parse::<i64>().ok(),
                "expires" => {
                    cookie.expires = DateTime::parse_from_rfc2822(value)
                        .ok()
                        .map(|date| date.timestamp())
                }
                _ => {}
            }
        }
        if let Some(age) = max_age {
            cookie.expires = Some(Utc::now().timestamp().saturating_add(age));
        }
        if microsoft_domain(&cookie.domain)
            && domain_matches(host, &cookie.domain, cookie.host_only)
            && self.cookies.len() < MAX_COOKIES
        {
            let _ = self.insert(cookie, 0);
        }
    }

    pub(crate) fn consent_request(
        &self,
        login: &BrowserLogin,
    ) -> Result<CookieConsentRequest, CookieImportError> {
        let mut url =
            Url::parse(&login.auth_url).map_err(|_| CookieImportError::AuthorizationRejected)?;
        let pairs: Vec<_> = url
            .query_pairs()
            .filter(|(key, _)| key != "prompt")
            .map(|(key, value)| (key.into_owned(), value.into_owned()))
            .collect();
        url.set_query(None);
        url.query_pairs_mut().extend_pairs(pairs);
        let request = CookieConsentRequest {
            authorize_url: url.to_string(),
            redirect_uri: login.redirect_uri.clone(),
            state: login.state.clone(),
            cookies: self
                .cookies
                .iter()
                .filter(|cookie| cookie.live(Utc::now().timestamp()))
                .map(|cookie| CookieConsentCookie {
                    name: cookie.name.clone(),
                    value: cookie.value.clone(),
                    domain: cookie.domain.clone(),
                    path: cookie.path.clone(),
                    include_subdomains: !cookie.host_only,
                    secure: cookie.secure,
                    http_only: cookie.http_only,
                    expires_unix: cookie.expires,
                    same_site: cookie.same_site,
                })
                .collect(),
        };
        request.validate()?;
        Ok(request)
    }

    pub(crate) async fn authorization_code(
        mut self,
        login: &BrowserLogin,
    ) -> Result<String, CookieImportError> {
        let client = reqwest::Client::builder()
            .redirect(reqwest::redirect::Policy::none())
            .referer(false)
            .connect_timeout(Duration::from_secs(10))
            .timeout(Duration::from_secs(30))
            .user_agent(format!("Arcadium {}", env!("CARGO_PKG_VERSION")))
            .build()
            .map_err(|_| CookieImportError::Connection)?;
        let mut url = silent_authorization_url(login)?;
        for _ in 0..MAX_REDIRECTS {
            if let Some(code) = callback_code(&url, login)? {
                return Ok(code);
            }
            if !trusted_endpoint(&url) {
                return Err(CookieImportError::AuthorizationRejected);
            }
            let mut request = client.get(url.clone());
            if let Some(cookies) = self.header(&url) {
                request = request.header(COOKIE, cookies);
            }
            let response = request
                .send()
                .await
                .map_err(|_| CookieImportError::Connection)?;
            for header in response.headers().get_all(SET_COOKIE) {
                if let Ok(header) = header.to_str() {
                    self.receive(&url, header);
                }
            }
            if response.status().is_server_error() || response.status().as_u16() == 429 {
                return Err(CookieImportError::ServiceUnavailable);
            }
            if !response.status().is_redirection() {
                return Err(if response.status().is_success() {
                    CookieImportError::InteractiveRequired
                } else {
                    CookieImportError::AuthorizationRejected
                });
            }
            let location = response
                .headers()
                .get(LOCATION)
                .and_then(|value| value.to_str().ok())
                .ok_or(CookieImportError::AuthorizationRejected)?;
            url = url
                .join(location)
                .map_err(|_| CookieImportError::AuthorizationRejected)?;
        }
        Err(CookieImportError::AuthorizationRejected)
    }
}

fn trusted_endpoint(url: &Url) -> bool {
    url.scheme() == "https"
        && url.username().is_empty()
        && url.password().is_none()
        && url.port_or_known_default() == Some(443)
        && matches!(
            url.host_str(),
            Some("login.live.com" | "login.microsoftonline.com")
        )
}

fn silent_authorization_url(login: &BrowserLogin) -> Result<Url, CookieImportError> {
    let mut url =
        Url::parse(&login.auth_url).map_err(|_| CookieImportError::AuthorizationRejected)?;
    let pairs: Vec<_> = url
        .query_pairs()
        .filter(|(key, _)| key != "prompt")
        .map(|(key, value)| (key.into_owned(), value.into_owned()))
        .collect();
    url.set_query(None);
    url.query_pairs_mut()
        .extend_pairs(pairs)
        .append_pair("prompt", "none");
    Ok(url)
}

fn callback_code(url: &Url, login: &BrowserLogin) -> Result<Option<String>, CookieImportError> {
    let callback =
        Url::parse(&login.redirect_uri).map_err(|_| CookieImportError::AuthorizationRejected)?;
    if url.scheme() != callback.scheme()
        || url.host_str() != callback.host_str()
        || url.port_or_known_default() != callback.port_or_known_default()
        || url.path() != callback.path()
    {
        return Ok(None);
    }
    if !url.username().is_empty() || url.password().is_some() || url.fragment().is_some() {
        return Err(CookieImportError::AuthorizationRejected);
    }
    let query: Vec<_> = url.query_pairs().collect();
    if query.iter().filter(|(key, _)| key == "state").count() != 1
        || query
            .iter()
            .find(|(key, _)| key == "state")
            .map(|(_, value)| value.as_ref())
            != Some(login.state.as_str())
    {
        return Err(CookieImportError::AuthorizationRejected);
    }
    if let Some((_, error)) = query.iter().find(|(key, _)| key == "error") {
        return Err(match error.as_ref() {
            "consent_required" => CookieImportError::ConsentRequired,
            "interaction_required" | "login_required" | "account_selection_required" => {
                CookieImportError::InteractiveRequired
            }
            "server_error" | "temporarily_unavailable" => CookieImportError::ServiceUnavailable,
            _ => CookieImportError::AuthorizationRejected,
        });
    }
    let mut codes = query.iter().filter(|(key, _)| key == "code");
    let code = codes
        .next()
        .filter(|(_, value)| !value.is_empty())
        .ok_or(CookieImportError::AuthorizationRejected)?;
    if codes.next().is_some() {
        return Err(CookieImportError::AuthorizationRejected);
    }
    Ok(Some(code.1.to_string()))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn login() -> BrowserLogin {
        BrowserLogin { auth_url: "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?client_id=arcadium&prompt=select_account&code_challenge=challenge".into(), state: "expected-state".into(), redirect_uri: "http://localhost:25575/callback".into() }
    }

    async fn consent_fixture() -> CookieConsentRequest {
        let (browser, _listener) = crate::msa::begin_browser_login().await.unwrap();
        ImportedCookies::parse("#HttpOnly_login.live.com\tFALSE\t/\tTRUE\t0\t__Host-MSAAUTH\tfixture\n.login.live.com\tTRUE\t/\tTRUE\t0\tpreference\t\n")
            .unwrap().consent_request(&browser).unwrap()
    }

    #[tokio::test]
    async fn consent_request_preserves_session_and_empty_cookie_flags() {
        let request = consent_fixture().await;
        request.validate().unwrap();
        let query: std::collections::HashMap<_, _> = Url::parse(&request.authorize_url)
            .unwrap()
            .query_pairs()
            .into_owned()
            .collect();
        assert!(!query.contains_key("prompt"));
        assert!(request.cookies[0].http_only);
        assert!(!request.cookies[0].include_subdomains);
        assert_eq!(request.cookies[0].native_domain(), "login.live.com");
        assert_eq!(request.cookies[0].expires_unix, None);
        assert!(request.cookies[1].value.is_empty());
        assert!(request.cookies[1].include_subdomains);
        assert_eq!(request.cookies[1].native_domain(), ".login.live.com");
        let encoded = serde_json::to_vec(&request).unwrap();
        let decoded: CookieConsentRequest = serde_json::from_slice(&encoded).unwrap();
        decoded.validate().unwrap();
    }

    #[tokio::test]
    async fn consent_request_rejects_empty_expired_and_malformed_native_sessions() {
        let mut request = consent_fixture().await;
        request.cookies[0].value.clear();
        assert!(matches!(
            request.validate(),
            Err(CookieImportError::NoMicrosoftCookies)
        ));
        request.cookies[0].value = "fixture".into();
        request.cookies[0].expires_unix = Some(1);
        assert!(request.validate().is_err());
        request.cookies[0].expires_unix = None;
        request.cookies[0].domain = "login.live.com.example.com".into();
        assert!(request.validate().is_err());
        request.cookies[0].domain = "login.live.com".into();
        request.cookies[0].path = "/\r\n".into();
        assert!(request.validate().is_err());
    }

    #[tokio::test]
    async fn consent_request_rejects_foreign_registration_and_callback_mutations() {
        let request = consent_fixture().await;
        for (key, value) in [
            ("client_id", "foreign-app"),
            ("redirect_uri", "http://example.com/"),
            ("state", "wrong-state"),
            ("code_challenge_method", "plain"),
            ("prompt", "select_account"),
            ("scope", "User.Read"),
        ] {
            let mut changed: CookieConsentRequest =
                serde_json::from_value(serde_json::to_value(&request).unwrap()).unwrap();
            let mut url = Url::parse(&changed.authorize_url).unwrap();
            let query: Vec<_> = url
                .query_pairs()
                .filter(|(name, _)| name != key)
                .map(|(name, value)| (name.into_owned(), value.into_owned()))
                .collect();
            url.set_query(None);
            url.query_pairs_mut()
                .extend_pairs(query)
                .append_pair(key, value);
            changed.authorize_url = url.into();
            assert!(matches!(
                changed.validate(),
                Err(CookieImportError::AuthorizationRejected)
            ));
        }
        let mut duplicate = request;
        let mut url = Url::parse(&duplicate.authorize_url).unwrap();
        url.query_pairs_mut().append_pair("state", &duplicate.state);
        duplicate.authorize_url = url.into();
        assert!(matches!(
            duplicate.validate(),
            Err(CookieImportError::AuthorizationRejected)
        ));
    }

    #[test]
    fn json_import_retains_http_only_and_same_site_for_native_cookie_import() {
        let jar = ImportedCookies::parse(r#"[{"domain":"login.live.com","hostOnly":true,"path":"/","name":"session","value":"fixture","secure":true,"httpOnly":true,"sameSite":"strict"}]"#).unwrap();
        assert!(jar.cookies[0].http_only);
        assert!(matches!(
            jar.cookies[0].same_site,
            Some(CookieSameSite::Strict)
        ));
        assert!(jar.cookies[0].host_only);
    }

    #[test]
    fn imports_netscape_http_only_session_cookies_without_other_domains() {
        let jar = ImportedCookies::parse("# Netscape HTTP Cookie File\n#HttpOnly_login.live.com\tFALSE\t/\tTRUE\t0\t__Host-MSAAUTH\tfixture\n.example.com\tTRUE\t/\tTRUE\t0\tunrelated\tprivate\n").unwrap();
        assert_eq!(jar.cookies.len(), 1);
        let header = jar
            .header(&Url::parse("https://login.live.com/oauth20_authorize.srf").unwrap())
            .unwrap();
        assert!(header.is_sensitive());
        assert_eq!(header.to_str().unwrap(), "__Host-MSAAUTH=fixture");
        assert!(
            jar.header(&Url::parse("https://login.live.com.example.com/").unwrap())
                .is_none()
        );
        assert!(
            jar.header(&Url::parse("https://example.com/").unwrap())
                .is_none()
        );
        assert!(
            jar.header(&Url::parse("http://login.live.com/").unwrap())
                .is_none()
        );
    }

    #[test]
    fn imports_json_arrays_and_wrapped_exports() {
        let cookie = r#"{"domain":".login.live.com","name":"session","value":"fixture","secure":true,"expirationDate":4000000000}"#;
        for text in [format!("[{cookie}]"), format!("{{\"cookies\":[{cookie}]}}")] {
            assert_eq!(ImportedCookies::parse(&text).unwrap().cookies.len(), 1);
        }
    }

    #[test]
    fn preserves_empty_session_cookies_in_the_authorization_header() {
        let jar = ImportedCookies::parse("login.live.com\tFALSE\t/\tTRUE\t0\tsession\tfixture\nlogin.live.com\tFALSE\t/\tTRUE\t0\tpreference\t\n").unwrap();
        assert_eq!(
            jar.header(&Url::parse("https://login.live.com/").unwrap())
                .unwrap()
                .to_str()
                .unwrap(),
            "session=fixture; preference="
        );
        assert!(matches!(
            ImportedCookies::parse("login.live.com\tFALSE\t/\tTRUE\t0\tsession\t"),
            Err(CookieImportError::NoMicrosoftCookies)
        ));
    }

    #[test]
    fn rejects_expired_cookies_and_header_injection_without_secret_errors() {
        assert!(matches!(
            ImportedCookies::parse("login.live.com\tFALSE\t/\tTRUE\t1\tsession\tfixture"),
            Err(CookieImportError::NoMicrosoftCookies)
        ));
        let error =
            ImportedCookies::parse("login.live.com\tFALSE\t/\tTRUE\t0\tsession\tfixture\tprivate")
                .err()
                .unwrap();
        assert!(matches!(error, CookieImportError::InvalidEntry { .. }));
        assert!(!error.to_string().contains("private"));
        assert!(!format!("{error:?}").contains("fixture"));
    }

    #[test]
    fn respects_cookie_paths_and_replaces_rotated_cookies() {
        let mut jar =
            ImportedCookies::parse(".login.live.com\tTRUE\t/login\tTRUE\t0\tsession\tfixture")
                .unwrap();
        let url = Url::parse("https://login.live.com/login/auth").unwrap();
        assert!(jar.header(&url).is_some());
        assert!(
            jar.header(&Url::parse("https://login.live.com/login-other").unwrap())
                .is_none()
        );
        jar.receive(
            &url,
            "session=rotated; Domain=login.live.com; Path=/login; Secure; Max-Age=60",
        );
        assert_eq!(jar.cookies.len(), 1);
        assert_eq!(
            jar.header(&url).unwrap().to_str().unwrap(),
            "session=rotated"
        );
        jar.receive(
            &url,
            "session=; Domain=login.live.com; Path=/login; Secure; Max-Age=0",
        );
        assert!(jar.header(&url).is_none());
    }

    #[test]
    fn limits_requests_to_microsoft_authorization_hosts() {
        for url in [
            "https://login.live.com/",
            "https://login.microsoftonline.com/consumers/",
        ] {
            assert!(trusted_endpoint(&Url::parse(url).unwrap()));
        }
        for url in [
            "http://login.live.com/",
            "https://login.live.com:444/",
            "https://login.live.com.example.com/",
            "https://account.live.com/",
            "https://user@login.live.com/",
        ] {
            assert!(!trusted_endpoint(&Url::parse(url).unwrap()));
        }
    }

    #[test]
    fn silent_auth_preserves_registration_and_pkce() {
        let url = silent_authorization_url(&login()).unwrap();
        let query: std::collections::HashMap<_, _> = url.query_pairs().into_owned().collect();
        assert_eq!(query["prompt"], "none");
        assert_eq!(query["client_id"], "arcadium");
        assert_eq!(query["code_challenge"], "challenge");
    }

    #[test]
    fn callback_requires_exact_target_and_single_matching_state_and_code() {
        let login = login();
        let valid = Url::parse("http://localhost:25575/callback?state=expected-state&code=fixture")
            .unwrap();
        assert_eq!(
            callback_code(&valid, &login).unwrap(),
            Some("fixture".into())
        );
        assert!(
            callback_code(
                &Url::parse("http://localhost:25575/other?state=expected-state&code=fixture")
                    .unwrap(),
                &login
            )
            .unwrap()
            .is_none()
        );
        for query in [
            "state=wrong&code=fixture",
            "code=fixture",
            "state=expected-state&code=fixture&code=other",
            "state=expected-state&state=expected-state&code=fixture",
        ] {
            assert!(
                callback_code(
                    &Url::parse(&format!("http://localhost:25575/callback?{query}")).unwrap(),
                    &login
                )
                .is_err()
            );
        }
        let challenge = Url::parse("http://localhost:25575/callback?state=expected-state&error=consent_required&error_description=private").unwrap();
        assert!(matches!(
            callback_code(&challenge, &login),
            Err(CookieImportError::ConsentRequired)
        ));
    }
}
