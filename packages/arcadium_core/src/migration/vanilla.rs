use std::path::PathBuf;

use crate::LauncherResult;
use arcadium_common::domain::GameLoader;
use arcadium_common::paths;

use super::{ImportTarget, MigrationDetection, MigrationSource, SourceInstance};

const ROOT_INSTANCE_NAME: &str = ".minecraft";

const IMPORT_EXCLUDE_TOP: &[&str] = &[
    "mods",
    "shaderpacks",
    "datapacks",
    "versions",
    "libraries",
    "assets",
    "bin",
    "jre",
    "logs",
    "crash-reports",
    "launcher_accounts.json",
    "launcher_accounts_microsoft_store.json",
    "launcher_msa_credentials.bin",
    "launcher_profiles.json",
    "launcher_settings.json",
    "usercache.json",
    "webcache",
    "webcache2",
    ".mojang",
    "cheatbreaker_accounts.json",
    "feather",
    "feather-mods",
    "feather-versions",
    "optionsLC.txt",
    "servers.dat_old",
    "cache",
    "downloads",
    "jfr",
    "skyblock-repo-cache",
    "meowdding-repo-cache",
];

pub fn old_root() -> Option<PathBuf> {
    let base = directories::BaseDirs::new().map(|d| {
        // Windows `%APPDATA%\.minecraft` (data_dir is Roaming)
        #[cfg(target_os = "windows")]
        {
            d.data_dir().join(".minecraft")
        }
        // macOS `~/Library/Application Support/minecraft` no leading dot
        #[cfg(target_os = "macos")]
        {
            d.data_dir().join("minecraft")
        }
        // Linux `~/.minecraft` not data_dir() (`~/.local/share`)
        #[cfg(not(any(target_os = "windows", target_os = "macos")))]
        {
            d.home_dir().join(".minecraft")
        }
    });

    let root = match std::env::var("VANILLA_MC_DIR") {
        Ok(dir) => PathBuf::from(dir),
        Err(_) => base?,
    };

    let exists = root.is_dir();
    tracing::debug!(root = %root.display(), exists, "vanilla migration: resolved old root");
    exists.then_some(root)
}

#[tracing::instrument]
pub async fn detect() -> LauncherResult<Option<MigrationDetection>> {
    let Some(root) = old_root() else {
        return Ok(None);
    };

    if !arcadium_io::dir_has_content(&root).await {
        tracing::debug!(root = %root.display(), "vanilla migration: empty install, skipping");
        return Ok(None);
    }

    tracing::info!(root = %root.display(), "vanilla migration: detected install");

    Ok(Some(MigrationDetection {
        source: MigrationSource::Vanilla,
        instances: vec![SourceInstance {
            instance_id: 0,
            folder_name: ROOT_INSTANCE_NAME.to_string(),
            mc_version: String::new(),
            target_mc_version: None,
            mc_loader: GameLoader::Vanilla,
            target_mc_loader: None,
            categories: Vec::new(),
            has_game_dir: true,
        }],
        root,
    }))
}

