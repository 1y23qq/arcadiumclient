use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::str::FromStr;

use arcadium_db::dao::bundle as bundle_dao;
use arcadium_db::dao::package_metadata as meta_dao;
use arcadium_db::models::{BundleRow, NewBundle};
use chrono::Utc;
use tokio::sync::RwLock;

use crate::bundles::error::BundleError;
use crate::bundles::manifest::{BundleManifest as RemoteBundleManifest, RemoteBundleRef};
use crate::bundles::polymrpack;
use crate::bundles::types::{BundleArchive, BundleFileKind};
use crate::ctx::ContentCtx;
use crate::error::{ContentError, ContentResult};
use arcadium_common::domain::{GameLoader, ProviderId};
use arcadium_common::paths;

#[derive(Debug, Clone)]
pub struct Bundle {
    pub remote_path: String,
    pub mc_version: String,
    pub loader: GameLoader,
    pub file_name: String,
    pub name: String,
    pub version_id: String,
    pub category: String,
    pub loader_version: String,
    pub path: PathBuf,
    pub hidden: bool,
}

impl Bundle {
    fn try_from_row(row: BundleRow) -> ContentResult<Self> {
        let loader = GameLoader::from_repr(row.mc_loader as u8)
            .ok_or(BundleError::InvalidLoader(row.mc_loader))?;

        let launcher_dir = paths::data_dir()?;
        let path = launcher_dir.join(&row.disk_path);

        Ok(Self {
            remote_path: row.remote_path,
            mc_version: row.mc_version,
            loader,
            file_name: row.file_name,
            name: row.name.unwrap_or_default(),
            version_id: row.version_id.unwrap_or_default(),
            category: row.category.unwrap_or_default(),
            loader_version: row.loader_version.unwrap_or_default(),
            path,
            hidden: row.hidden != 0,
        })
    }
}

pub struct BundlesManager {
    manifest: RwLock<RemoteBundleManifest>,
    pub(crate) archive_cache: RwLock<HashMap<PathBuf, crate::bundles::types::BundleManifest>>,
}

impl BundlesManager {
    pub fn new() -> Self {
        Self {
            manifest: RwLock::new(RemoteBundleManifest::default()),
            archive_cache: RwLock::new(HashMap::new()),
        }
    }

    #[tracing::instrument(skip_all)]
    pub async fn sync(&self, ctx: &ContentCtx) -> ContentResult<bool> {
        let Some(fetched) = Self::fetch_manifest(ctx).await? else {
            tracing::debug!(
                "skipping bundle sync because no remote or cached manifest is available"
            );
            return Ok(false);
        };
        let FetchedManifest {
            manifest,
            changed: manifest_changed,
            etag,
        } = fetched;
        *self.manifest.write().await = manifest.clone();
        self.archive_cache.write().await.clear();

        if !manifest_changed {
            tracing::debug!("bundles manifest unchanged; skipping per-bundle remote checks");
            return Ok(false);
        }

        let remote_paths: Vec<String> = manifest
            .remote_paths()
            .into_iter()
            .map(|entry| entry.remote_path)
            .collect();

        if remote_paths.is_empty() {
            tracing::error!("bundle catalog listed no bundles; keeping the previous catalog");
            return Ok(false);
        }

        bundle_dao::hide_bundles_not_in(&ctx.db, &remote_paths).await?;

        let bundles_root = paths::bundles_dir()?;
        arcadium_io::create_dir_all(&bundles_root.join("bundles")).await?;

        let mut synced_all = true;
        for entry in manifest.remote_paths() {
            if let Err(err) = self.sync_bundle(&entry, ctx, &bundles_root).await {
                synced_all = false;
                tracing::warn!(
                    remote_path = %entry.remote_path,
                    error = %err,
                    "failed to sync bundle"
                );
            }
        }

        if synced_all {
            if let Some(etag) = etag {
                let manifest_path = paths::bundles_dir()?.join("metadata.json");
                arcadium_net::commit_etag(&manifest_path, &etag).await;
            }
        } else {
            tracing::warn!(
                "bundle catalog incomplete; not caching the manifest etag so the next \
                 sync retries the missing bundles"
            );
        }

        Ok(true)
    }

