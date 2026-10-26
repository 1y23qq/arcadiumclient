use std::path::{Path, PathBuf};

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
struct PinnedMod {
    project: String,
    version: String,
    mod_version: String,
    file: String,
    bytes: u64,
    url: String,
    sha1: String,
    sha512: String,
    published: String,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct CompatibilityMod {
    mod_id: String,
    replaces_sha1: String,
    #[serde(flatten)]
    artifact: PinnedMod,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct Release {
    #[serde(flatten)]
    artifact: PinnedMod,
    minecraft: String,
    sodium_versions: Vec<String>,
    #[serde(default)]
    compatibility_mods: Vec<CompatibilityMod>,
}

fn release(minecraft: &str) -> Option<&'static Release> {
    static MODERN: std::sync::OnceLock<Release> = std::sync::OnceLock::new();
    static NEXT: std::sync::OnceLock<Release> = std::sync::OnceLock::new();
    let (cell, bytes) = match minecraft {
        "1.21.11" => (&MODERN, arcadium_common::distribution::MODERN_12111_LOCK),
        "26.2" => (&NEXT, arcadium_common::distribution::MODERN_262_LOCK),
        _ => return None,
    };
    Some(cell.get_or_init(|| {
        let lock: serde_json::Value =
            serde_json::from_slice(bytes).expect("valid modern distribution lock");
        serde_json::from_value(lock["optionalNvidium"].clone()).expect("pinned Nvidium release")
    }))
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
    release(version).is_some()
        && !decided
        && !installed
        && adapters.iter().any(super::gpu::supports_nvidium)
}

fn compatible_sodium_version(pinned: &Release, version: &str) -> bool {
    pinned
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

async fn ask(
    events: &EventBus,
    adapters: &[Gpu],
    pinned: &Release,
    replace: bool,
) -> LauncherResult<bool> {
    let names = adapters
        .iter()
        .filter(|gpu| super::gpu::supports_nvidium(gpu))
        .map(|gpu| gpu.name.as_str())
        .collect::<Vec<_>>()
        .join(", ");
    let setup = if pinned.minecraft == "26.2" {
        "Enabling it installs Nvidium and selects OpenGL, which Nvidium requires."
    } else if replace {
        "Enabling it installs Nvidium and switches the bundled Sodium and Sodium Extra to compatible versions. Their current versions are kept disabled."
    } else {
        "Enabling it installs Nvidium with your current OpenGL renderer."
    };
    Ok(events.ask(
        Prompt::new("Nvidium recommended", format!(
            "Compatible NVIDIA GPU detected: {names}.\n\nWould you like to use Nvidium for Minecraft {}? {setup}", pinned.minecraft
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

struct InstalledMod {
    identity: arcadium_content::packages::local_manifest::JarModId,
    path: PathBuf,
    enabled: bool,
}

async fn installed_mods(mods: &Path) -> LauncherResult<Vec<InstalledMod>> {
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
            found.push(InstalledMod {
                identity,
                path,
                enabled: name.ends_with(".jar"),
            });
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
    let Some(pinned) = release(version) else {
        return Ok(None);
    };
    if !mods_in_cluster {
        return Ok(None);
    }
    let mods = active_mods(cluster, game_dir, mods_in_cluster)?;
    let state_path = decision_path(version)?;
    if let Ok(raw) = tokio::fs::read(&state_path).await
        && let Ok(state) = serde_json::from_slice::<Decision>(&raw)
        && state.minecraft == version
    {
        if state.choice == "enabled"
            && arcadium_net::matches_on_disk(
                &mods.join(&pinned.artifact.file),
                &pinned.artifact.sha1,
            )
            .await
            && (version != "26.2"
                || tokio::fs::read_to_string(user_game_dir.join("options.txt"))
                    .await
                    .is_ok_and(|options| prefers_opengl(&options)))
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
    let installed = present.iter().any(|found| found.identity.id == "nvidium")
        || linked.iter().any(|artifact| {
            artifact.project_id.as_deref() == Some(pinned.artifact.project.as_str())
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
        let Some(replacements) = compatibility_plan(services, cluster, pinned, &present).await?
        else {
            tracing::info!(
                "Nvidium recommendation deferred; the current mods need manual compatibility changes"
            );
            return Ok(None);
        };
        if ask(
            &services.events,
            &adapters,
            pinned,
            !replacements.is_empty(),
        )
        .await?
        {
            install(
                services,
                cluster,
                pinned,
                &replacements,
                user_game_dir,
                game_dir,
                mods_in_cluster,
            )
            .await?;
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

fn decision_path(minecraft: &str) -> LauncherResult<PathBuf> {
    Ok(arcadium_common::paths::data_dir()?
        .join(format!("metadata/recommendations/nvidium-{minecraft}.json")))
}

async fn compatibility_plan<'a>(
    services: &LauncherServices,
    cluster: &Cluster,
    pinned: &'a Release,
    present: &[InstalledMod],
) -> LauncherResult<Option<Vec<&'a CompatibilityMod>>> {
    let active: Vec<_> = present.iter().filter(|found| found.enabled).collect();
    let sodium: Vec<_> = active
        .iter()
        .filter(|found| found.identity.id == "sodium")
        .collect();
    if sodium.len() != 1 {
        return Ok(None);
    }
    let mut replacements = Vec::new();
    for replacement in &pinned.compatibility_mods {
        let found: Vec<_> = active
            .iter()
            .filter(|found| found.identity.id == replacement.mod_id)
            .collect();
        if found.is_empty() && replacement.mod_id != "sodium" {
            continue;
        }
        if found.len() != 1 {
            return Ok(None);
        }
        let found = found[0];
        if replacement.mod_id == "sodium"
            && found
                .identity
                .version
                .as_ref()
                .is_some_and(|value| compatible_sodium_version(pinned, value))
        {
            continue;
        }
        if arcadium_net::matches_on_disk(&found.path, &replacement.artifact.sha1).await {
            continue;
        }
        let linked = arcadium_db::dao::artifact::get_cluster_artifact(
            &services.db,
            cluster.id,
            &replacement.replaces_sha1,
        )
        .await?;
        if !linked.is_some_and(|link| link.enabled != 0)
            || !arcadium_net::matches_on_disk(&found.path, &replacement.replaces_sha1).await
        {
            return Ok(None);
        }
        replacements.push(replacement);
    }
    if !sodium[0]
        .identity
        .version
        .as_ref()
        .is_some_and(|value| compatible_sodium_version(pinned, value))
        && !replacements.iter().any(|entry| entry.mod_id == "sodium")
    {
        return Ok(None);
    }
    if !replacements.is_empty() {
        let lock: serde_json::Value =
            serde_json::from_slice(arcadium_common::distribution::MODERN_12111_LOCK)?;
        let known: std::collections::HashSet<_> = lock["bundles"]
            .as_array()
            .unwrap()
            .iter()
            .flat_map(|bundle| bundle["mods"].as_array().unwrap())
            .filter_map(|entry| entry["sha1"].as_str())
            .collect();
        for found in active {
            let hash = arcadium_io::sha1_file(&found.path).await?;
            if known.contains(hash.as_str())
                || pinned
                    .compatibility_mods
                    .iter()
                    .any(|entry| entry.artifact.sha1 == hash)
            {
                continue;
            }
            let entries =
                arcadium_io::read_zip_file_entries(&found.path, |name| name == "fabric.mod.json")
                    .await?;
            for (_, bytes) in entries {
                let manifest: serde_json::Value = serde_json::from_slice(&bytes)?;
                if ["depends", "breaks", "conflicts"].iter().any(|section| {
                    ["sodium", "sodium-extra"]
                        .iter()
                        .any(|id| !manifest[section][id].is_null())
                }) {
                    tracing::info!(file = %found.path.display(), "Nvidium recommendation deferred for custom renderer dependencies");
                    return Ok(None);
                }
            }
        }
    }
    Ok(Some(replacements))
}

async fn install(
    services: &LauncherServices,
    cluster: &Cluster,
    pinned: &Release,
    replacements: &[&CompatibilityMod],
    user_game_dir: &Path,
    game_dir: &Path,
    mods_in_cluster: bool,
) -> LauncherResult<()> {
    let mut artifacts = Vec::new();
    for replacement in replacements {
        artifacts.push(download(services, &replacement.artifact, &pinned.minecraft).await?);
    }
    artifacts.push(download(services, &pinned.artifact, &pinned.minecraft).await?);
    let replaced: Vec<_> = replacements
        .iter()
        .map(|entry| entry.replaces_sha1.as_str())
        .collect();
    activate_cached_mods(
        services,
        cluster,
        &artifacts,
        &replaced,
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

async fn download(
    services: &LauncherServices,
    pinned: &PinnedMod,
    minecraft: &str,
) -> LauncherResult<arcadium_db::models::ArtifactRow> {
    let ctx = services.content();
    let version = VersionDetail {
        version_id: pinned.version.clone(),
        project_id: pinned.project.clone(),
        name: pinned.file.clone(),
        version_number: pinned.mod_version.clone(),
        changelog: None,
        game_versions: vec![minecraft.into()],
        loaders: vec![GameLoader::Fabric],
        published: pinned.published.parse().expect("pinned publication date"),
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
    Ok(artifact)
}

async fn activate_cached_mods(
    services: &LauncherServices,
    cluster: &Cluster,
    artifacts: &[arcadium_db::models::ArtifactRow],
    replaced: &[&str],
    user_game_dir: &Path,
    game_dir: &Path,
    mods_in_cluster: bool,
) -> LauncherResult<()> {
    use arcadium_db::dao::artifact as artifact_dao;
    use arcadium_db::dao::cluster_bundle as bundle_dao;

    let ctx = services.content();
    let mut previous = Vec::new();
    for artifact in artifacts {
        let link =
            artifact_dao::get_cluster_artifact(&services.db, cluster.id, &artifact.hash).await?;
        if link.as_ref().is_some_and(|link| link.enabled == 0) {
            return Err(LauncherError::Minecraft(
                "A required Nvidium mod was disabled. Keep the current renderer or enable the mod first.".into(),
            ));
        }
        previous.push(link);
    }
    let overrides = bundle_dao::list_overrides(&services.db, cluster.id).await?;
    let mut previous_replaced = Vec::new();
    for hash in replaced {
        let link = artifact_dao::get_cluster_artifact(&services.db, cluster.id, hash)
            .await?
            .filter(|link| link.enabled != 0)
            .ok_or_else(|| {
                LauncherError::Minecraft(
                    "The current renderer changed before Nvidium installation.".into(),
                )
            })?;
        let tracked = bundle_dao::get_bundle_tracked(&services.db, cluster.id, hash).await?;
        previous_replaced.push((link, tracked));
    }
    let result = async {
        for (artifact, previous) in artifacts.iter().zip(&previous) {
            if previous.is_none() { PackageStore::link_by_hash(&artifact.hash, cluster.id, None, &ctx).await?; }
        }
        for (link, _) in &previous_replaced {
            arcadium_content::bundles::set_artifact_enabled_to(cluster.id, &link.hash, false, &ctx).await?;
        }
        super::materialize_content(services, cluster, game_dir, mods_in_cluster).await?;
        let mods = active_mods(cluster, game_dir, mods_in_cluster)?;
        for artifact in artifacts {
            let active = artifact_dao::get_cluster_artifact(&services.db, cluster.id, &artifact.hash).await?;
            let path = mods.join(active.as_ref().map_or(artifact.file_name.as_str(), |link| link.cluster_file_name.as_str()));
            if !active.is_some_and(|link| link.enabled != 0) || !arcadium_net::matches_on_disk(&path, &artifact.hash).await {
                return Err(LauncherError::Minecraft("Nvidium could not be placed in the active mods folder. The renderer was kept unchanged.".into()));
            }
        }
        for (link, _) in &previous_replaced {
            if mods.join(&link.cluster_file_name).exists() {
                return Err(LauncherError::Minecraft("The previous Sodium version is still active. The renderer was kept unchanged.".into()));
            }
        }
        if cluster.mc_version == "26.2" {
            let path = user_game_dir.join("options.txt");
            let original = match tokio::fs::read_to_string(&path).await {
                Ok(original) => original,
                Err(error) if error.kind() == std::io::ErrorKind::NotFound => String::new(),
                Err(error) => return Err(error.into()),
            };
            arcadium_io::write_atomic(&path, opengl_options(&original)).await?;
        }
        Ok(())
    }.await;
    if result.is_err() {
        for (artifact, previous) in artifacts.iter().zip(&previous) {
            if previous.is_none() {
                artifact_dao::unlink_cluster_artifact(&services.db, cluster.id, &artifact.hash)
                    .await?;
                let path =
                    active_mods(cluster, game_dir, mods_in_cluster)?.join(&artifact.file_name);
                if arcadium_net::matches_on_disk(&path, &artifact.hash).await {
                    arcadium_content::packages::store::remove_entry(&path).await?;
                }
            }
        }
        for (link, tracked) in &previous_replaced {
            artifact_dao::update_cluster_artifact(
                &services.db,
                cluster.id,
                &link.hash,
                &link.cluster_file_name,
                link.enabled,
            )
            .await?;
            if let Some(tracked) = tracked
                && let (Some(bundle), Some(package)) = (&tracked.bundle_name, &tracked.package_id)
            {
                if let Some(previous) = overrides
                    .iter()
                    .find(|row| &row.bundle_name == bundle && &row.package_id == package)
                    .and_then(|row| arcadium_db::models::OverrideType::parse(&row.override_type))
                {
                    bundle_dao::save_override(&services.db, cluster.id, bundle, package, previous)
                        .await?;
                } else {
                    bundle_dao::remove_override(&services.db, cluster.id, bundle, package).await?;
                }
            }
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

    async fn fixture_mod(
        services: &LauncherServices,
        name: &str,
        id: &str,
        version: &str,
        depends: serde_json::Value,
    ) -> arcadium_db::models::ArtifactRow {
        let relative = format!("artifacts/{name}");
        let path = arcadium_common::paths::data_dir().unwrap().join(&relative);
        let file = tokio::fs::File::create(&path).await.unwrap();
        let mut writer = async_zip::tokio::write::ZipFileWriter::with_tokio(file);
        writer.write_entry_whole(
            async_zip::ZipEntryBuilder::new("fabric.mod.json".into(), async_zip::Compression::Stored),
            &serde_json::to_vec(&serde_json::json!({"schemaVersion":1,"id":id,"version":version,"depends":depends})).unwrap(),
        ).await.unwrap();
        let mut output = writer.close().await.unwrap();
        futures_lite::io::AsyncWriteExt::flush(&mut output)
            .await
            .unwrap();
        let hash = arcadium_io::sha1_file(&path).await.unwrap();
        arcadium_db::dao::artifact::insert_artifact(
            &services.db,
            &hash,
            ContentType::Mod as i64,
            &relative,
            name,
            Some(tokio::fs::metadata(&path).await.unwrap().len() as i64),
        )
        .await
        .unwrap()
    }

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
    fn first_modern_launch_checks_all_adapters_without_overriding_prior_choices() {
        let adapters = hybrid();
        for minecraft in ["1.21.11", "26.2"] {
            assert!(recommendation_needed(minecraft, false, false, &adapters));
            assert!(!recommendation_needed(minecraft, true, false, &adapters));
            assert!(!recommendation_needed(minecraft, false, true, &adapters));
            assert!(!recommendation_needed(
                minecraft,
                false,
                false,
                &adapters[..1]
            ));
        }
        for minecraft in ["1.8.9", "1.7.10"] {
            assert!(!recommendation_needed(minecraft, false, false, &adapters));
        }
    }

    #[tokio::test]
    async fn prompt_installs_only_after_explicit_enable() {
        for (minecraft, replace) in [("26.2", false), ("1.21.11", false), ("1.21.11", true)] {
            for response in [Some("enable"), None] {
                let (events, mut receiver) = EventBus::channel();
                let task = tokio::spawn(async move {
                    ask(&events, &hybrid(), release(minecraft).unwrap(), replace).await
                });
                let Some(Event::Notification(Notification::Prompt(prompt))) = receiver.recv().await
                else {
                    panic!("expected recommendation")
                };
                assert!(prompt.body.contains("NVIDIA GeForce RTX 4060 Laptop GPU"));
                assert!(prompt.body.contains(minecraft));
                assert_eq!(prompt.body.contains("Sodium Extra"), replace);
                assert_eq!(prompt.choices.len(), 1);
                assert_eq!(prompt.dismiss.as_deref(), Some("Keep current renderer"));
                prompt.reply.send(response.map(Answer::new)).unwrap();
                assert_eq!(task.await.unwrap().unwrap(), response == Some("enable"));
            }
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
        let pinned = release("26.2").unwrap();
        assert_eq!(pinned.artifact.sha512.len(), 128);
        assert_eq!(pinned.artifact.sha1.len(), 40);
        assert_eq!(pinned.artifact.file, "nvidium-0.4.4-beta6-26.2.jar");
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
        assert!(compatible_sodium_version(
            release("26.2").unwrap(),
            "0.9.2+mc26.2"
        ));
        assert!(compatible_sodium_version(release("26.2").unwrap(), "0.9.2"));
        assert!(compatible_sodium_version(
            release("26.2").unwrap(),
            "0.9.1+mc26.2"
        ));
        assert!(!compatible_sodium_version(
            release("26.2").unwrap(),
            "0.9.20+mc26.2"
        ));
        assert!(!compatible_sodium_version(
            release("26.2").unwrap(),
            "0.9.3+mc26.2"
        ));
        assert!(!compatible_sodium_version(
            release("26.2").unwrap(),
            "0.9.2-beta.1"
        ));
        let pinned = release("1.21.11").unwrap();
        assert!(compatible_sodium_version(pinned, "0.8.12+mc1.21.11"));
        assert!(compatible_sodium_version(pinned, "0.8.11"));
        assert!(!compatible_sodium_version(pinned, "0.8.14+mc1.21.11"));
        assert!(!compatible_sodium_version(pinned, "0.8.120"));
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
        assert_ne!(
            decision_path("1.21.11").unwrap(),
            decision_path("26.2").unwrap()
        );
        for minecraft in ["1.21.11", "26.2"] {
            for mods_in_cluster in [true, false] {
                let cluster = clusters
                    .create(
                        &global,
                        CreateClusterOptions::new(
                            format!("Nvidium {minecraft} {mods_in_cluster}"),
                            minecraft,
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

                activate_cached_mods(
                    &services,
                    &cluster,
                    std::slice::from_ref(&artifact),
                    &[],
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
                    if minecraft == "26.2" {
                        "renderDistance:8\npreferredGraphicsBackend:\"opengl\"\n"
                    } else {
                        "preferredGraphicsBackend:\"vulkan\"\nrenderDistance:8\n"
                    }
                );
                assert_eq!(
                    artifact_dao::get_cluster_artifact(&services.db, cluster.id, &hash)
                        .await
                        .unwrap()
                        .unwrap()
                        .enabled,
                    1
                );

                PackageStore::set_artifact_enabled_to(
                    cluster.id,
                    &hash,
                    false,
                    &services.content(),
                )
                .await
                .unwrap();
                super::super::materialize_content(&services, &cluster, &game_dir, mods_in_cluster)
                    .await
                    .unwrap();
                tokio::fs::write(&options, "preferredGraphicsBackend:\"vulkan\"\n")
                    .await
                    .unwrap();
                assert!(
                    activate_cached_mods(
                        &services,
                        &cluster,
                        std::slice::from_ref(&artifact),
                        &[],
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
            activate_cached_mods(
                &services,
                &cluster,
                std::slice::from_ref(&artifact),
                &[],
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

        let cluster = clusters
            .create(
                &global,
                CreateClusterOptions::new("Compatible renderer", "1.21.11", GameLoader::Fabric),
            )
            .await
            .unwrap();
        let game_dir = cluster.dir().unwrap().join("runtime");
        let user_game_dir = cluster.dir().unwrap().join("userdata");
        tokio::fs::create_dir_all(&user_game_dir).await.unwrap();
        let options = user_game_dir.join("options.txt");
        let original = "version:4671\r\nrenderDistance:12\r\nlang:ja_jp\r\n";
        tokio::fs::write(&options, original).await.unwrap();
        let old = fixture_mod(
            &services,
            "sodium-original.jar",
            "sodium-fixture",
            "0.8.14",
            serde_json::json!({}),
        )
        .await;
        let compatible = fixture_mod(
            &services,
            "sodium-compatible.jar",
            "sodium-fixture",
            "0.8.12",
            serde_json::json!({}),
        )
        .await;
        PackageStore::link_by_hash(&old.hash, cluster.id, None, &services.content())
            .await
            .unwrap();
        use arcadium_db::dao::cluster_bundle as bundle_dao;
        use arcadium_db::models::OverrideType;
        bundle_dao::track_bundle_artifact(
            &services.db,
            cluster.id,
            &old.hash,
            "performance",
            "fixture",
            "sodium",
        )
        .await
        .unwrap();
        bundle_dao::save_override(
            &services.db,
            cluster.id,
            "performance",
            "sodium",
            OverrideType::Enabled,
        )
        .await
        .unwrap();
        super::super::materialize_content(&services, &cluster, &game_dir, true)
            .await
            .unwrap();
        let mods = active_mods(&cluster, &game_dir, true).unwrap();
        let mut pinned: Release = serde_json::from_value(
            serde_json::from_slice::<serde_json::Value>(
                arcadium_common::distribution::MODERN_12111_LOCK,
            )
            .unwrap()["optionalNvidium"]
                .clone(),
        )
        .unwrap();
        pinned.compatibility_mods.truncate(1);
        pinned.compatibility_mods[0].replaces_sha1 = old.hash.clone();
        pinned.compatibility_mods[0].artifact.sha1 = compatible.hash.clone();
        let mut found = installed_mods(&mods).await.unwrap();
        for entry in &mut found {
            if entry.identity.id == "sodium-fixture" {
                entry.identity.id = "sodium".into();
            }
        }
        assert_eq!(
            compatibility_plan(&services, &cluster, &pinned, &found)
                .await
                .unwrap()
                .unwrap()
                .len(),
            1
        );
        assert_eq!(
            artifact_dao::get_cluster_artifact(&services.db, cluster.id, &old.hash)
                .await
                .unwrap()
                .unwrap()
                .enabled,
            1
        );
        assert!(
            artifact_dao::get_cluster_artifact(&services.db, cluster.id, &compatible.hash)
                .await
                .unwrap()
                .is_none()
        );
        let custom = fixture_mod(
            &services,
            "custom-renderer-addon.jar",
            "custom-addon",
            "1",
            serde_json::json!({"sodium": ">=0.8.14"}),
        )
        .await;
        let mut custom_found = found;
        custom_found.push(InstalledMod {
            identity: arcadium_content::packages::local_manifest::JarModId {
                id: "custom-addon".into(),
                version: Some("1".into()),
            },
            path: artifact_absolute_path(&custom.path).unwrap(),
            enabled: true,
        });
        assert!(
            compatibility_plan(&services, &cluster, &pinned, &custom_found)
                .await
                .unwrap()
                .is_none()
        );

        let group = [compatible.clone(), artifact.clone()];
        let blocker = mods.join(&artifact.file_name);
        tokio::fs::create_dir(&blocker).await.unwrap();
        assert!(
            activate_cached_mods(
                &services,
                &cluster,
                &group,
                &[&old.hash],
                &user_game_dir,
                &game_dir,
                true
            )
            .await
            .is_err()
        );
        assert_eq!(
            artifact_dao::get_cluster_artifact(&services.db, cluster.id, &old.hash)
                .await
                .unwrap()
                .unwrap()
                .enabled,
            1
        );
        for added in &group {
            assert!(
                artifact_dao::get_cluster_artifact(&services.db, cluster.id, &added.hash)
                    .await
                    .unwrap()
                    .is_none()
            );
        }
        assert_eq!(
            bundle_dao::list_overrides(&services.db, cluster.id)
                .await
                .unwrap()[0]
                .override_type,
            "enabled"
        );
        assert!(arcadium_net::matches_on_disk(&mods.join(&old.file_name), &old.hash).await);
        tokio::fs::remove_dir(&blocker).await.unwrap();
        activate_cached_mods(
            &services,
            &cluster,
            &group,
            &[&old.hash],
            &user_game_dir,
            &game_dir,
            true,
        )
        .await
        .unwrap();
        assert_eq!(
            artifact_dao::get_cluster_artifact(&services.db, cluster.id, &old.hash)
                .await
                .unwrap()
                .unwrap()
                .enabled,
            0
        );
        assert!(!mods.join(&old.file_name).exists());
        for added in &group {
            assert!(arcadium_net::matches_on_disk(&mods.join(&added.file_name), &added.hash).await);
        }
        assert_eq!(
            bundle_dao::list_overrides(&services.db, cluster.id)
                .await
                .unwrap()[0]
                .override_type,
            "disabled"
        );
        assert_eq!(tokio::fs::read_to_string(&options).await.unwrap(), original);
    }
}
