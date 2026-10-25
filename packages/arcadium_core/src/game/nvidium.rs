use std::path::Path;

use arcadium_common::domain::{ContentType, GameLoader, ProviderId};
use arcadium_content::packages::store::{
    PackageStore, artifact_absolute_path, download_version_file,
};
use arcadium_content::packages::types::{VersionDetail, VersionFile};
use arcadium_events::{Choice, EventBus, Prompt};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha512};

use super::gpu::Gpu;
use crate::clusters::Cluster;
use crate::state::LauncherServices;
use crate::{LauncherError, LauncherResult};

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct Release {
    project: String,
    version: String,
    mod_version: String,
    file: String,
    bytes: u64,
    url: String,
    sha1: String,
    sha512: String,
    sodium_versions: Vec<String>,
}

fn release() -> &'static Release {
    static RELEASE: std::sync::OnceLock<Release> = std::sync::OnceLock::new();
    RELEASE.get_or_init(|| {
        let lock: serde_json::Value =
            serde_json::from_slice(arcadium_common::distribution::MODERN_262_LOCK)
                .expect("valid 26.2 distribution lock");
        serde_json::from_value(lock["optionalNvidium"].clone()).expect("pinned Nvidium release")
    })
}

#[derive(Serialize, Deserialize)]
struct Decision {
    minecraft: String,
    choice: String,
    adapters: Vec<String>,
    #[serde(default)]
    selected_gpu: Option<String>,
}

fn recommendation_needed(version: &str, decided: bool, installed: bool, adapters: &[Gpu]) -> bool {
    version == "26.2" && !decided && !installed && adapters.iter().any(super::gpu::supports_nvidium)
}

fn compatible_sodium_version(version: &str) -> bool {
    release()
        .sodium_versions
        .iter()
        .any(|pinned| version == pinned || version.starts_with(&format!("{pinned}+")))
}

fn active_mods(
    cluster: &Cluster,
    game_dir: &Path,
    mods_in_cluster: bool,
) -> LauncherResult<std::path::PathBuf> {
    Ok(if mods_in_cluster {
        cluster.dir()?
    } else {
        game_dir.to_path_buf()
    }
    .join("mods"))
}

async fn ask(events: &EventBus, adapters: &[Gpu]) -> LauncherResult<bool> {
    let names = adapters
        .iter()
        .filter(|gpu| super::gpu::supports_nvidium(gpu))
        .map(|gpu| gpu.name.as_str())
        .collect::<Vec<_>>()
        .join(", ");
    Ok(events.ask(
        Prompt::new("Nvidium recommended", format!(
            "Compatible NVIDIA GPU detected: {names}.\n\nWould you like to use Nvidium for Minecraft 26.2? Enabling it installs Nvidium and selects OpenGL, which Nvidium requires."
        ))
        .option(Choice::primary("enable", "Enable Nvidium"), true)
        .dismiss("Keep current renderer")
    ).await?.is_some_and(|answer| answer.value))
}

fn opengl_options(original: &str) -> String {
    let separator = if original.contains("\r\n") {
        "\r\n"
    } else {
        "\n"
    };
    let mut lines: Vec<_> = original
        .lines()
        .filter(|line| !line.starts_with("preferredGraphicsBackend:"))
        .collect();
    if original.trim().is_empty() {
        lines.push("version:4903");
    }
    lines.push("preferredGraphicsBackend:\"opengl\"");
    lines.join(separator) + separator
}

fn prefers_opengl(options: &str) -> bool {
    let backend = options
        .lines()
        .filter_map(|line| line.strip_prefix("preferredGraphicsBackend:"))
        .last();
    backend.is_none_or(|value| {
        matches!(
            serde_json::from_str::<String>(value).ok().as_deref(),
            Some("default" | "opengl")
        )
    })
}

async fn installed_mods(
    mods: &Path,
) -> LauncherResult<Vec<arcadium_content::packages::local_manifest::JarModId>> {
    let mut found = Vec::new();
    let mut entries = match tokio::fs::read_dir(mods).await {
        Ok(entries) => entries,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(found),
        Err(error) => return Err(error.into()),
    };
    while let Some(entry) = entries.next_entry().await? {
        let path = entry.path();
        let name = entry.file_name().to_string_lossy().to_ascii_lowercase();
        if (name.ends_with(".jar") || name.ends_with(".jar.disabled"))
            && let Some(identity) =
                arcadium_content::packages::local_manifest::read_jar_mod_id(&path).await
        {
            if identity.id == "nvidium" || (identity.id == "sodium" && name.ends_with(".jar")) {
                found.push(identity);
            }
        }
    }
    Ok(found)
}

