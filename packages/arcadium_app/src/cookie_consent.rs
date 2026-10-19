use std::io::{BufRead, Read, Write};
use std::sync::{
    Arc,
    atomic::{AtomicBool, Ordering},
    mpsc,
};
use std::time::Duration;

use arcadium_auth::{CookieConsentCookie, CookieConsentRequest};
use url::Url;

const MAX_INPUT_BYTES: usize = 2 * 1024 * 1024;
const INPUT_TIMEOUT: Duration = Duration::from_secs(10);
const CONSENT_TIMEOUT: Duration = Duration::from_secs(330);

pub fn requested() -> bool {
    std::env::args_os().any(|arg| arg == "--cookie-consent")
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum Status {
    Complete,
    Cancelled,
    Unavailable,
    Failed,
}

fn report(status: &str) {
    let mut output = std::io::stdout().lock();
    let _ = writeln!(output, "{status}");
    let _ = output.flush();
}

pub fn run() -> anyhow::Result<()> {
    native::prepare_environment();
    let cancelled = Arc::new(AtomicBool::new(false));
    let (sender, receiver) = mpsc::sync_channel(1);
    let input_cancelled = cancelled.clone();
    std::thread::spawn(move || {
        let mut input = std::io::stdin().lock();
        let request = read_request(&mut input);
        if sender.send(request).is_ok() {
            let mut command = [0_u8; 1];
            let _ = input.read(&mut command);
        }
        input_cancelled.store(true, Ordering::Release);
    });
    let request = match receiver.recv_timeout(INPUT_TIMEOUT) {
        Ok(Ok(request)) => request,
        _ => {
            report("FAILED");
            anyhow::bail!("Cookie confirmation input was rejected")
        }
    };
    let status = native::run(request, cancelled);
    report(match status {
        Status::Complete => "COMPLETE",
        Status::Cancelled => "CANCELLED",
        Status::Unavailable => "UNAVAILABLE",
        Status::Failed => "FAILED",
    });
    match status {
        Status::Complete | Status::Cancelled => Ok(()),
        _ => anyhow::bail!("Cookie confirmation could not continue"),
    }
}

fn read_request(input: &mut impl BufRead) -> Result<CookieConsentRequest, ()> {
    let mut line = Vec::new();
    input
        .take((MAX_INPUT_BYTES + 1) as u64)
        .read_until(b'\n', &mut line)
        .map_err(|_| ())?;
    if line.is_empty() || line.len() > MAX_INPUT_BYTES || line.last() != Some(&b'\n') {
        return Err(());
    }
    let request: CookieConsentRequest = serde_json::from_slice(&line).map_err(|_| ())?;
    request.validate().map_err(|_| ())?;
    Ok(request)
}

struct NavigationPolicy {
    redirect: Url,
    state: String,
}

impl NavigationPolicy {
    fn new(request: &CookieConsentRequest) -> Result<Self, ()> {
        Ok(Self {
            redirect: Url::parse(&request.redirect_uri).map_err(|_| ())?,
            state: request.state.clone(),
        })
    }

    fn callback(&self, url: &Url) -> bool {
        url.scheme() == self.redirect.scheme()
            && url.host_str() == self.redirect.host_str()
            && url.port_or_known_default() == self.redirect.port_or_known_default()
            && url.path() == self.redirect.path()
            && url.username().is_empty()
            && url.password().is_none()
            && url.fragment().is_none()
    }

    fn allows(&self, value: &str) -> bool {
        let Ok(url) = Url::parse(value) else {
            return false;
        };
        if !url.username().is_empty() || url.password().is_some() {
            return false;
        }
        if self.callback(&url) {
            return self.callback_status(value).is_some();
        }
        url.scheme() == "https"
            && url.port_or_known_default() == Some(443)
            && matches!(
                url.host_str(),
                Some(
                    "login.live.com"
                        | "account.live.com"
                        | "consent.live.com"
                        | "login.microsoftonline.com"
                        | "login.microsoft.com"
                        | "account.microsoft.com"
                )
            )
    }

    fn callback_status(&self, value: &str) -> Option<Status> {
        let url = Url::parse(value).ok()?;
        if !self.callback(&url) {
            return None;
        }
        let mut state = None;
        let mut code = None;
        let mut error = None;
        for (name, value) in url.query_pairs() {
            match name.as_ref() {
                "state" if state.is_none() => state = Some(value.into_owned()),
                "code" if code.is_none() => code = Some(value.into_owned()),
                "error" if error.is_none() => error = Some(value.into_owned()),
                "state" | "code" | "error" => return None,
                _ => {}
            }
        }
        if state.as_deref() != Some(&self.state) {
            return None;
        }
        match (code.as_deref(), error.as_deref()) {
            (Some(code), None) if !code.is_empty() => Some(Status::Complete),
            (None, Some("access_denied")) => Some(Status::Cancelled),
            (None, Some(_)) => Some(Status::Failed),
            _ => None,
        }
    }
}

fn native_domain(cookie: &CookieConsentCookie) -> String {
    cookie.native_domain()
}

#[cfg(windows)]
fn verified_windows_expiry(original: i64, observed: f64, now: f64) -> bool {
    observed.is_finite()
        && now.is_finite()
        && observed > now
        && observed < i64::MAX as f64
        && observed <= original as f64
}

#[cfg(not(any(windows, target_os = "macos")))]
mod native {
    use super::*;
    pub(super) fn prepare_environment() {}
    pub(super) fn run(_: CookieConsentRequest, _: Arc<AtomicBool>) -> Status {
        Status::Unavailable
    }
}

#[cfg(any(windows, target_os = "macos"))]
mod native {
    use super::*;
    #[cfg(windows)]
    use std::path::{Path, PathBuf};
    use std::{cell::Cell, rc::Rc, time::Instant};
    use winit::{
        application::ApplicationHandler,
        dpi::LogicalSize,
        event::WindowEvent,
        event_loop::{ActiveEventLoop, ControlFlow, EventLoop},
        window::{Window, WindowId},
    };
    use wry::{NewWindowResponse, PageLoadEvent, PermissionResponse, WebView, WebViewBuilder};

    #[cfg(windows)]
    type NativeEvent = ();
    #[cfg(target_os = "macos")]
    enum NativeEvent {
        CookieSeeded(usize),
        CookiesVerified(bool),
    }

    fn new_event_loop() -> Result<EventLoop<NativeEvent>, winit::error::EventLoopError> {
        #[cfg(windows)]
        {
            EventLoop::new()
        }
        #[cfg(target_os = "macos")]
        {
            EventLoop::<NativeEvent>::with_user_event().build()
        }
    }

    pub(super) fn run(request: CookieConsentRequest, cancelled: Arc<AtomicBool>) -> Status {
        let Ok(policy) = NavigationPolicy::new(&request) else {
            return Status::Failed;
        };
        let Ok(event_loop) = new_event_loop() else {
            return Status::Unavailable;
        };
        let mut app = ConsentWindow {
            request: Some(request),
            policy: Rc::new(policy),
            cancelled,
            status: Rc::new(Cell::new(None)),
            window: None,
            webview: None,
            started: Instant::now(),
            #[cfg(windows)]
            directory: None,
            #[cfg(windows)]
            context: None,
            #[cfg(target_os = "macos")]
            mac_request: None,
            #[cfg(target_os = "macos")]
            mac_seeded: 0,
            #[cfg(target_os = "macos")]
            mac_proxy: event_loop.create_proxy(),
        };
        let result = event_loop.run_app(&mut app);
        app.destroy();
        #[cfg(windows)]
        if let Some(directory) = app.directory.take() {
            if directory.cleanup().is_err() {
                return Status::Failed;
            }
        }
        if result.is_err() {
            Status::Unavailable
        } else {
            app.status.get().unwrap_or(Status::Cancelled)
        }
    }

    pub(super) fn prepare_environment() {
        for name in std::env::vars_os()
            .map(|(name, _)| name)
            .filter(|name| {
                name.to_string_lossy()
                    .to_ascii_uppercase()
                    .starts_with("WEBVIEW2_")
            })
            .collect::<Vec<_>>()
        {
            // The helper starts before any UI or worker threads.
            unsafe {
                std::env::remove_var(name);
            }
        }
    }

    struct ConsentWindow {
        request: Option<CookieConsentRequest>,
        policy: Rc<NavigationPolicy>,
        cancelled: Arc<AtomicBool>,
        status: Rc<Cell<Option<Status>>>,
        window: Option<Window>,
        webview: Option<WebView>,
        started: Instant,
        #[cfg(windows)]
        directory: Option<OwnedDirectory>,
        #[cfg(windows)]
        context: Option<wry::WebContext>,
        #[cfg(target_os = "macos")]
        mac_request: Option<Rc<CookieConsentRequest>>,
        #[cfg(target_os = "macos")]
        mac_seeded: usize,
        #[cfg(target_os = "macos")]
        mac_proxy: winit::event_loop::EventLoopProxy<NativeEvent>,
    }

    impl ConsentWindow {
        fn destroy(&mut self) {
            #[cfg(target_os = "macos")]
            self.mac_request.take();
            self.webview.take();
            self.window.take();
            #[cfg(windows)]
            self.context.take();
        }

        fn initialize(&mut self, event_loop: &ActiveEventLoop) -> Result<(), Status> {
            if self.cancelled.load(Ordering::Acquire) {
                return Err(Status::Cancelled);
            }
            let request = self.request.take().ok_or(Status::Failed)?;
            let window = event_loop
                .create_window(
                    Window::default_attributes()
                        .with_title("Arcadium Client")
                        .with_inner_size(LogicalSize::new(720.0, 720.0))
                        .with_min_inner_size(LogicalSize::new(520.0, 480.0))
                        .with_visible(false),
                )
                .map_err(|_| Status::Unavailable)?;
            #[cfg(windows)]
            {
                self.directory = Some(OwnedDirectory::new().map_err(|_| Status::Unavailable)?);
                self.context = Some(wry::WebContext::new(Some(
                    self.directory.as_ref().unwrap().path.clone(),
                )));
            }
            #[cfg(windows)]
            let builder = {
                use wry::WebViewBuilderExtWindows;
                WebViewBuilder::new_with_web_context(self.context.as_mut().unwrap())
                    .with_profile_name(self.directory.as_ref().unwrap().profile.clone())
                    .with_additional_browser_args("")
                    .with_browser_accelerator_keys(false)
                    .with_default_context_menus(false)
            };
            #[cfg(target_os = "macos")]
            let builder = WebViewBuilder::new();
            let policy = self.policy.clone();
            let finished_policy = self.policy.clone();
            let finished_status = self.status.clone();
            let webview = builder
                .with_incognito(true)
                .with_devtools(false)
                .with_clipboard(false)
                .with_autoplay(false)
                .with_general_autofill_enabled(false)
                .with_back_forward_navigation_gestures(false)
                .with_navigation_handler(move |url| policy.allows(&url))
                .with_new_window_req_handler(|_, _| NewWindowResponse::Deny)
                .with_download_started_handler(|_, _| false)
                .with_permission_handler(|_| PermissionResponse::Deny)
                .with_on_page_load_handler(move |event, url| {
                    if matches!(event, PageLoadEvent::Finished) {
                        if let Some(status) = finished_policy.callback_status(&url) {
                            finished_status.set(Some(status));
                        }
                    }
                })
                .build(&window)
                .map_err(|_| Status::Unavailable)?;
            #[cfg(windows)]
            verify_privacy(&webview, self.directory.as_ref().unwrap())
                .map_err(|_| Status::Unavailable)?;
            #[cfg(target_os = "macos")]
            verify_privacy(&webview).map_err(|_| Status::Unavailable)?;
            #[cfg(windows)]
            {
                for cookie in &request.cookies {
                    if self.cancelled.load(Ordering::Acquire) {
                        return Err(Status::Cancelled);
                    }
                    if self.started.elapsed() >= CONSENT_TIMEOUT {
                        return Err(Status::Failed);
                    }
                    seed_cookie(&webview, cookie).map_err(|_| Status::Failed)?;
                }
                verify_cookies(&webview, &request.cookies).map_err(|_| Status::Failed)?;
                if self.cancelled.load(Ordering::Acquire) {
                    return Err(Status::Cancelled);
                }
                self.window = Some(window);
                self.webview = Some(webview);
                self.show_authorization(&request.authorize_url)
            }
            #[cfg(target_os = "macos")]
            {
                self.window = Some(window);
                self.webview = Some(webview);
                self.mac_request = Some(Rc::new(request));
                self.start_mac_cookie()
            }
        }

        fn show_authorization(&mut self, url: &str) -> Result<(), Status> {
            if self.cancelled.load(Ordering::Acquire) {
                return Err(Status::Cancelled);
            }
            let webview = self.webview.as_ref().ok_or(Status::Failed)?;
            webview.load_url(url).map_err(|_| Status::Failed)?;
            self.window
                .as_ref()
                .ok_or(Status::Failed)?
                .set_visible(true);
            report("READY");
            Ok(())
        }

        #[cfg(target_os = "macos")]
        fn start_mac_cookie(&mut self) -> Result<(), Status> {
            if self.cancelled.load(Ordering::Acquire) {
                return Err(Status::Cancelled);
            }
            let request = self.mac_request.as_ref().ok_or(Status::Failed)?;
            let webview = self.webview.as_ref().ok_or(Status::Failed)?;
            if let Some(cookie) = request.cookies.get(self.mac_seeded) {
                seed_cookie(webview, cookie, self.mac_seeded, self.mac_proxy.clone())
                    .map_err(|_| Status::Failed)
            } else {
                verify_cookies(webview, request.clone(), self.mac_proxy.clone());
                Ok(())
            }
        }
    }

    impl ApplicationHandler<NativeEvent> for ConsentWindow {
        fn resumed(&mut self, event_loop: &ActiveEventLoop) {
            if self.request.is_some() {
                if let Err(status) = self.initialize(event_loop) {
                    self.status.set(Some(status));
                    self.destroy();
                    event_loop.exit();
                }
            }
        }

        #[cfg(target_os = "macos")]
        fn user_event(&mut self, event_loop: &ActiveEventLoop, event: NativeEvent) {
            if self.status.get().is_some() {
                return;
            }
            let result = match event {
                NativeEvent::CookieSeeded(index) if index == self.mac_seeded => {
                    self.mac_seeded += 1;
                    self.start_mac_cookie()
                }
                NativeEvent::CookieSeeded(_) => Err(Status::Failed),
                NativeEvent::CookiesVerified(true) => {
                    if let Some(request) = self.mac_request.take() {
                        self.show_authorization(&request.authorize_url)
                    } else {
                        Err(Status::Failed)
                    }
                }
                NativeEvent::CookiesVerified(false) => Err(Status::Failed),
            };
            if let Err(status) = result {
                self.status.set(Some(status));
                self.destroy();
                event_loop.exit();
            }
        }

        fn window_event(&mut self, event_loop: &ActiveEventLoop, _: WindowId, event: WindowEvent) {
            if matches!(event, WindowEvent::CloseRequested | WindowEvent::Destroyed) {
                if self.status.get().is_none() {
                    self.status.set(Some(Status::Cancelled));
                }
                self.destroy();
                event_loop.exit();
            }
        }

        fn about_to_wait(&mut self, event_loop: &ActiveEventLoop) {
            if self.status.get().is_none() && self.cancelled.load(Ordering::Acquire) {
                self.status.set(Some(Status::Cancelled));
            }
            if self.status.get().is_none() && self.started.elapsed() >= CONSENT_TIMEOUT {
                self.status.set(Some(Status::Failed));
            }
            if self.status.get().is_some() {
                self.destroy();
                event_loop.exit();
            } else {
                event_loop.set_control_flow(ControlFlow::WaitUntil(
                    Instant::now() + Duration::from_millis(100),
                ));
            }
        }

        fn exiting(&mut self, _: &ActiveEventLoop) {
            self.destroy();
        }
    }

    #[cfg(windows)]
    struct OwnedDirectory {
        path: PathBuf,
        parent: PathBuf,
        profile: String,
    }

    #[cfg(windows)]
    impl OwnedDirectory {
        fn new() -> std::io::Result<Self> {
            let parent = std::fs::canonicalize(std::env::temp_dir())?;
            let supplied = std::env::var_os("ARCADIUM_COOKIE_WEBVIEW_DIR")
                .ok_or_else(|| std::io::Error::from(std::io::ErrorKind::PermissionDenied))?;
            let supplied = PathBuf::from(supplied);
            let metadata = std::fs::symlink_metadata(&supplied)?;
            if !supplied.is_absolute() || !metadata.is_dir() || metadata.file_type().is_symlink() {
                return Err(std::io::ErrorKind::PermissionDenied.into());
            }
            let path = std::fs::canonicalize(&supplied)?;
            let name = path
                .file_name()
                .ok_or(std::io::ErrorKind::PermissionDenied)?
                .to_string_lossy();
            let id = name
                .strip_prefix("arcadium-cookie-webview-")
                .and_then(|value| uuid::Uuid::parse_str(value).ok())
                .ok_or(std::io::ErrorKind::PermissionDenied)?;
            if name != format!("arcadium-cookie-webview-{id}")
                || path.parent() != Some(parent.as_path())
                || std::fs::read_dir(&path)?.next().is_some()
            {
                return Err(std::io::ErrorKind::PermissionDenied.into());
            }
            let profile = format!("Arcadium{}", id.simple());
            Ok(Self {
                path,
                parent,
                profile,
            })
        }

        fn owns(&self, path: &Path) -> bool {
            std::fs::canonicalize(path).is_ok_and(|resolved| {
                resolved == self.path && resolved.parent() == Some(self.parent.as_path())
            }) && self.path.file_name().is_some_and(|name| {
                name.to_string_lossy()
                    .starts_with("arcadium-cookie-webview-")
            })
        }

        fn cleanup(self) -> Result<bool, ()> {
            if !self.owns(&self.path) {
                return Err(());
            }
            for _ in 0..40 {
                if std::fs::remove_dir_all(&self.path).is_ok() || !self.path.exists() {
                    return Ok(true);
                }
                std::thread::sleep(Duration::from_millis(100));
            }
            // The parent owns this path and retries after the helper exits.
            Ok(false)
        }
    }

    #[cfg(windows)]
    unsafe fn string_value(
        get: impl FnOnce(*mut windows::core::PWSTR) -> windows::core::Result<()>,
    ) -> windows::core::Result<String> {
        let mut value = windows::core::PWSTR::null();
        let result = get(&mut value);
        let text = if result.is_ok() {
            unsafe { value.to_string() }.unwrap_or_default()
        } else {
            String::new()
        };
        if !value.is_null() {
            unsafe {
                windows::Win32::System::Com::CoTaskMemFree(Some(value.0.cast()));
            }
        }
        result.map(|_| text)
    }

    #[cfg(windows)]
    fn verify_privacy(view: &WebView, directory: &OwnedDirectory) -> windows::core::Result<()> {
        use webview2_com::Microsoft::Web::WebView2::Win32::*;
        use windows::core::{BOOL, Interface};
        use wry::WebViewExtWindows;
        unsafe {
            let environment = view.environment().cast::<ICoreWebView2Environment7>()?;
            let actual = string_value(|value| environment.UserDataFolder(value))?;
            let profile = view.webview().cast::<ICoreWebView2_13>()?.Profile()?;
            let name = string_value(|value| profile.ProfileName(value))?;
            let mut private = BOOL::default();
            profile.IsInPrivateModeEnabled(&mut private)?;
            if !private.as_bool()
                || name != directory.profile
                || !directory.owns(Path::new(&actual))
            {
                return Err(windows::core::Error::from_hresult(windows::core::HRESULT(
                    0x80070005_u32 as i32,
                )));
            }
            let settings = view
                .webview()
                .Settings()?
                .cast::<ICoreWebView2Settings4>()?;
            settings.SetIsPasswordAutosaveEnabled(false)?;
            settings.SetIsGeneralAutofillEnabled(false)?;
            Ok(())
        }
    }

    #[cfg(windows)]
    fn seed_cookie(view: &WebView, source: &CookieConsentCookie) -> windows::core::Result<()> {
        use arcadium_auth::CookieSameSite;
        use webview2_com::Microsoft::Web::WebView2::Win32::*;
        use windows::core::{HSTRING, Interface};
        use wry::WebViewExtWindows;
        unsafe {
            let manager = view.webview().cast::<ICoreWebView2_2>()?.CookieManager()?;
            let cookie = manager.CreateCookie(
                &HSTRING::from(&source.name),
                &HSTRING::from(&source.value),
                &HSTRING::from(native_domain(source)),
                &HSTRING::from(&source.path),
            )?;
            cookie.SetIsHttpOnly(source.http_only)?;
            cookie.SetIsSecure(source.secure)?;
            cookie.SetExpires(source.expires_unix.map_or(-1.0, |expiry| expiry as f64))?;
            if let Some(policy) = &source.same_site {
                cookie.SetSameSite(match policy {
                    CookieSameSite::Lax => COREWEBVIEW2_COOKIE_SAME_SITE_KIND_LAX,
                    CookieSameSite::Strict => COREWEBVIEW2_COOKIE_SAME_SITE_KIND_STRICT,
                    CookieSameSite::None => COREWEBVIEW2_COOKIE_SAME_SITE_KIND_NONE,
                })?;
            }
            manager.AddOrUpdateCookie(&cookie)
        }
    }

    #[cfg(windows)]
    fn verify_cookies(view: &WebView, expected: &[CookieConsentCookie]) -> Result<(), ()> {
        use webview2_com::{GetCookiesCompletedHandler, Microsoft::Web::WebView2::Win32::*};
        use windows::core::{Interface, PCWSTR};
        use wry::WebViewExtWindows;
        let (sender, receiver) = mpsc::channel();
        unsafe {
            let manager = view
                .webview()
                .cast::<ICoreWebView2_2>()
                .map_err(|_| ())?
                .CookieManager()
                .map_err(|_| ())?;
            manager
                .GetCookies(
                    PCWSTR::null(),
                    &GetCookiesCompletedHandler::create(Box::new(move |error, cookies| {
                        let _ = sender.send(if error.is_ok() { cookies } else { None });
                        Ok(())
                    })),
                )
                .map_err(|_| ())?;
            let cookies = webview2_com::wait_with_pump(receiver)
                .map_err(|_| ())?
                .ok_or(())?;
            let mut count = 0;
            cookies.Count(&mut count).map_err(|_| ())?;
            if count as usize != expected.len() {
                return Err(());
            }
            let mut actual = Vec::new();
            for index in 0..count {
                let cookie = cookies.GetValueAtIndex(index).map_err(|_| ())?;
                actual.push((
                    string_value(|value| cookie.Name(value)).map_err(|_| ())?,
                    string_value(|value| cookie.Value(value)).map_err(|_| ())?,
                    string_value(|value| cookie.Domain(value)).map_err(|_| ())?,
                    string_value(|value| cookie.Path(value)).map_err(|_| ())?,
                    cookie,
                ));
            }
            let now = std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map_err(|_| ())?
                .as_secs_f64();
            for source in expected {
                let Some((_, _, _, _, cookie)) =
                    actual.iter().find(|(name, value, domain, path, _)| {
                        name == &source.name
                            && value == &source.value
                            && domain == &native_domain(source)
                            && path == &source.path
                    })
                else {
                    return Err(());
                };
                let mut secure = windows::core::BOOL::default();
                let mut http_only = windows::core::BOOL::default();
                let mut session = windows::core::BOOL::default();
                let mut expires = 0.0;
                cookie.IsSecure(&mut secure).map_err(|_| ())?;
                cookie.IsHttpOnly(&mut http_only).map_err(|_| ())?;
                cookie.IsSession(&mut session).map_err(|_| ())?;
                cookie.Expires(&mut expires).map_err(|_| ())?;
                if secure.as_bool() != source.secure
                    || http_only.as_bool() != source.http_only
                    || session.as_bool() != source.expires_unix.is_none()
                    || source
                        .expires_unix
                        .is_some_and(|expiry| !verified_windows_expiry(expiry, expires, now))
                {
                    return Err(());
                }
                if let Some(policy) = source.same_site {
                    use arcadium_auth::CookieSameSite;
                    let expected_policy = match policy {
                        CookieSameSite::Lax => COREWEBVIEW2_COOKIE_SAME_SITE_KIND_LAX,
                        CookieSameSite::Strict => COREWEBVIEW2_COOKIE_SAME_SITE_KIND_STRICT,
                        CookieSameSite::None => COREWEBVIEW2_COOKIE_SAME_SITE_KIND_NONE,
                    };
                    let mut actual_policy = COREWEBVIEW2_COOKIE_SAME_SITE_KIND_LAX;
                    cookie.SameSite(&mut actual_policy).map_err(|_| ())?;
                    if actual_policy != expected_policy {
                        return Err(());
                    }
                }
            }
            Ok(())
        }
    }

    #[cfg(target_os = "macos")]
    fn verify_privacy(view: &WebView) -> Result<(), ()> {
        use wry::WebViewExtMacOS;
        if unsafe {
            view.webview()
                .configuration()
                .websiteDataStore()
                .isPersistent()
        } {
            Err(())
        } else {
            Ok(())
        }
    }

    #[cfg(target_os = "macos")]
    fn seed_cookie(
        view: &WebView,
        source: &CookieConsentCookie,
        index: usize,
        proxy: winit::event_loop::EventLoopProxy<NativeEvent>,
    ) -> Result<(), ()> {
        use arcadium_auth::CookieSameSite;
        use objc2::{rc::Retained, runtime::AnyObject};
        use objc2_foundation::*;
        use wry::WebViewExtMacOS;
        unsafe {
            let name = NSString::from_str(&source.name);
            let value = NSString::from_str(&source.value);
            let domain = NSString::from_str(&native_domain(source));
            let path = NSString::from_str(&source.path);
            let properties: Retained<NSMutableDictionary<NSHTTPCookiePropertyKey, AnyObject>> =
                NSMutableDictionary::from_slices(
                    &[
                        NSHTTPCookieName,
                        NSHTTPCookieValue,
                        NSHTTPCookieDomain,
                        NSHTTPCookiePath,
                    ],
                    &[&name, &value, &domain, &path],
                );
            if source.secure {
                properties.insert(NSHTTPCookieSecure, ns_string!("TRUE"));
            }
            if source.http_only {
                properties.insert(ns_string!("HttpOnly"), ns_string!("TRUE"));
            }
            if let Some(expires) = source.expires_unix {
                properties.insert(
                    NSHTTPCookieExpires,
                    &*NSDate::dateWithTimeIntervalSince1970(expires as f64),
                );
            } else {
                properties.insert(NSHTTPCookieDiscard, ns_string!("TRUE"));
            }
            if let Some(policy) = &source.same_site {
                match policy {
                    CookieSameSite::Lax => {
                        properties.insert(ns_string!("SameSite"), ns_string!("lax"));
                    }
                    CookieSameSite::Strict => {
                        properties.insert(ns_string!("SameSite"), ns_string!("strict"));
                    }
                    CookieSameSite::None => {}
                }
            }
            let cookie = NSHTTPCookie::cookieWithProperties(&properties).ok_or(())?;
            view.webview()
                .configuration()
                .websiteDataStore()
                .httpCookieStore()
                .setCookie_completionHandler(
                    &cookie,
                    Some(&block2::RcBlock::new(move || {
                        let _ = proxy.send_event(NativeEvent::CookieSeeded(index));
                    })),
                );
            Ok(())
        }
    }

    #[cfg(target_os = "macos")]
    fn verify_cookies(
        view: &WebView,
        expected: Rc<CookieConsentRequest>,
        proxy: winit::event_loop::EventLoopProxy<NativeEvent>,
    ) {
        use objc2_foundation::{NSArray, NSHTTPCookie};
        use wry::WebViewExtMacOS;
        unsafe {
            view.webview()
                .configuration()
                .websiteDataStore()
                .httpCookieStore()
                .getAllCookies(&block2::RcBlock::new(
                    move |cookies: std::ptr::NonNull<NSArray<NSHTTPCookie>>| {
                        let verified = matches_mac_cookies(cookies.as_ref(), &expected.cookies);
                        let _ = proxy.send_event(NativeEvent::CookiesVerified(verified));
                    },
                ));
        }
    }

    #[cfg(target_os = "macos")]
    fn matches_mac_cookies(
        cookies: &objc2_foundation::NSArray<objc2_foundation::NSHTTPCookie>,
        expected: &[CookieConsentCookie],
    ) -> bool {
        let actual = cookies.to_vec();
        if actual.len() != expected.len() {
            return false;
        }
        for source in expected {
            let Some(cookie) = actual.iter().find(|cookie| {
                cookie.name().to_string() == source.name
                    && cookie.value().to_string() == source.value
                    && cookie.domain().to_string() == native_domain(source)
                    && cookie.path().to_string() == source.path
            }) else {
                return false;
            };
            if cookie.isSecure() != source.secure
                || cookie.isHTTPOnly() != source.http_only
                || cookie.isSessionOnly() != source.expires_unix.is_none()
                || source.expires_unix.is_some_and(|expires| {
                    cookie.expiresDate().is_none_or(|date| {
                        (date.timeIntervalSince1970() - expires as f64).abs() > 1.0
                    })
                })
            {
                return false;
            }
            if let Some(policy) = source.same_site {
                use arcadium_auth::CookieSameSite;
                let actual_policy = cookie
                    .sameSitePolicy()
                    .map(|value| value.to_string().to_ascii_lowercase());
                let matches = match policy {
                    CookieSameSite::Lax => actual_policy.as_deref() == Some("lax"),
                    CookieSameSite::Strict => actual_policy.as_deref() == Some("strict"),
                    CookieSameSite::None => {
                        actual_policy.is_none() || actual_policy.as_deref() == Some("none")
                    }
                };
                if !matches {
                    return false;
                }
            }
        }
        true
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn policy() -> NavigationPolicy {
        NavigationPolicy {
            redirect: Url::parse("http://localhost:25575/callback").unwrap(),
            state: "fixture-state".into(),
        }
    }

    #[test]
    fn navigation_rejects_other_schemes_hosts_credentials_and_ports() {
        let policy = policy();
        assert!(policy.allows("https://login.live.com/oauth20_authorize.srf"));
        for value in [
            "http://login.live.com/",
            "https://login.live.com.evil.test/",
            "https://evil.test/",
            "https://user@login.live.com/",
            "https://login.live.com:444/",
            "file:///tmp/cookies",
            "javascript:alert(1)",
            "about:blank",
        ] {
            assert!(!policy.allows(value));
        }
    }

    #[test]
    fn callback_requires_exact_origin_path_and_one_state() {
        let policy = policy();
        let good = "http://localhost:25575/callback?code=fixture&state=fixture-state";
        assert!(policy.allows(good));
        assert!(matches!(
            policy.callback_status(good),
            Some(Status::Complete)
        ));
        for value in [
            "http://localhost:25576/callback?code=fixture&state=fixture-state",
            "http://127.0.0.1:25575/callback?code=fixture&state=fixture-state",
            "http://localhost:25575/other?code=fixture&state=fixture-state",
            "http://localhost:25575/callback?code=fixture&state=wrong",
            "http://localhost:25575/callback?code=fixture&state=fixture-state&state=fixture-state",
            "http://localhost:25575/callback?code=&state=fixture-state",
            "http://localhost:25575/callback?code=fixture&state=fixture-state#fragment",
        ] {
            assert!(!policy.allows(value));
        }
    }

    #[test]
    fn denied_consent_is_cancellation_and_ambiguous_callbacks_are_blocked() {
        let policy = policy();
        assert!(matches!(
            policy.callback_status(
                "http://localhost:25575/callback?error=access_denied&state=fixture-state"
            ),
            Some(Status::Cancelled)
        ));
        assert!(matches!(
            policy.callback_status(
                "http://localhost:25575/callback?error=server_error&state=fixture-state"
            ),
            Some(Status::Failed)
        ));
        assert!(policy.callback_status("http://localhost:25575/callback?code=fixture&error=access_denied&state=fixture-state").is_none());
    }

    #[test]
    fn oversized_and_unterminated_private_input_are_rejected() {
        assert!(read_request(&mut std::io::Cursor::new(vec![b'x'; MAX_INPUT_BYTES + 1])).is_err());
        assert!(read_request(&mut std::io::Cursor::new(b"{}".to_vec())).is_err());
        assert!(read_request(&mut std::io::Cursor::new(b"{}\n".to_vec())).is_err());
    }

    #[test]
    fn native_domain_keeps_host_only_and_empty_cookie_values() {
        let mut cookie = CookieConsentCookie {
            name: "fixture".into(),
            value: String::new(),
            domain: "login.live.com".into(),
            path: "/".into(),
            include_subdomains: false,
            secure: true,
            http_only: true,
            expires_unix: None,
            same_site: None,
        };
        assert_eq!(native_domain(&cookie), "login.live.com");
        cookie.include_subdomains = true;
        assert_eq!(native_domain(&cookie), ".login.live.com");
        assert!(cookie.value.is_empty());
    }

    #[cfg(windows)]
    #[test]
    fn windows_cookie_expiry_allows_live_shortening_and_rejects_extensions() {
        let now = 1_800_000_000.0;
        let original = 1_900_000_000;
        assert!(verified_windows_expiry(original, original as f64, now));
        assert!(verified_windows_expiry(
            original,
            now + 400.0 * 86400.0,
            now
        ));
        assert!(verified_windows_expiry(original, now + 3600.0, now));
        for observed in [
            now - 1.0,
            now,
            original as f64 + 0.1,
            f64::NAN,
            f64::INFINITY,
            f64::NEG_INFINITY,
        ] {
            assert!(!verified_windows_expiry(original, observed, now));
        }
        assert!(!verified_windows_expiry(i64::MAX, i64::MAX as f64, now));
    }
}
