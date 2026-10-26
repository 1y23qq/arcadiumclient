use std::path::{Path, PathBuf};

use crate::{LauncherError, LauncherResult};

pub(super) async fn prepare_mods(runtime: &Path, mods: &Path) -> LauncherResult<()> {
    tokio::fs::create_dir_all(mods).await?;
    let target = tokio::fs::canonicalize(mods).await?;
    let link = runtime.join("mods");
    if tokio::fs::canonicalize(&link).await.ok().as_ref() == Some(&target) {
        return Ok(());
    }
    if target.starts_with(&link) || link.starts_with(&target) {
        return Err(LauncherError::Minecraft(
            "Forge mod directories overlap.".into(),
        ));
    }
    let backup = match tokio::fs::symlink_metadata(&link).await {
        Ok(_) => {
            let backup = runtime.join(format!(".arcadium-previous-mods-{}", uuid::Uuid::new_v4()));
            tokio::fs::rename(&link, &backup).await?;
            Some(backup)
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => None,
        Err(error) => return Err(error.into()),
    };
    if let Err(error) = arcadium_io::symlink_dir(&target, &link).await {
        if let Some(backup) = &backup {
            tokio::fs::rename(backup, &link).await?;
        }
        return Err(error.into());
    }
    if let Some(backup) = backup {
        tracing::info!(path = %backup.display(), "Preserved the previous runtime mods folder before enabling native Forge");
    }
    Ok(())
}

pub(super) async fn retire_ornithe_mods(
    runtime: &Path,
    mods: &Path,
) -> LauncherResult<Vec<PathBuf>> {
    let retired: Vec<serde_json::Value> =
        serde_json::from_slice(arcadium_common::distribution::FORGE_1710_RETIRED)?;
    retire_entries(runtime, mods, &retired).await
}

async fn retire_entries(
    runtime: &Path,
    mods: &Path,
    retired: &[serde_json::Value],
) -> LauncherResult<Vec<PathBuf>> {
    let mut moved = Vec::new();
    for entry in retired {
        let file = entry["file"]
            .as_str()
            .ok_or_else(|| LauncherError::Minecraft("Invalid Forge migration file.".into()))?;
        if Path::new(file).file_name() != Some(std::ffi::OsStr::new(file)) {
            return Err(LauncherError::Minecraft(
                "Invalid Forge migration path.".into(),
            ));
        }
        for root in [mods.to_path_buf(), mods.join("1.7.10")] {
            let path = root.join(file);
            if !tokio::fs::try_exists(&path).await? {
                continue;
            }
            let known_hash = entry["sha1"].as_str().is_some_and(|sha1| sha1.len() == 40);
            let matches_hash = if known_hash {
                arcadium_net::matches_on_disk(&path, entry["sha1"].as_str().unwrap()).await
            } else {
                false
            };
            let matches_id = if let Some(id) = entry["modId"].as_str() {
                arcadium_content::packages::local_manifest::read_jar_mod_id(&path)
                    .await
                    .is_some_and(|found| found.id == id)
            } else {
                false
            };
            if !matches_hash && !matches_id {
                continue;
            }
            let backup = runtime
                .join(".arcadium-retired-ornithe")
                .join(uuid::Uuid::new_v4().to_string());
            tokio::fs::create_dir_all(&backup).await?;
            let destination = backup.join(file);
            tokio::fs::rename(&path, &destination).await?;
            moved.push(destination);
        }
    }
    Ok(moved)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn native_mod_link_preserves_a_previous_directory_and_is_repeatable() {
        let root = arcadium_io::testing::ScratchDir::new("native-forge-mod-link");
        let runtime = root.join("runtime");
        let mods = root.join("cluster-mods");
        tokio::fs::create_dir_all(runtime.join("mods"))
            .await
            .unwrap();
        tokio::fs::write(runtime.join("mods/previous.txt"), b"preserved")
            .await
            .unwrap();
        prepare_mods(&runtime, &mods).await.unwrap();
        prepare_mods(&runtime, &mods).await.unwrap();
        tokio::fs::write(mods.join("native.jar"), b"native mod")
            .await
            .unwrap();
        assert_eq!(
            tokio::fs::read(runtime.join("mods/native.jar"))
                .await
                .unwrap(),
            b"native mod"
        );
        let mut entries = tokio::fs::read_dir(&runtime).await.unwrap();
        let mut backup = None;
        while let Some(entry) = entries.next_entry().await.unwrap() {
            if entry
                .file_name()
                .to_string_lossy()
                .starts_with(".arcadium-previous-mods-")
            {
                backup = Some(entry.path());
            }
        }
        assert_eq!(
            tokio::fs::read(backup.unwrap().join("previous.txt"))
                .await
                .unwrap(),
            b"preserved"
        );
    }

    #[tokio::test]
    async fn retirement_keeps_unknown_files_and_preserves_known_component_bytes() {
        let root = arcadium_io::testing::ScratchDir::new("native-forge-retirement");
        let mods = root.join("mods");
        tokio::fs::create_dir_all(&mods).await.unwrap();
        let path = mods.join("ArcadiumCore-1.7.10.jar");
        let file = tokio::fs::File::create(&path).await.unwrap();
        let mut writer = async_zip::tokio::write::ZipFileWriter::with_tokio(file);
        writer
            .write_entry_whole(
                async_zip::ZipEntryBuilder::new(
                    "fabric.mod.json".into(),
                    async_zip::Compression::Stored,
                ),
                br#"{"id":"oneconfig","version":"older"}"#,
            )
            .await
            .unwrap();
        let mut file = writer.close().await.unwrap();
        futures_lite::io::AsyncWriteExt::flush(&mut file)
            .await
            .unwrap();
        drop(file);
        let bytes = tokio::fs::read(&path).await.unwrap();
        tokio::fs::write(
            mods.join("ArcadiumFeatures-1.7.10.jar"),
            b"an unrelated file",
        )
        .await
        .unwrap();
        tokio::fs::write(mods.join("user-native.jar"), b"user mod")
            .await
            .unwrap();
        let backups = retire_ornithe_mods(root.path(), &mods).await.unwrap();
        assert_eq!(backups.len(), 1);
        assert!(!path.exists());
        assert_eq!(tokio::fs::read(&backups[0]).await.unwrap(), bytes);
        assert!(mods.join("ArcadiumFeatures-1.7.10.jar").exists());
        assert!(mods.join("user-native.jar").exists());
        assert!(
            retire_ornithe_mods(root.path(), &mods)
                .await
                .unwrap()
                .is_empty()
        );
    }

    #[tokio::test]
    async fn split_module_retirement_requires_exact_bytes_and_preserves_modified_copies() {
        let root = arcadium_io::testing::ScratchDir::new("native-forge-split-retirement");
        let mods = root.join("mods");
        let versioned = mods.join("1.7.10");
        tokio::fs::create_dir_all(&versioned).await.unwrap();
        let filename = "+unimixins-compat-1.7.10-0.3.2.jar";
        let managed = b"known managed split module";
        tokio::fs::write(mods.join(filename), managed).await.unwrap();
        tokio::fs::write(versioned.join(filename), b"user modified module")
            .await
            .unwrap();
        let entries = [serde_json::json!({"file": filename, "sha1": arcadium_io::sha1_bytes(managed)})];
        let backups = retire_entries(root.path(), &mods, &entries).await.unwrap();
        assert_eq!(backups.len(), 1);
        assert_eq!(tokio::fs::read(&backups[0]).await.unwrap(), managed);
        assert!(!mods.join(filename).exists());
        assert_eq!(
            tokio::fs::read(versioned.join(filename)).await.unwrap(),
            b"user modified module"
        );
        assert!(retire_entries(root.path(), &mods, &entries).await.unwrap().is_empty());
    }
}