pub async fn prepare(
    services: &LauncherServices,
    cluster: &Cluster,
    user_game_dir: &Path,
    game_dir: &Path,
    mods_in_cluster: bool,
) -> LauncherResult<Option<String>> {
    let version = cluster.mc_version.as_str();
    let cluster_id = cluster.id;
    if version != "26.2" || !mods_in_cluster {
        return Ok(None);
    }
    let mods = active_mods(cluster, game_dir, mods_in_cluster)?;
    let state_path =
        arcadium_common::paths::data_dir()?.join("metadata/recommendations/nvidium-26.2.json");
    if let Ok(raw) = tokio::fs::read(&state_path).await
        && let Ok(state) = serde_json::from_slice::<Decision>(&raw)
        && state.minecraft == "26.2"
    {
        if state.choice == "enabled"
            && arcadium_net::matches_on_disk(&mods.join(&release().file), &release().sha1).await
            && tokio::fs::read_to_string(user_game_dir.join("options.txt"))
                .await
                .is_ok_and(|options| prefers_opengl(&options))
        {
            return Ok(state.selected_gpu);
        }
        return Ok(None);
    }
    let adapters = match super::gpu::inventory().await {
        Ok(adapters) => adapters,
        Err(error) => {
            tracing::warn!(%error, "Nvidium recommendation deferred; GPU inventory unavailable");
            return Ok(None);
        }
    };
    let ctx = services.content();
    let linked = PackageStore::list_linked_artifacts(cluster_id, &ctx).await?;
    let present = installed_mods(&mods).await?;
    let installed = present.iter().any(|identity| identity.id == "nvidium")
        || linked.iter().any(|artifact| {
            artifact.project_id.as_deref() == Some(release().project.as_str())
                || artifact
                    .file_name
                    .to_ascii_lowercase()
                    .starts_with("nvidium")
        });
    let mut choice = if installed {
        "already-installed"
    } else {
        "unsupported"
    };
    let mut selected_gpu = None;
    if recommendation_needed(version, false, installed, &adapters) {
        let compatible_sodium = present.iter().any(|identity| {
            identity.id == "sodium"
                && identity
                    .version
                    .as_ref()
                    .is_some_and(|value| compatible_sodium_version(value))
        });
        if !compatible_sodium {
            tracing::info!("Nvidium recommendation deferred; compatible Sodium is not active");
            return Ok(None);
        }
        if ask(&services.events, &adapters).await? {
            install(services, cluster, user_game_dir, game_dir, mods_in_cluster).await?;
            choice = "enabled";
            selected_gpu = adapters
                .iter()
                .find(|gpu| super::gpu::supports_nvidium(gpu))
                .map(|gpu| {
                    if cfg!(windows) {
                        "dedicated".to_owned()
                    } else {
                        gpu.id.clone()
                    }
                });
        } else {
            choice = "declined";
        }
    }
    let decision = Decision {
        minecraft: version.into(),
        choice: choice.into(),
        adapters: adapters.into_iter().map(|gpu| gpu.name).collect(),
        selected_gpu: selected_gpu.clone(),
    };
    arcadium_io::write_atomic(&state_path, serde_json::to_vec_pretty(&decision)?).await?;
    Ok(selected_gpu)
}

async fn install(
    services: &LauncherServices,
    cluster: &Cluster,
    user_game_dir: &Path,
    game_dir: &Path,
    mods_in_cluster: bool,
) -> LauncherResult<()> {
    let pinned = release();
    let ctx = services.content();
    let version = VersionDetail {
        version_id: pinned.version.clone(),
        project_id: pinned.project.clone(),
        name: format!("Nvidium {}", pinned.mod_version),
        version_number: pinned.mod_version.clone(),
        changelog: None,
        game_versions: vec!["26.2".into()],
        loaders: vec![GameLoader::Fabric],
        published: "2026-09-22T15:55:45.489858Z"
            .parse()
            .expect("pinned publication date"),
        downloads: 0,
        files: Vec::new(),
        dependencies: Vec::new(),
    };
    let file = VersionFile {
        sha1: pinned.sha1.clone(),
        url: pinned.url.clone(),
        file_name: pinned.file.clone(),
        primary: true,
        size: pinned.bytes,
        fingerprint: None,
    };
    let artifact = download_version_file(
        ProviderId::Modrinth,
        &pinned.project,
        &version,
        ContentType::Mod,
        &file,
        false,
        None,
        &ctx,
    )
    .await?;
    let raw = arcadium_io::read(artifact_absolute_path(&artifact.path)?).await?;
    let sha512 = Sha512::digest(&raw)
        .iter()
        .map(|byte| format!("{byte:02x}"))
        .collect::<String>();
    if raw.len() as u64 != pinned.bytes || sha512 != pinned.sha512 {
        return Err(LauncherError::Minecraft(
            "The Nvidium download did not match the pinned release.".into(),
        ));
    }
    activate_cached_mod(
        services,
        cluster,
        &artifact,
        user_game_dir,
        game_dir,
        mods_in_cluster,
    )
    .await?;
    services
        .events
        .signal(arcadium_events::Signal::ClustersChanged);
    Ok(())
}