    pub async fn cache_archive_manifest(
        &self,
        path: std::path::PathBuf,
        manifest: crate::bundles::types::BundleManifest,
    ) {
        self.archive_cache.write().await.insert(path, manifest);
    }

    #[tracing::instrument(level = "debug", skip(self, ctx))]
    pub async fn archives_for(
        &self,
        ctx: &ContentCtx,
        mc_version: &str,
        loader: GameLoader,
    ) -> ContentResult<Vec<BundleArchive>> {
        let mut archives = Vec::new();
        for bundle in self.list_for(ctx, mc_version, loader).await? {
            let manifest = self.manifest_for_archive(ctx, &bundle.path).await?;
            archives.push(BundleArchive { bundle, manifest });
        }
        Ok(archives)
    }

    #[tracing::instrument(level = "debug", skip(self, ctx))]
    async fn manifest_for_archive(
        &self,
        ctx: &ContentCtx,
        path: &Path,
    ) -> ContentResult<crate::bundles::types::BundleManifest> {
        if let Some(manifest) = self.archive_cache.read().await.get(path) {
            return Ok(manifest.clone());
        }
        let manifest = polymrpack::read_manifest_from_archive(path).await?;
        record_external_metadata(&manifest, ctx).await;
        self.archive_cache
            .write()
            .await
            .insert(path.to_path_buf(), manifest.clone());
        Ok(manifest)
    }

    #[tracing::instrument(level = "debug", skip(self, ctx))]
    pub async fn list_for(
        &self,
        ctx: &ContentCtx,
        mc_version: &str,
        loader: GameLoader,
    ) -> ContentResult<Vec<Bundle>> {
        if mc_version == arcadium_common::distribution::MINECRAFT_VERSION {
            if loader != GameLoader::Ornithe {
                return Ok(Vec::new());
            }
            return self.list_pinned(ctx, &paths::bundles_dir()?).await;
        }
        let rows =
            bundle_dao::list_visible_for_version_loader(&ctx.db, mc_version, loader as i64).await?;

        rows.into_iter()
            .map(Bundle::try_from_row)
            .collect::<Result<Vec<_>, _>>()
    }

    async fn list_pinned(
        &self,
        ctx: &ContentCtx,
        bundles_root: &Path,
    ) -> ContentResult<Vec<Bundle>> {
        let manifest: RemoteBundleManifest =
            serde_json::from_slice(arcadium_common::distribution::BUNDLES)?;
        arcadium_io::create_dir_all(&bundles_root.join("bundles")).await?;
        let mut bundles = Vec::new();
        for entry in manifest.remote_paths().into_iter().filter(|entry| {
            entry.mc_version == arcadium_common::distribution::MINECRAFT_VERSION
                && entry.loader == "ornithe"
        }) {
            self.sync_bundle(&entry, ctx, bundles_root).await?;
            let row = bundle_dao::get_by_remote_path(&ctx.db, &entry.remote_path)
                .await?
                .ok_or_else(|| ContentError::InvalidData {
                    reason: "Arcadium's pinned bundle catalog could not be restored.".into(),
                })?;
            bundles.push(Bundle::try_from_row(row)?);
        }
        bundles.sort_by(|left, right| {
            (&left.name, &left.file_name).cmp(&(&right.name, &right.file_name))
        });
        Ok(bundles)
    }

