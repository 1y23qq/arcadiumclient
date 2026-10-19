use std::fs::{File, OpenOptions, TryLockError};
use std::io::{Read, Write};
use std::path::Path;
use std::sync::Weak;
use std::time::{Duration, Instant};

use serde::{Deserialize, Serialize};
use tokio::sync::watch;

const DEFAULT_ENDPOINT: &str = "https://presence-test.arcadiumclient.com";
const HEARTBEAT: Duration = Duration::from_secs(30);
const IDENTITY_FILE: &str = "usage-identity.json";

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct Identity {
    version: u8,
    installation_id: uuid::Uuid,
    secret: String,
}

impl Identity {
    fn valid(&self) -> bool {
        self.version == 1
            && self.installation_id.get_version_num() == 4
            && self.installation_id.get_variant() == uuid::Variant::RFC4122
            && self.secret.len() == 64
            && self
                .secret
                .bytes()
                .all(|v| v.is_ascii_digit() || (b'a'..=b'f').contains(&v))
    }
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct Heartbeat<'a> {
    installation_id: uuid::Uuid,
    session_id: uuid::Uuid,
    client: &'a str,
}

pub(crate) struct UsagePresence {
    identity: Option<Identity>,
    stop: watch::Sender<bool>,
    task: parking_lot::Mutex<Option<tokio::task::JoinHandle<()>>>,
}

impl UsagePresence {
    pub(crate) async fn initialize() -> Self {
        let identity = match arcadium_common::paths::config_dir() {
            Ok(root) => {
                let root = root.to_path_buf();
                match tokio::task::spawn_blocking(move || load_identity(&root)).await {
                    Ok(Ok(identity)) => Some(identity),
                    _ => {
                        tracing::warn!(
                            "The anonymous online count is unavailable for this launcher profile"
                        );
                        None
                    }
                }
            }
            Err(_) => None,
        };
        let (stop, _) = watch::channel(false);
        Self {
            identity,
            stop,
            task: parking_lot::Mutex::new(None),
        }
    }

    pub(crate) fn disabled() -> Self {
        let (stop, _) = watch::channel(false);
        Self {
            identity: None,
            stop,
            task: parking_lot::Mutex::new(None),
        }
    }

    pub(crate) fn start(&self, state: Weak<crate::LauncherState>) {
        let mut task = self.task.lock();
        if task.is_some() || *self.stop.borrow() {
            return;
        }
        let Some(identity) = self.identity.clone() else {
            return;
        };
        let Some(endpoint) = configured_endpoint() else {
            return;
        };
        let stop = self.stop.subscribe();
        *task = Some(tokio::spawn(run(identity, endpoint, state, stop)));
    }

    pub(crate) async fn shutdown(&self) {
        self.stop.send_replace(true);
        let task = self.task.lock().take();
        if let Some(mut task) = task {
            if tokio::time::timeout(Duration::from_secs(4), &mut task)
                .await
                .is_err()
            {
                task.abort();
            }
        }
    }
}

pub(crate) fn allowed(settings: &crate::settings::LauncherSettings) -> bool {
    settings.online_count_enabled
        && (settings.seen_onboarding || settings.accepted_tos_version > 0)
        && !settings.declined_tos
}

pub(crate) fn configured_endpoint() -> Option<url::Url> {
    endpoint(
        std::env::var("ARCADIUM_USAGE_ENDPOINT")
            .as_deref()
            .unwrap_or(DEFAULT_ENDPOINT),
    )
}

fn endpoint(value: &str) -> Option<url::Url> {
    let uri = url::Url::parse(value).ok()?;
    let host = uri.host_str()?;
    let production = uri.scheme() == "https"
        && host == "presence-test.arcadiumclient.com"
        && uri.port().is_none();
    let local = uri.scheme() == "http" && matches!(host, "127.0.0.1" | "localhost" | "[::1]");
    if (!production && !local)
        || uri.path() != "/"
        || uri.query().is_some()
        || uri.fragment().is_some()
        || !uri.username().is_empty()
        || uri.password().is_some()
    {
        return None;
    }
    Some(uri)
}