async fn activate_cached_mod(
    services: &LauncherServices,
    cluster: &Cluster,
    artifact: &arcadium_db::models::ArtifactRow,
    user_game_dir: &Path,
    game_dir: &Path,
    mods_in_cluster: bool,
) -> LauncherResult<()> {
    use arcadium_db::dao::artifact as artifact_dao;

    let ctx = services.content();
    let previous =
        artifact_dao::get_cluster_artifact(&services.db, cluster.id, &artifact.hash).await?;
    if previous.as_ref().is_some_and(|link| link.enabled == 0) {
        return Err(LauncherError::Minecraft(
            "Nvidium was disabled. Keep the current renderer or enable the mod first.".into(),
        ));
    }
    PackageStore::link_by_hash(&artifact.hash, cluster.id, None, &ctx).await?;
    let result = async {
        super::materialize_content(services, cluster, game_dir, mods_in_cluster).await?;
        let active = artifact_dao::get_cluster_artifact(&services.db, cluster.id, &artifact.hash)
            .await?
            .is_some_and(|link| link.enabled != 0);
        let path = active_mods(cluster, game_dir, mods_in_cluster)?.join(&artifact.file_name);
        if !active || !arcadium_net::matches_on_disk(&path, &artifact.hash).await {
            return Err(LauncherError::Minecraft("Nvidium could not be placed in the active mods folder. The renderer was kept unchanged.".into()));
        }
        let path = user_game_dir.join("options.txt");
        let original = match tokio::fs::read_to_string(&path).await {
            Ok(original) => original,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => String::new(),
            Err(error) => return Err(error.into()),
        };
        arcadium_io::write_atomic(&path, opengl_options(&original)).await?;
        Ok(())
    }.await;
    if result.is_err() && previous.is_none() {
        artifact_dao::unlink_cluster_artifact(&services.db, cluster.id, &artifact.hash).await?;
        let path = active_mods(cluster, game_dir, mods_in_cluster)?.join(&artifact.file_name);
        if arcadium_net::matches_on_disk(&path, &artifact.hash).await {
            arcadium_content::packages::store::remove_entry(&path).await?;
        }
        if let Err(error) =
            super::materialize_content(services, cluster, game_dir, mods_in_cluster).await
        {
            tracing::warn!(%error, "Could not reconcile a failed Nvidium activation");
        }
    }
    result
}

#[cfg(test)]
mod tests {
    use super::*;
    use arcadium_events::{Answer, Event, Notification};

    fn hybrid() -> Vec<Gpu> {
        vec![
            Gpu {
                id: "integrated".into(),
                name: "Intel UHD Graphics".into(),
            },
            Gpu {
                id: "nvidia".into(),
                name: "NVIDIA GeForce RTX 4060 Laptop GPU".into(),
            },
        ]
    }

    #[test]
    fn first_262_launch_checks_all_adapters_without_overriding_prior_choices() {
        let adapters = hybrid();
        assert!(recommendation_needed("26.2", false, false, &adapters));
        assert!(!recommendation_needed("1.21.11", false, false, &adapters));
        assert!(!recommendation_needed("26.2", true, false, &adapters));
        assert!(!recommendation_needed("26.2", false, true, &adapters));
        assert!(!recommendation_needed("26.2", false, false, &adapters[..1]));
    }

