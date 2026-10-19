//! Construct [`AuthService`] in the composition layer and pass it down nothing
//! in here reaches for a global or a database
//! Credentials persist in the platform-protected `auth.dat` store

mod cookie;
mod data;
mod diagnostics;
mod error;
mod msa;
mod offline;
mod service;
mod session;
mod store;

pub use cookie::{CookieConsentCookie, CookieConsentRequest, CookieImportError, CookieSameSite};
pub use data::{
    AccountKind, BrowserLogin, DeviceCodeLogin, MicrosoftLoginSession, MinecraftAccount,
};
pub use diagnostics::{AuthErrorGuidance, AuthErrorSample, diagnose_auth_error, preview_samples};
pub use error::{AuthError, AuthResult, MinecraftAuthError, MinecraftAuthStep};
pub use msa::PendingBrowserLogin;
pub use offline::{offline_account, offline_uuid, validate_offline_username};
pub use service::{
    AuthService, CookieBrowserLogin, CookieLogin, MICROSOFT_LOGIN_PROGRESS, VerifiedCookieAccount,
};
pub use session::SessionLoginError;
pub use store::CredentialsStore;

mod protected_store;