fn read_identity(path: &Path) -> std::io::Result<Identity> {
    let mut bytes = Vec::new();
    File::open(path)?.take(2049).read_to_end(&mut bytes)?;
    if bytes.len() > 2048 {
        return Err(std::io::Error::other("Invalid online-count identity"));
    }
    let identity: Identity = serde_json::from_slice(&bytes)
        .map_err(|_| std::io::Error::other("Invalid online-count identity"))?;
    if !identity.valid() {
        return Err(std::io::Error::other("Invalid online-count identity"));
    }
    Ok(identity)
}

fn private_options() -> OpenOptions {
    let mut options = OpenOptions::new();
    options.read(true).write(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        options.mode(0o600);
    }
    options
}

fn load_identity(root: &Path) -> std::io::Result<Identity> {
    std::fs::create_dir_all(root)?;
    let lock = private_options()
        .create(true)
        .truncate(false)
        .open(root.join("usage-identity.lock"))?;
    let deadline = Instant::now() + Duration::from_secs(2);
    loop {
        match lock.try_lock() {
            Ok(()) => break,
            Err(TryLockError::WouldBlock) if Instant::now() < deadline => {
                std::thread::sleep(Duration::from_millis(10))
            }
            Err(error) => return Err(error.into()),
        }
    }
    let target = root.join(IDENTITY_FILE);
    match read_identity(&target) {
        Ok(identity) => return Ok(identity),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(error) => return Err(error),
    }
    let secret: [u8; 32] = rand::random();
    let identity = Identity {
        version: 1,
        installation_id: uuid::Uuid::new_v4(),
        secret: secret.iter().map(|byte| format!("{byte:02x}")).collect(),
    };
    let temporary = root.join(format!(".usage-identity-{}.tmp", uuid::Uuid::new_v4()));
    let result = (|| {
        let mut file = private_options().create_new(true).open(&temporary)?;
        file.write_all(&serde_json::to_vec(&identity)?)?;
        file.sync_all()?;
        drop(file);
        std::fs::rename(&temporary, &target)
    })();
    if result.is_err() {
        let _ = std::fs::remove_file(&temporary);
    }
    result?;
    Ok(identity)
}

async fn send(
    client: &reqwest::Client,
    url: &url::Url,
    identity: &Identity,
    session: uuid::Uuid,
    revoke: bool,
) -> bool {
    let mut url = url.clone();
    url.set_path("/v1/usage/heartbeat");
    let request = if revoke {
        client.delete(url)
    } else {
        client.post(url)
    };
    let result = request
        .bearer_auth(&identity.secret)
        .json(&Heartbeat {
            installation_id: identity.installation_id,
            session_id: session,
            client: "launcher",
        })
        .send()
        .await;
    let Ok(mut response) = result else {
        return false;
    };
    if !response.status().is_success()
        || response
            .content_length()
            .is_some_and(|length| length > 4096)
    {
        return false;
    }
    let mut received = 0usize;
    loop {
        match response.chunk().await {
            Ok(Some(chunk)) => {
                received = received.saturating_add(chunk.len());
                if received > 4096 {
                    return false;
                }
            }
            Ok(None) => return true,
            Err(_) => return false,
        }
    }
}