    #[tokio::test]
    async fn prompt_installs_only_after_explicit_enable() {
        for response in [Some("enable"), None] {
            let (events, mut receiver) = EventBus::channel();
            let task = tokio::spawn(async move { ask(&events, &hybrid()).await });
            let Some(Event::Notification(Notification::Prompt(prompt))) = receiver.recv().await
            else {
                panic!("expected recommendation")
            };
            assert!(prompt.body.contains("NVIDIA GeForce RTX 4060 Laptop GPU"));
            assert!(prompt.body.contains("OpenGL"));
            assert_eq!(prompt.choices.len(), 1);
            assert_eq!(prompt.dismiss.as_deref(), Some("Keep current renderer"));
            prompt.reply.send(response.map(Answer::new)).unwrap();
            assert_eq!(task.await.unwrap().unwrap(), response == Some("enable"));
        }
    }

    #[test]
    fn opengl_selection_preserves_other_options_and_removes_duplicate_backends() {
        let before = "version:4900\r\nlang:ja_jp\r\npreferredGraphicsBackend:\"vulkan\"\r\nrenderDistance:8\r\npreferredGraphicsBackend:\"default\"\r\n";
        let after = opengl_options(before);
        assert_eq!(
            after,
            "version:4900\r\nlang:ja_jp\r\nrenderDistance:8\r\npreferredGraphicsBackend:\"opengl\"\r\n"
        );
        assert_eq!(opengl_options(&after), after);
        assert_eq!(
            opengl_options(""),
            "version:4903\npreferredGraphicsBackend:\"opengl\"\n"
        );
    }

    #[test]
    fn cached_nvidium_keeps_native_gl_default_but_respects_a_later_vulkan_choice() {
        for options in [
            "version:4903\n",
            "preferredGraphicsBackend:\"default\"\n",
            "preferredGraphicsBackend:\"opengl\"\n",
        ] {
            assert!(prefers_opengl(options));
        }
        assert!(!prefers_opengl(
            "preferredGraphicsBackend:\"opengl\"\npreferredGraphicsBackend:\"vulkan\"\n"
        ));
        assert!(prefers_opengl(
            "preferredGraphicsBackend:\"vulkan\"\npreferredGraphicsBackend:\"default\"\n"
        ));
        assert!(!prefers_opengl("preferredGraphicsBackend:invalid\n"));
    }

    #[test]
    fn nvidium_is_pinned_to_a_matching_sodium_and_never_in_default_bundles() {
        let pinned = release();
        assert_eq!(pinned.sha512.len(), 128);
        assert_eq!(pinned.sha1.len(), 40);
        assert_eq!(pinned.file, "nvidium-0.4.4-beta6-26.2.jar");
        assert!(pinned.sodium_versions.contains(&"0.9.2".to_owned()));
        let lock: serde_json::Value =
            serde_json::from_slice(arcadium_common::distribution::MODERN_262_LOCK).unwrap();
        for bundle in lock["bundles"].as_array().unwrap() {
            assert!(!bundle["mods"].as_array().unwrap().iter().any(|file| {
                file["path"]
                    .as_str()
                    .unwrap()
                    .to_ascii_lowercase()
                    .contains("nvidium")
            }));
        }
        assert_eq!(lock["optionalNvidium"]["enabledByDefault"], false);
    }

    #[test]
    fn sodium_build_metadata_is_allowed_without_accepting_other_releases() {
        assert!(compatible_sodium_version("0.9.2+mc26.2"));
        assert!(compatible_sodium_version("0.9.2"));
        assert!(compatible_sodium_version("0.9.1+mc26.2"));
        assert!(!compatible_sodium_version("0.9.20+mc26.2"));
        assert!(!compatible_sodium_version("0.9.3+mc26.2"));
        assert!(!compatible_sodium_version("0.9.2-beta.1"));
    }

