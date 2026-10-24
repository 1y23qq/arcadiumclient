use std::path::{Path, PathBuf};
use std::time::Duration;

use anyhow::{Context, Result, ensure};
use reqwest::{Client, Url};
use semver::Version;
use serde::Deserialize;
use sha2::{Digest, Sha256};
use tokio::io::{AsyncReadExt, AsyncWriteExt};

pub mod bundle;
mod platform;
pub use platform::UpdatePlatform;

pub const RELEASES_API: &str =
    "https://api.github.com/repos/1y23qq/arcadium-public/releases?per_page=100";
const DOWNLOAD_BASE: &str = "https://github.com/1y23qq/arcadium-public/releases/download/";
const MAX_INSTALLER_BYTES: u64 = 1024 * 1024 * 1024;

#[derive(Debug, Deserialize)]
pub struct Release {
    pub tag_name: String,
    pub draft: bool,
    pub published_at: Option<String>,
    pub assets: Vec<Asset>,
}

#[derive(Debug, Deserialize, Clone)]
pub struct Asset {
    pub name: String,
    pub browser_download_url: String,
    pub size: u64,
    pub state: String,
    pub digest: Option<String>,
}

#[derive(Debug, Clone)]
pub struct Candidate {
    pub platform: UpdatePlatform,
    pub version: Version,
    pub installer: Asset,
    pub manifest: Asset,
}

#[derive(Deserialize)]
struct Manifest {
    product: String,
    #[serde(rename = "launcherVersion")]
    version: String,
    platform: String,
    #[serde(rename = "installerSha256")]
    sha256: String,
}

pub fn select_release(current: &Version, releases: Vec<Release>) -> Option<Candidate> {
    select_release_for_platform(current, releases, UpdatePlatform::WindowsX64)
}

pub fn select_release_for_platform(
    current: &Version,
    releases: Vec<Release>,
    platform: UpdatePlatform,
) -> Option<Candidate> {
    releases
        .into_iter()
        .filter_map(|release| {
            if release.draft || release.published_at.is_none() {
                return None;
            }
            let version = Version::parse(release.tag_name.strip_prefix('v')?).ok()?;
            if !version.cmp_precedence(current).is_gt() {
                return None;
            }
            let name = platform.installer_name(&version.to_string());
            let installer = release.assets.iter().find(|a| a.name == name)?.clone();
            let manifest = release
                .assets
                .iter()
                .find(|a| a.name == platform.manifest_name())?
                .clone();
            for asset in [&installer, &manifest] {
                if asset.state != "uploaded"
                    || asset.size == 0
                    || asset.browser_download_url
                        != format!("{DOWNLOAD_BASE}v{version}/{}", asset.name)
                {
                    return None;
                }
            }
            if installer.size > MAX_INSTALLER_BYTES || manifest.size > 65536 {
                return None;
            }
            Some(Candidate {
                platform,
                version,
                installer,
                manifest,
            })
        })
        .max_by(|a, b| a.version.cmp_precedence(&b.version))
}

pub struct UpdateClient {
    client: Client,
    platform: UpdatePlatform,
}

impl UpdateClient {
    pub fn new(current: &str) -> Result<Self> {
        Self::for_platform(
            current,
            UpdatePlatform::current().context("Unsupported update platform")?,
        )
    }

    pub fn for_platform(current: &str, platform: UpdatePlatform) -> Result<Self> {
        let redirect = reqwest::redirect::Policy::custom(|attempt| {
            let url = attempt.url();
            if attempt.previous().len() >= 5 || !trusted_url(url) {
                attempt.error("Untrusted update redirect")
            } else {
                attempt.follow()
            }
        });
        Ok(Self {
            platform,
            client: Client::builder()
                .user_agent(format!("ArcadiumClient/{current}"))
                .connect_timeout(Duration::from_secs(5))
                .timeout(Duration::from_secs(900))
                .redirect(redirect)
                .build()?,
        })
    }

    pub async fn check(&self, current: &str) -> Result<Option<Candidate>> {
        let releases = self
            .client
            .get(RELEASES_API)
            .header("Accept", "application/vnd.github+json")
            .send()
            .await?
            .error_for_status()?
            .json::<Vec<Release>>()
            .await?;
        Ok(select_release_for_platform(
            &Version::parse(current)?,
            releases,
            self.platform,
        ))
    }