    #[tracing::instrument(level = "debug", skip(self, entry, ctx, bundles_root), fields(remote_path = %entry.remote_path))]
    async fn sync_bundle(
        &self,
        entry: &RemoteBundleRef,
        ctx: &ContentCtx,
        bundles_root: &Path,
    ) -> ContentResult<()> {
        let loader = GameLoader::from_str(&entry.loader)
            .map_err(|_| BundleError::UnknownLoader(entry.loader.clone()))?;

        let file_name = entry
            .remote_path
            .split('/')
            .next_back()
            .ok_or_else(|| BundleError::InvalidPath(entry.remote_path.clone()))?
            .to_string();

        let disk_path = bundles_root.join("bundles").join(&file_name);

        if let Some(bytes) = arcadium_common::distribution::bundle(&entry.remote_path) {
            if arcadium_io::sha1_bytes(bytes) != entry.sha1 {
                return Err(ContentError::InvalidData {
                    reason: "Arcadium's pinned bundle metadata is damaged. Reinstall the launcher."
                        .into(),
                });
            }
            if !arcadium_net::matches_on_disk(&disk_path, &entry.sha1).await {
                arcadium_io::write_atomic(&disk_path, bytes).await?;
                self.archive_cache.write().await.remove(&disk_path);
            }
        } else {
            return Err(ContentError::InvalidData {
                reason: "Bundle is not part of Arcadium 1.8.9".into(),
            });
        }

        let meta = polymrpack::read_meta_from_archive(&disk_path).await?;

        let relative_disk_path = disk_path
            .strip_prefix(paths::data_dir()?)
            .map(|p| p.to_string_lossy().into_owned())
            .unwrap_or_else(|_| disk_path.to_string_lossy().into_owned());

        let synced_at = Utc::now().to_rfc3339();

        bundle_dao::upsert_bundle(
            &ctx.db,
            NewBundle {
                remote_path: &entry.remote_path,
                mc_version: &entry.mc_version,
                mc_loader: loader as i64,
                file_name: &file_name,
                name: Some(&meta.name),
                version_id: Some(&meta.version_id),
                category: Some(&meta.category),
                loader_version: Some(&meta.loader_version),
                disk_path: &relative_disk_path,
                hidden: false,
                etag: Some(&entry.sha1),
                synced_at: Some(&synced_at),
            },
        )
        .await?;

        Ok(())
    }

    /// Defers the ETag commit
    /// bundles download after the catalog so an early ETag would 304 past
    /// bundles that never arrived
    /// `sync` commits it once all land
    #[tracing::instrument(level = "debug", skip(_ctx))]
    async fn fetch_manifest(_ctx: &ContentCtx) -> ContentResult<Option<FetchedManifest>> {
        Ok(Some(FetchedManifest {
            manifest: serde_json::from_slice(arcadium_common::distribution::BUNDLES)?,
            changed: true,
            etag: None,
        }))
    }
}

impl Default for BundlesManager {
    fn default() -> Self {
        Self::new()
    }
}

#[tracing::instrument(level = "debug", skip_all, fields(bundle = %manifest.name))]
async fn record_external_metadata(
    manifest: &crate::bundles::types::BundleManifest,
    ctx: &ContentCtx,
) {
    for bundle_file in &manifest.files {
        let BundleFileKind::External {
            file,
            meta: Some(meta),
            ..
        } = &bundle_file.kind
        else {
            continue;
        };

        if let Err(err) = meta_dao::upsert_package_metadata(
            &ctx.db,
            ProviderId::Local as i64,
            &file.sha1,
            meta.name.as_deref().unwrap_or(&file.name),
            meta.description.as_deref().unwrap_or_default(),
            &meta.authors.join(", "),
            meta.icon_url.as_deref(),
        )
        .await
        {
            tracing::warn!(file = %file.name, error = %err, "failed to record bundle file metadata");
        }
    }
}

struct FetchedManifest {
    manifest: RemoteBundleManifest,
    changed: bool,
    etag: Option<String>,
}

#[cfg(test)]
mod tests {
    use super::*;
    use arcadium_io::testing::ScratchDir;

    async fn context(config: arcadium_net::NetConfig) -> ContentCtx {
        let db = sqlx::sqlite::SqlitePoolOptions::new()
            .max_connections(1)
            .connect("sqlite::memory:")
            .await
            .unwrap();
        sqlx::migrate!("../arcadium_db/migrations")
            .run(&db)
            .await
            .unwrap();
        ContentCtx::new(
            db,
            arcadium_net::RequestClient::new(config).unwrap(),
            arcadium_events::EventBus::channel().0,
            crate::packages::provider::PackageProviderRegistry::new(),
        )
    }

    async fn seed_old_catalog(ctx: &ContentCtx, loader: GameLoader) {
        bundle_dao::upsert_bundle(
            &ctx.db,
            NewBundle {
                remote_path: "/oneclient/bundles/historical.mrpack",
                mc_version: "1.8.9",
                mc_loader: loader as i64,
                file_name: "historical.mrpack",
                name: Some("Historical upstream bundle"),
                version_id: Some("old"),
                category: Some("utility"),
                loader_version: Some("0.16.0"),
                disk_path: "historical.mrpack",
                hidden: false,
                etag: None,
                synced_at: None,
            },
        )
        .await
        .unwrap();
    }