async fn run(
    identity: Identity,
    endpoint: url::Url,
    state: Weak<crate::LauncherState>,
    mut stop: watch::Receiver<bool>,
) {
    let Ok(client) = reqwest::Client::builder()
        .redirect(reqwest::redirect::Policy::none())
        .connect_timeout(Duration::from_secs(3))
        .timeout(Duration::from_secs(5))
        .user_agent(concat!("Arcadium/", env!("CARGO_PKG_VERSION")))
        .build()
    else {
        return;
    };
    let session = uuid::Uuid::new_v4();
    let mut lease = false;
    let mut clock = tokio::time::interval(HEARTBEAT);
    clock.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Skip);
    loop {
        tokio::select! {
            _ = stop.changed() => break,
            _ = clock.tick() => {
                let enabled = state.upgrade().is_some_and(|state| allowed(&state.settings.read()));
                if enabled { lease |= send(&client, &endpoint, &identity, session, false).await; }
                else if lease {
                    if send(&client, &endpoint, &identity, session, true).await { lease = false; }
                }
                if state.strong_count() == 0 { break; }
            }
        }
    }
    if lease {
        let _ = tokio::time::timeout(
            Duration::from_secs(2),
            send(&client, &endpoint, &identity, session, true),
        )
        .await;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn identities_are_private_stable_and_shared_across_simultaneous_starts() {
        let root =
            std::env::temp_dir().join(format!("arcadium-usage-test-{}", uuid::Uuid::new_v4()));
        let workers: Vec<_> = (0..8)
            .map(|_| {
                let root = root.clone();
                std::thread::spawn(move || load_identity(&root).unwrap())
            })
            .collect();
        let identities: Vec<_> = workers
            .into_iter()
            .map(|thread| thread.join().unwrap())
            .collect();
        for identity in &identities {
            assert!(identity.valid());
            assert_eq!(identity.installation_id, identities[0].installation_id);
            assert_eq!(identity.secret, identities[0].secret);
        }
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            assert_eq!(
                std::fs::metadata(root.join(IDENTITY_FILE))
                    .unwrap()
                    .permissions()
                    .mode()
                    & 0o777,
                0o600
            );
        }
        std::fs::remove_file(root.join(IDENTITY_FILE)).unwrap();
        std::fs::remove_file(root.join("usage-identity.lock")).unwrap();
        std::fs::remove_dir(root).unwrap();
    }

    #[test]
    fn invalid_identities_are_rejected_without_overwriting_them() {
        let root =
            std::env::temp_dir().join(format!("arcadium-usage-test-{}", uuid::Uuid::new_v4()));
        std::fs::create_dir(&root).unwrap();
        let file = root.join(IDENTITY_FILE);
        std::fs::write(&file, b"{broken").unwrap();
        assert!(load_identity(&root).is_err());
        assert_eq!(std::fs::read(&file).unwrap(), b"{broken");
        std::fs::write(&file, vec![b' '; 2049]).unwrap();
        assert!(load_identity(&root).is_err());
        std::fs::remove_file(file).unwrap();
        std::fs::remove_file(root.join("usage-identity.lock")).unwrap();
        std::fs::remove_dir(root).unwrap();
    }

    #[test]
    fn counts_require_the_online_setting_and_completed_setup() {
        let mut settings = crate::settings::LauncherSettings::default();
        assert!(!allowed(&settings));
        settings.seen_onboarding = true;
        assert!(allowed(&settings));
        settings.online_count_enabled = false;
        assert!(!allowed(&settings));
        settings.online_count_enabled = true;
        settings.declined_tos = true;
        assert!(!allowed(&settings));
    }

    #[test]
    fn endpoints_cannot_redirect_installation_secrets_to_other_hosts() {
        assert!(endpoint(DEFAULT_ENDPOINT).is_some());
        assert!(endpoint("http://127.0.0.1:8123").is_some());
        for value in [
            "",
            "https://attacker.example",
            "https://presence-test.arcadiumclient.com.evil.test",
            "http://presence-test.arcadiumclient.com",
            "https://secret@presence-test.arcadiumclient.com",
            "https://presence-test.arcadiumclient.com/?target=x",
            "https://presence-test.arcadiumclient.com/v1",
        ] {
            assert!(endpoint(value).is_none());
        }
    }

    #[tokio::test]
    async fn transport_uses_the_same_installation_and_releases_only_its_own_session() {
        use tokio::io::{AsyncReadExt, AsyncWriteExt};
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let origin = endpoint(&format!("http://{}", listener.local_addr().unwrap())).unwrap();
        let identity = Identity {
            version: 1,
            installation_id: uuid::Uuid::new_v4(),
            secret: "a".repeat(64),
        };
        let session = uuid::Uuid::new_v4();
        let expected_id = identity.installation_id;
        let server = tokio::spawn(async move {
            for method in ["POST", "POST", "DELETE"] {
                let (mut stream, _) = listener.accept().await.unwrap();
                let mut input = Vec::new();
                let boundary = loop {
                    let mut byte = [0u8; 1];
                    stream.read_exact(&mut byte).await.unwrap();
                    input.push(byte[0]);
                    assert!(input.len() < 4096);
                    if input.ends_with(b"\r\n\r\n") {
                        break input.len();
                    }
                };
                let headers = String::from_utf8(input[..boundary].to_vec())
                    .unwrap()
                    .to_ascii_lowercase();
                assert!(headers.starts_with(&format!(
                    "{} /v1/usage/heartbeat http/1.1",
                    method.to_ascii_lowercase()
                )));
                assert!(headers.contains(&format!("authorization: bearer {}", "a".repeat(64))));
                assert!(headers.contains("content-type: application/json"));
                let length: usize = headers
                    .lines()
                    .find_map(|line| line.strip_prefix("content-length: "))
                    .unwrap()
                    .parse()
                    .unwrap();
                let mut body = vec![0; length];
                stream.read_exact(&mut body).await.unwrap();
                let value: serde_json::Value = serde_json::from_slice(&body).unwrap();
                assert_eq!(value.as_object().unwrap().len(), 3);
                assert_eq!(value["installationId"], expected_id.to_string());
                assert_eq!(value["sessionId"], session.to_string());
                assert_eq!(value["client"], "launcher");
                stream.write_all(b"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 17\r\nConnection: close\r\n\r\n{\"accepted\":true}").await.unwrap();
            }
        });
        let client = reqwest::Client::builder()
            .no_proxy()
            .timeout(Duration::from_secs(2))
            .build()
            .unwrap();
        assert!(send(&client, &origin, &identity, session, false).await);
        assert!(send(&client, &origin, &identity, session, false).await);
        assert!(send(&client, &origin, &identity, session, true).await);
        tokio::time::timeout(Duration::from_secs(5), server)
            .await
            .unwrap()
            .unwrap();
    }

    #[tokio::test]
    async fn oversized_chunked_responses_are_rejected_before_the_body_finishes() {
        use tokio::io::{AsyncReadExt, AsyncWriteExt};
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let origin = endpoint(&format!("http://{}", listener.local_addr().unwrap())).unwrap();
        let identity = Identity {
            version: 1,
            installation_id: uuid::Uuid::new_v4(),
            secret: "a".repeat(64),
        };
        let server = tokio::spawn(async move {
            let (mut stream, _) = listener.accept().await.unwrap();
            let mut headers = Vec::new();
            while !headers.ends_with(b"\r\n\r\n") {
                headers.push(stream.read_u8().await.unwrap());
                assert!(headers.len() < 4096);
            }
            let headers = String::from_utf8(headers).unwrap().to_ascii_lowercase();
            let length: usize = headers
                .lines()
                .find_map(|line| line.strip_prefix("content-length: "))
                .unwrap()
                .parse()
                .unwrap();
            stream.read_exact(&mut vec![0; length]).await.unwrap();
            stream.write_all(b"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nTransfer-Encoding: chunked\r\n\r\n1001\r\n").await.unwrap();
            stream.write_all(&vec![b'x'; 4097]).await.unwrap();
            stream.write_all(b"\r\n").await.unwrap();
            let mut byte = [0u8; 1];
            let closed = tokio::time::timeout(Duration::from_secs(1), stream.read(&mut byte)).await;
            assert!(matches!(closed, Ok(Ok(0)) | Ok(Err(_))));
        });
        let client = reqwest::Client::builder()
            .no_proxy()
            .timeout(Duration::from_secs(3))
            .build()
            .unwrap();
        assert!(
            !tokio::time::timeout(
                Duration::from_secs(1),
                send(&client, &origin, &identity, uuid::Uuid::new_v4(), false)
            )
            .await
            .unwrap()
        );
        tokio::time::timeout(Duration::from_secs(2), server)
            .await
            .unwrap()
            .unwrap();
    }
}