    pub async fn download(
        &self,
        candidate: &Candidate,
        directory: &Path,
        mut progress: impl FnMut(u64, u64, bool),
    ) -> Result<PathBuf> {
        let mut response = self
            .client
            .get(&candidate.manifest.browser_download_url)
            .send()
            .await?
            .error_for_status()?;
        let mut manifest_bytes = Vec::new();
        while let Some(chunk) = response.chunk().await? {
            ensure!(
                manifest_bytes.len() + chunk.len() <= 65536,
                "Update manifest is too large"
            );
            manifest_bytes.extend_from_slice(&chunk);
        }
        ensure!(
            manifest_bytes.len() as u64 == candidate.manifest.size,
            "Update manifest download was incomplete"
        );
        if let Some(digest) = &candidate.manifest.digest {
            ensure!(
                digest == &format!("sha256:{}", digest_hex(Sha256::digest(&manifest_bytes))),
                "Update manifest checksum verification failed"
            );
        }
        let manifest: Manifest = serde_json::from_slice(&manifest_bytes)?;
        ensure!(
            manifest.product == "Arcadium Client" && manifest.platform == candidate.platform.id(),
            "Update belongs to another product or platform"
        );
        ensure!(
            manifest.version == candidate.version.to_string(),
            "Update version mismatch"
        );
        ensure!(valid_digest(&manifest.sha256), "Invalid installer checksum");
        if let Some(digest) = &candidate.installer.digest {
            ensure!(
                digest == &format!("sha256:{}", manifest.sha256),
                "Release and manifest checksums disagree"
            );
        }
        tokio::fs::create_dir_all(directory).await?;
        let path = directory.join(&candidate.installer.name);
        let result = async {
            let mut response = self
                .client
                .get(&candidate.installer.browser_download_url)
                .send()
                .await?
                .error_for_status()?;
            let mut file = tokio::fs::File::create(&path).await?;
            let mut hash = Sha256::new();
            let mut downloaded = 0u64;
            let mut last = 0u64;
            progress(0, candidate.installer.size, false);
            while let Some(chunk) = response.chunk().await? {
                downloaded += chunk.len() as u64;
                ensure!(
                    downloaded <= candidate.installer.size,
                    "Installer exceeds its declared size"
                );
                file.write_all(&chunk).await?;
                hash.update(&chunk);
                if downloaded - last >= 256 * 1024 || downloaded == candidate.installer.size {
                    progress(downloaded, candidate.installer.size, false);
                    last = downloaded;
                }
            }
            file.flush().await?;
            file.sync_all().await?;
            drop(file);
            progress(downloaded, candidate.installer.size, true);
            ensure!(
                downloaded == candidate.installer.size,
                "Installer download was incomplete"
            );
            let actual = digest_hex(hash.finalize());
            ensure!(
                actual == manifest.sha256,
                "Installer checksum verification failed"
            );
            let mut magic = [0; 2];
            tokio::fs::File::open(&path)
                .await?
                .read_exact(&mut magic)
                .await?;
            ensure!(
                magic == candidate.platform.magic(),
                "Update has an invalid file format"
            );
            Ok::<_, anyhow::Error>(path.clone())
        }
        .await;
        if result.is_err() {
            let _ = tokio::fs::remove_file(&path).await;
        }
        result.context("Could not download a verified Arcadium update")
    }
}

fn valid_digest(value: &str) -> bool {
    value.len() == 64
        && value
            .bytes()
            .all(|c| c.is_ascii_digit() || (b'a'..=b'f').contains(&c))
}

fn digest_hex(bytes: impl AsRef<[u8]>) -> String {
    bytes
        .as_ref()
        .iter()
        .map(|byte| format!("{byte:02x}"))
        .collect()
}