    #[tokio::test]
    async fn reachable_upstream_backend_cannot_replace_the_embedded_catalog() {
        let backend = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
        backend.set_nonblocking(true).unwrap();
        let url = format!("http://{}", backend.local_addr().unwrap());
        let ctx = context(arcadium_net::NetConfig::default().with_overrides(
            None,
            None,
            Some(&url),
            Some(&url),
        ))
        .await;
        let fetched = tokio::time::timeout(
            std::time::Duration::from_secs(1),
            BundlesManager::fetch_manifest(&ctx),
        )
        .await
        .unwrap()
        .unwrap()
        .unwrap();
        assert_eq!(
            fetched.manifest,
            serde_json::from_slice(arcadium_common::distribution::BUNDLES).unwrap()
        );
        assert!(
            matches!(backend.accept(), Err(error) if error.kind() == std::io::ErrorKind::WouldBlock)
        );
    }

    #[tokio::test]
    async fn pinned_access_repairs_old_archive_and_ignores_upstream_database_rows() {
        let temp = ScratchDir::new("pinned-bundle-access");
        let ctx = context(Default::default()).await;
        seed_old_catalog(&ctx, GameLoader::Ornithe).await;
        let manager = BundlesManager::new();
        let first = manager.list_pinned(&ctx, temp.path()).await.unwrap();
        assert_eq!(first.len(), 4);
        let path = first[0].path.clone();
        let mut stale = manager.manifest_for_archive(&ctx, &path).await.unwrap();
        stale.name = "Stale upstream cache".into();
        for name in ["polyplus", "chattweaks", "polysprint", "CrashPatch"] {
            stale.files.push(crate::bundles::types::BundleFile {
                enabled: true,
                hidden: false,
                path: format!("mods/{name}.jar"),
                size: 1,
                file_type: Default::default(),
                kind: BundleFileKind::Managed {
                    provider: ProviderId::Modrinth,
                    project_id: name.into(),
                    version_id: "historical".into(),
                    sha1: "0".repeat(40),
                },
            });
        }
        manager.cache_archive_manifest(path.clone(), stale).await;
        tokio::fs::write(&path, b"historical upstream archive")
            .await
            .unwrap();

        let bundles = manager.list_pinned(&ctx, temp.path()).await.unwrap();
        assert_eq!(bundles.len(), 4);
        let pinned: RemoteBundleManifest =
            serde_json::from_slice(arcadium_common::distribution::BUNDLES).unwrap();
        for bundle in bundles {
            let entry = pinned
                .remote_paths()
                .into_iter()
                .find(|entry| entry.remote_path == bundle.remote_path)
                .unwrap();
            assert!(arcadium_net::matches_on_disk(&bundle.path, &entry.sha1).await);
            assert_ne!(bundle.remote_path, "/oneclient/bundles/historical.mrpack");
            let manifest = manager
                .manifest_for_archive(&ctx, &bundle.path)
                .await
                .unwrap();
            assert_ne!(manifest.name, "Stale upstream cache");
            for file in manifest.files {
                let name = file.path.to_ascii_lowercase();
                assert!(
                    !["polyplus", "chattweaks", "polysprint", "crashpatch"]
                        .iter()
                        .any(|retired| name.contains(retired)),
                    "{}",
                    file.path
                );
            }
        }
    }

    #[tokio::test]
    async fn unreadable_pinned_cache_never_falls_back_to_historical_upstream_rows() {
        let temp = ScratchDir::new("pinned-bundle-failure");
        let ctx = context(Default::default()).await;
        seed_old_catalog(&ctx, GameLoader::Ornithe).await;
        tokio::fs::write(temp.join("bundles"), b"blocked cache directory")
            .await
            .unwrap();
        assert!(
            BundlesManager::new()
                .list_pinned(&ctx, temp.path())
                .await
                .is_err()
        );
    }

    #[tokio::test]
    async fn unsupported_legacy_loader_cannot_read_an_old_upstream_catalog() {
        let ctx = context(Default::default()).await;
        seed_old_catalog(&ctx, GameLoader::Fabric).await;
        assert!(
            BundlesManager::new()
                .archives_for(&ctx, "1.8.9", GameLoader::Fabric)
                .await
                .unwrap()
                .is_empty()
        );
    }
}