#[tracing::instrument(skip(state, target))]
pub async fn import_game_dir(
    state: &std::sync::Arc<crate::LauncherState>,
    target: ImportTarget,
) -> LauncherResult<()> {
    let Some(src) = old_root() else {
        return Ok(());
    };

    let mut target_version = None;
    let dest = match &target {
        ImportTarget::Shared => paths::shared_minecraft_dir()?,
        ImportTarget::Dedicated { new_cluster_id } => {
            let cluster = state.clusters.get(*new_cluster_id).await?;
            target_version = Some(cluster.mc_version.clone());
            state.clusters.ensure_dedicated_marker(&cluster).await?;
            let dir = cluster.game_dir()?;
            arcadium_io::create_dir_all(&dir).await?;
            arcadium_io::write(cluster.dedicated_marker()?, Vec::new()).await?;
            dir
        }
    };

    arcadium_io::create_dir_all(&dest).await?;
    let excluded = super::import_exclusions(IMPORT_EXCLUDE_TOP, target_version.as_deref());
    arcadium_io::copy_dir(&src, &dest, &excluded).await?;

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn import_copies_user_data_and_skips_the_rest() {
        let src = arcadium_io::tempdir().await.expect("src dir");
        let dst = arcadium_io::tempdir().await.expect("dst dir");
        let (src, dst) = (src.dir_path(), dst.dir_path());

        for file in [
            "options.txt",
            "servers.dat",
            "launcher_accounts.json",
            "launcher_msa_credentials.bin",
            "cheatbreaker_accounts.json",
        ] {
            arcadium_io::write(src.join(file), b"x".to_vec())
                .await
                .unwrap();
        }
        for (dir, file) in [
            ("saves", "world/level.dat"),
            ("config", "sodium.json"),
            ("screenshots", "shot.png"),
            ("mods", "sodium.jar"),
            ("resourcepacks", "faithful.zip"),
            ("shaderpacks", "bsl.zip"),
            ("datapacks", "pack.zip"),
            ("feather-mods", "mod.jar"),
            ("jre", "bin/java"),
            ("versions", "1.21.1/1.21.1.jar"),
            ("libraries", "com/foo/foo.jar"),
            ("assets", "objects/ab/abcdef"),
            ("logs", "latest.log"),
        ] {
            let path = src.join(dir).join(file);
            arcadium_io::create_dir_all(path.parent().unwrap())
                .await
                .unwrap();
            arcadium_io::write(path, b"x".to_vec()).await.unwrap();
        }

        arcadium_io::copy_dir(src, dst, IMPORT_EXCLUDE_TOP)
            .await
            .unwrap();

        for kept in [
            "options.txt",
            "servers.dat",
            "saves/world/level.dat",
            "config/sodium.json",
            "screenshots/shot.png",
            "resourcepacks/faithful.zip",
        ] {
            assert!(dst.join(kept).exists(), "{kept} should have been copied");
        }
        for skipped in [
            "mods",
            "shaderpacks",
            "datapacks",
            "versions",
            "libraries",
            "assets",
            "logs",
            "launcher_accounts.json",
            "launcher_msa_credentials.bin",
            "cheatbreaker_accounts.json",
            "feather-mods",
            "jre",
        ] {
            assert!(
                !dst.join(skipped).exists(),
                "{skipped} should not have been copied"
            );
        }
    }

    #[tokio::test]
    async fn import_preserves_folder_resource_packs_and_selected_names() {
        let src = arcadium_io::tempdir().await.expect("src dir");
        let dst = arcadium_io::tempdir().await.expect("dst dir");
        let (src, dst) = (src.dir_path(), dst.dir_path());
        let pack = "resourcepacks/My Pack";
        let metadata = br#"{"pack":{"pack_format":1,"description":"Imported folder"}}"#;
        let texture = b"folder texture";
        let options = b"resourcePacks:[\"My Pack\",\"faithful.zip\"]\n";

        for (relative, contents) in [
            (format!("{pack}/pack.mcmeta"), metadata.as_slice()),
            (
                format!("{pack}/assets/minecraft/textures/blocks/stone.png"),
                texture.as_slice(),
            ),
            ("options.txt".to_owned(), options.as_slice()),
        ] {
            let path = src.join(relative);
            arcadium_io::create_dir_all(path.parent().unwrap())
                .await
                .unwrap();
            arcadium_io::write(path, contents).await.unwrap();
        }

        let archive = src.join("resourcepacks/faithful.zip");
        let file = tokio::fs::File::create(&archive).await.unwrap();
        let mut writer = async_zip::tokio::write::ZipFileWriter::with_tokio(file);
        let entry = async_zip::ZipEntryBuilder::new(
            "pack.mcmeta".to_owned().into(),
            async_zip::Compression::Stored,
        );
        writer.write_entry_whole(entry, metadata).await.unwrap();
        let mut output = writer.close().await.unwrap();
        futures_lite::io::AsyncWriteExt::flush(&mut output)
            .await
            .unwrap();
        let archive_bytes = arcadium_io::read(&archive).await.unwrap();

        arcadium_io::copy_dir(src, dst, IMPORT_EXCLUDE_TOP)
            .await
            .unwrap();

        assert_eq!(
            arcadium_io::read(dst.join(format!("{pack}/pack.mcmeta")))
                .await
                .unwrap(),
            metadata
        );
        assert_eq!(
            arcadium_io::read(
                dst.join(format!("{pack}/assets/minecraft/textures/blocks/stone.png"))
            )
            .await
            .unwrap(),
            texture
        );
        assert_eq!(
            arcadium_io::read(dst.join("options.txt")).await.unwrap(),
            options
        );
        assert_eq!(
            arcadium_io::read(dst.join("resourcepacks/faithful.zip"))
                .await
                .unwrap(),
            archive_bytes
        );
        assert!(src.join(format!("{pack}/pack.mcmeta")).exists());
        assert!(archive.exists());
    }
}