fn trusted_url(url: &Url) -> bool {
    url.scheme() == "https"
        && url.port_or_known_default() == Some(443)
        && url.username().is_empty()
        && url.password().is_none()
        && matches!(
            url.host_str(),
            Some(
                "github.com"
                    | "api.github.com"
                    | "release-assets.githubusercontent.com"
                    | "objects.githubusercontent.com"
                    | "github-releases.githubusercontent.com"
            )
        )
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::{Read, Write};
    use std::net::TcpListener;
    fn release(version: &str) -> Release {
        let names = [
            format!("ArcadiumClientSetup-{version}-x64.exe"),
            "package-manifest.json".into(),
        ];
        Release {
            tag_name: format!("v{version}"),
            draft: false,
            published_at: Some("2026-10-12".into()),
            assets: names
                .into_iter()
                .map(|name| Asset {
                    browser_download_url: format!("{DOWNLOAD_BASE}v{version}/{name}"),
                    name,
                    size: 100,
                    state: "uploaded".into(),
                    digest: None,
                })
                .collect(),
        }
    }

    fn multiplatform_release(version: &str) -> Release {
        let mut release = release(version);
        for platform in [UpdatePlatform::MacArm64, UpdatePlatform::MacX64] {
            for name in [
                platform.installer_name(version),
                platform.manifest_name().to_string(),
            ] {
                release.assets.push(Asset {
                    browser_download_url: format!("{DOWNLOAD_BASE}v{version}/{name}"),
                    name,
                    size: 100,
                    state: "uploaded".into(),
                    digest: None,
                });
            }
        }
        release
    }

    #[test]
    fn mac_updates_select_only_the_matching_cpu_and_manifest() {
        for platform in [UpdatePlatform::MacArm64, UpdatePlatform::MacX64] {
            let candidate = select_release_for_platform(
                &Version::parse("0.2.5").unwrap(),
                vec![multiplatform_release("0.2.6")],
                platform,
            )
            .unwrap();
            assert_eq!(candidate.installer.name, platform.installer_name("0.2.6"));
            assert_eq!(candidate.manifest.name, platform.manifest_name());
            assert!(
                select_release_for_platform(
                    &Version::parse("0.2.5").unwrap(),
                    vec![release("0.2.6")],
                    platform
                )
                .is_none()
            );
        }
        let candidate = select_release(
            &Version::parse("0.2.5").unwrap(),
            vec![multiplatform_release("0.2.6")],
        )
        .unwrap();
        assert_eq!(
            candidate.installer.name,
            "ArcadiumClientSetup-0.2.6-x64.exe"
        );
        assert_eq!(candidate.manifest.name, "package-manifest.json");
    }
    #[test]
    fn selects_the_highest_published_version_and_never_downgrades() {
        let current = Version::parse("0.2.4").unwrap();
        assert_eq!(
            select_release(
                &current,
                vec![release("0.2.5"), release("0.2.3"), release("0.3.0")]
            )
            .unwrap()
            .version
            .to_string(),
            "0.3.0"
        );
        assert!(select_release(&current, vec![release("0.2.4"), release("0.2.3")]).is_none());
    }
    #[test]
    fn ignores_drafts_incomplete_assets_and_foreign_downloads() {
        let current = Version::parse("0.2.3").unwrap();
        let mut draft = release("0.2.4");
        draft.draft = true;
        let mut foreign = release("0.2.5");
        foreign.assets[0].browser_download_url = "https://github.com/other/project/file.exe".into();
        let mut incomplete = release("0.2.6");
        incomplete.assets[0].state = "new".into();
        assert!(select_release(&current, vec![draft, foreign, incomplete]).is_none());
    }
    #[test]
    fn official_updates_use_the_new_owner_for_every_platform() {
        assert_eq!(
            RELEASES_API,
            "https://api.github.com/repos/1y23qq/arcadium-public/releases?per_page=100"
        );
        let current = Version::parse("0.4.4").unwrap();
        for platform in [
            UpdatePlatform::WindowsX64,
            UpdatePlatform::MacArm64,
            UpdatePlatform::MacX64,
        ] {
            let candidate = select_release_for_platform(
                &current,
                vec![multiplatform_release("0.4.5")],
                platform,
            )
            .unwrap();
            assert!(
                candidate
                    .installer
                    .browser_download_url
                    .starts_with(DOWNLOAD_BASE)
            );
            let mut retired = multiplatform_release("0.4.5");
            for asset in &mut retired.assets {
                asset.browser_download_url = asset.browser_download_url.replace(
                    "github.com/1y23qq/arcadium-public/",
                    "github.com/0aurorq3/arcadium-public/",
                );
            }
            assert!(select_release_for_platform(&current, vec![retired], platform).is_none());
        }
    }
    #[test]
    fn redirects_require_https_and_an_official_download_host() {
        assert!(trusted_url(
            &Url::parse("https://release-assets.githubusercontent.com/test").unwrap()
        ));
        assert!(!trusted_url(&Url::parse("http://github.com/test").unwrap()));
        assert!(!trusted_url(
            &Url::parse("https://github.com.example.com/test").unwrap()
        ));
        assert!(!trusted_url(
            &Url::parse("https://example.com/file.exe").unwrap()
        ));
    }

    async fn download_fixture(corrupt: bool) -> (Result<PathBuf>, PathBuf, Vec<(u64, u64, bool)>) {
        let bytes = b"MZArcadium verified fixture";
        let expected = if corrupt {
            "0".repeat(64)
        } else {
            digest_hex(Sha256::digest(bytes))
        };
        let manifest = serde_json::to_vec(&serde_json::json!({"product":"Arcadium Client","launcherVersion":"0.2.4","platform":"windows-x64","installerSha256":expected})).unwrap();
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        listener.set_nonblocking(true).unwrap();
        let address = listener.local_addr().unwrap();
        let manifest_size = manifest.len() as u64;
        let server = std::thread::spawn(move || {
            let deadline = std::time::Instant::now() + Duration::from_secs(5);
            let mut served = 0;
            while served < 2 && std::time::Instant::now() < deadline {
                let Ok((mut socket, _)) = listener.accept() else {
                    std::thread::sleep(Duration::from_millis(5));
                    continue;
                };
                socket.set_nonblocking(false).unwrap();
                socket
                    .set_read_timeout(Some(Duration::from_secs(2)))
                    .unwrap();
                let mut request = [0; 4096];
                let length = socket.read(&mut request).unwrap();
                let body: &[u8] =
                    if String::from_utf8_lossy(&request[..length]).starts_with("GET /manifest ") {
                        &manifest
                    } else {
                        bytes
                    };
                write!(
                    socket,
                    "HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n",
                    body.len()
                )
                .unwrap();
                socket.write_all(body).unwrap();
                served += 1;
            }
        });
        let mut candidate =
            select_release(&Version::parse("0.2.3").unwrap(), vec![release("0.2.4")]).unwrap();
        candidate.installer.browser_download_url = format!("http://{address}/installer");
        candidate.installer.size = bytes.len() as u64;
        candidate.manifest.browser_download_url = format!("http://{address}/manifest");
        candidate.manifest.size = manifest_size;
        let nonce = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        static NEXT: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);
        let sequence = NEXT.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
        let directory = std::env::temp_dir().join(format!(
            "ArcadiumUpdateTest-{}-{nonce}-{sequence}",
            std::process::id()
        ));
        let client = UpdateClient {
            platform: UpdatePlatform::WindowsX64,
            client: Client::builder().no_proxy().build().unwrap(),
        };
        let mut progress = Vec::new();
        let result = client
            .download(&candidate, &directory, |current, total, verify| {
                progress.push((current, total, verify))
            })
            .await;
        server.join().unwrap();
        (result, directory, progress)
    }

    #[tokio::test]
    async fn a_verified_download_is_saved_and_reports_real_progress() {
        let (result, directory, progress) = download_fixture(false).await;
        let path = result.unwrap();
        assert!(path.is_file());
        assert_eq!(progress.first().unwrap().0, 0);
        let last = progress.last().unwrap();
        assert_eq!(last.0, last.1);
        assert!(last.2);
        std::fs::remove_file(path).unwrap();
        std::fs::remove_dir(directory).unwrap();
    }

    #[tokio::test]
    async fn a_corrupt_download_is_rejected_and_removed() {
        let (result, directory, _) = download_fixture(true).await;
        assert!(result.is_err());
        assert_eq!(std::fs::read_dir(&directory).unwrap().count(), 0);
        std::fs::remove_dir(directory).unwrap();
    }
}