    #[tokio::test]
    async fn opt_in_materializes_before_first_spawn_and_respects_disabled_links() {
        use crate::clusters::CreateClusterOptions;
        use arcadium_db::dao::artifact as artifact_dao;

        let scratch = arcadium_io::testing::ScratchDir::new("nvidium_first_launch");
        arcadium_common::paths::set_launcher_dir(scratch.path().to_path_buf());
        let (events, _receiver) = EventBus::channel();
        let db = arcadium_db::connect(scratch.path().join("fixture.db"))
            .await
            .unwrap();
        let services = LauncherServices {
            events,
            requester: arcadium_net::RequestClient::new(arcadium_net::NetConfig::default())
                .unwrap(),
            db,
            packages: arcadium_content::packages::provider::PackageProviderRegistry::new(),
        };
        let clusters = crate::clusters::ClusterManager::new(services.db.clone());
        let cache = arcadium_common::paths::data_dir()
            .unwrap()
            .join("artifacts/nvidium-fixture.jar");
        tokio::fs::create_dir_all(cache.parent().unwrap())
            .await
            .unwrap();
        let mut bytes = b"PK\x05\x06".to_vec();
        bytes.extend([0; 18]);
        tokio::fs::write(&cache, &bytes).await.unwrap();
        let hash = arcadium_io::sha1_file(&cache).await.unwrap();
        let artifact = artifact_dao::insert_artifact(
            &services.db,
            &hash,
            ContentType::Mod as i64,
            "artifacts/nvidium-fixture.jar",
            "nvidium-fixture.jar",
            Some(bytes.len() as i64),
        )
        .await
        .unwrap();
        let global = arcadium_cluster::GameSettingsProfile::default_global_profile();
        for mods_in_cluster in [true, false] {
            let cluster = clusters
                .create(
                    &global,
                    CreateClusterOptions::new(
                        format!("Nvidium {mods_in_cluster}"),
                        "26.2",
                        GameLoader::Fabric,
                    ),
                )
                .await
                .unwrap();
            let game_dir = cluster.dir().unwrap().join("runtime");
            let user_game_dir = cluster.dir().unwrap().join("userdata");
            tokio::fs::create_dir_all(&user_game_dir).await.unwrap();
            let options = user_game_dir.join("options.txt");
            tokio::fs::write(
                &options,
                "preferredGraphicsBackend:\"vulkan\"\nrenderDistance:8\n",
            )
            .await
            .unwrap();
            super::super::materialize_content(&services, &cluster, &game_dir, mods_in_cluster)
                .await
                .unwrap();
            let active = active_mods(&cluster, &game_dir, mods_in_cluster)
                .unwrap()
                .join(&artifact.file_name);
            assert!(!active.exists());

            activate_cached_mod(
                &services,
                &cluster,
                &artifact,
                &user_game_dir,
                &game_dir,
                mods_in_cluster,
            )
            .await
            .unwrap();
            assert_eq!(tokio::fs::read(&active).await.unwrap(), bytes);
            assert!(arcadium_net::matches_on_disk(&active, &hash).await);
            assert_eq!(
                tokio::fs::read_to_string(&options).await.unwrap(),
                "renderDistance:8\npreferredGraphicsBackend:\"opengl\"\n"
            );
            assert_eq!(
                artifact_dao::get_cluster_artifact(&services.db, cluster.id, &hash)
                    .await
                    .unwrap()
                    .unwrap()
                    .enabled,
                1
            );

            PackageStore::set_artifact_enabled_to(cluster.id, &hash, false, &services.content())
                .await
                .unwrap();
            super::super::materialize_content(&services, &cluster, &game_dir, mods_in_cluster)
                .await
                .unwrap();
            tokio::fs::write(&options, "preferredGraphicsBackend:\"vulkan\"\n")
                .await
                .unwrap();
            assert!(
                activate_cached_mod(
                    &services,
                    &cluster,
                    &artifact,
                    &user_game_dir,
                    &game_dir,
                    mods_in_cluster
                )
                .await
                .is_err()
            );
            assert!(!active.exists());
            assert_eq!(
                tokio::fs::read_to_string(&options).await.unwrap(),
                "preferredGraphicsBackend:\"vulkan\"\n"
            );
            assert_eq!(
                artifact_dao::get_cluster_artifact(&services.db, cluster.id, &hash)
                    .await
                    .unwrap()
                    .unwrap()
                    .enabled,
                0
            );
        }

        let cluster = clusters
            .create(
                &global,
                CreateClusterOptions::new("Nvidium failure", "26.2", GameLoader::Fabric),
            )
            .await
            .unwrap();
        let game_dir = cluster.dir().unwrap().join("runtime");
        let user_game_dir = cluster.dir().unwrap().join("userdata");
        tokio::fs::create_dir_all(user_game_dir.join("options.txt"))
            .await
            .unwrap();
        assert!(
            activate_cached_mod(
                &services,
                &cluster,
                &artifact,
                &user_game_dir,
                &game_dir,
                true
            )
            .await
            .is_err()
        );
        assert!(
            artifact_dao::get_cluster_artifact(&services.db, cluster.id, &hash)
                .await
                .unwrap()
                .is_none()
        );
        assert!(
            !active_mods(&cluster, &game_dir, true)
                .unwrap()
                .join(&artifact.file_name)
                .exists()
        );
    }
}
