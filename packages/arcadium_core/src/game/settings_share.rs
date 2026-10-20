use std::io;
use std::path::{Path, PathBuf};

const SHARED_PATHS: [&str; 4] = ["config", "profiles", "arcadium", "oneconfig/sounds"];
const BACKUP_DIRECTORY: &str = ".arcadium-settings-backup";

struct ChangedLink {
    path: PathBuf,
    backup: Option<PathBuf>,
}

async fn undo(changes: &[ChangedLink]) -> io::Result<()> {
    for change in changes.iter().rev() {
        if arcadium_io::read_link(&change.path).await.is_err() {
            return Err(io::Error::other(format!(
                "Settings path changed during migration: {}. Preserved settings remain in {:?}.",
                change.path.display(),
                change.backup
            )));
        }
        arcadium_io::remove_symlink_dir(&change.path)
            .await
            .map_err(io::Error::other)?;
        if let Some(backup) = &change.backup {
            tokio::fs::rename(backup, &change.path)
                .await
                .map_err(|error| {
                    io::Error::other(format!(
                        "Could not restore settings from {}: {error}",
                        backup.display()
                    ))
                })?;
        }
    }
    Ok(())
}

pub async fn share_client_settings(
    game_directory: &Path,
    primary_directory: &Path,
) -> io::Result<Vec<PathBuf>> {
    tokio::fs::create_dir_all(game_directory).await?;
    tokio::fs::create_dir_all(primary_directory).await?;
    let game = tokio::fs::canonicalize(game_directory).await?;
    let primary = tokio::fs::canonicalize(primary_directory).await?;
    if game == primary {
        return Ok(Vec::new());
    }
    if game.starts_with(&primary) || primary.starts_with(&game) {
        return Err(io::Error::other("Client settings directories overlap."));
    }

    let backup_base = game.join(BACKUP_DIRECTORY);
    if arcadium_io::read_link(&backup_base).await.is_ok() {
        return Err(io::Error::other(
            "The client settings backup directory is a link.",
        ));
    }
    let backup_root = backup_base.join(uuid::Uuid::new_v4().to_string());
    let mut changes = Vec::new();
    let mut backups = Vec::new();

    for name in SHARED_PATHS {
        let link = game.join(name);
        let target = primary.join(name);
        let temporary = game.join(format!(".arcadium-settings-link-{}", uuid::Uuid::new_v4()));
        let result = async {
            tokio::fs::create_dir_all(&target).await?;
            let actual_target = tokio::fs::canonicalize(&target).await?;
            if let Ok(current) = tokio::fs::canonicalize(&link).await
                && current == actual_target
            {
                return Ok::<_, io::Error>(None);
            }
            if actual_target.starts_with(&game) {
                return Err(io::Error::other("Shared settings point into the version-specific game directory."));
            }
            let parent = link.parent().ok_or_else(|| io::Error::other("Settings directory has no parent."))?;
            tokio::fs::create_dir_all(parent).await?;
            if !tokio::fs::canonicalize(parent).await?.starts_with(&game) {
                return Err(io::Error::other("A settings directory parent points outside the game directory."));
            }
            let previous = match tokio::fs::symlink_metadata(&link).await {
                Ok(metadata) => {
                    if !metadata.is_dir() && arcadium_io::read_link(&link).await.is_err() {
                        return Err(io::Error::other(format!("Settings path is not a directory: {}", link.display())));
                    }
                    Some(backup_root.join(name))
                }
                Err(error) if error.kind() == io::ErrorKind::NotFound => None,
                Err(error) => return Err(error),
            };
            arcadium_io::symlink_dir(&target, &temporary)
                .await
                .map_err(io::Error::other)?;
            if let Some(backup) = &previous {
                tokio::fs::create_dir_all(backup.parent().unwrap()).await?;
                tokio::fs::rename(&link, backup).await?;
            }
            if let Err(error) = tokio::fs::rename(&temporary, &link).await {
                if let Some(backup) = &previous
                    && let Err(restore) = tokio::fs::rename(backup, &link).await
                {
                    return Err(io::Error::other(format!("{error}; settings were preserved in {} but could not be restored: {restore}", backup.display())));
                }
                return Err(error);
            }
            Ok(Some(ChangedLink { path: link, backup: previous }))
        }
        .await;

        match result {
            Ok(Some(change)) => {
                if let Some(backup) = &change.backup {
                    backups.push(backup.clone());
                }
                changes.push(change);
            }
            Ok(None) => {}
            Err(error) => {
                if arcadium_io::read_link(&temporary).await.is_ok() {
                    let _ = arcadium_io::remove_symlink_dir(&temporary).await;
                }
                if let Err(restore) = undo(&changes).await {
                    return Err(io::Error::other(format!("{error}; {restore}")));
                }
                return Err(error);
            }
        }
    }
    Ok(backups)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn settings_are_shared_both_ways_without_touching_game_data() {
        let root = arcadium_io::testing::ScratchDir::new("shared_client_settings");
        let primary = root.path().join("primary");
        let legacy = root.path().join("legacy");
        for (name, old, current) in [
            ("config/fps.json", "legacy-fps", "primary-fps"),
            ("profiles/PvP/hud.json", "legacy-hud", "primary-hud"),
            (
                "arcadium/preferences.json",
                "legacy-preferences",
                "primary-preferences",
            ),
            (
                "oneconfig/sounds/custom.ogg",
                "legacy-sound",
                "primary-sound",
            ),
        ] {
            for (base, value) in [(&primary, current), (&legacy, old)] {
                let path = base.join(name);
                tokio::fs::create_dir_all(path.parent().unwrap())
                    .await
                    .unwrap();
                tokio::fs::write(path, value).await.unwrap();
            }
        }
        for (base, value) in [(&primary, "1.8.9"), (&legacy, "1.7.10")] {
            tokio::fs::create_dir_all(base.join("saves/World"))
                .await
                .unwrap();
            tokio::fs::write(base.join("saves/World/level.dat"), value)
                .await
                .unwrap();
            tokio::fs::write(base.join("options.txt"), value)
                .await
                .unwrap();
        }
        let backups = share_client_settings(&legacy, &primary).await.unwrap();
        assert_eq!(backups.len(), 4);
        for name in SHARED_PATHS {
            assert_eq!(
                tokio::fs::canonicalize(legacy.join(name)).await.unwrap(),
                tokio::fs::canonicalize(primary.join(name)).await.unwrap()
            );
        }
        assert_eq!(
            tokio::fs::read_to_string(legacy.join("profiles/PvP/hud.json"))
                .await
                .unwrap(),
            "primary-hud"
        );
        assert!(backups.iter().any(|path| path.join("fps.json").is_file()
            && std::fs::read_to_string(path.join("fps.json")).unwrap() == "legacy-fps"));
        tokio::fs::write(legacy.join("config/fps.json"), "changed-in-legacy")
            .await
            .unwrap();
        assert_eq!(
            tokio::fs::read_to_string(primary.join("config/fps.json"))
                .await
                .unwrap(),
            "changed-in-legacy"
        );
        tokio::fs::write(primary.join("profiles/PvP/hud.json"), "changed-in-primary")
            .await
            .unwrap();
        assert_eq!(
            tokio::fs::read_to_string(legacy.join("profiles/PvP/hud.json"))
                .await
                .unwrap(),
            "changed-in-primary"
        );
        for (base, value) in [(&primary, "1.8.9"), (&legacy, "1.7.10")] {
            assert_eq!(
                tokio::fs::read_to_string(base.join("options.txt"))
                    .await
                    .unwrap(),
                value
            );
            assert_eq!(
                tokio::fs::read_to_string(base.join("saves/World/level.dat"))
                    .await
                    .unwrap(),
                value
            );
        }
        assert!(
            share_client_settings(&legacy, &primary)
                .await
                .unwrap()
                .is_empty()
        );
    }

    #[tokio::test]
    async fn a_later_failure_restores_every_previous_native_directory() {
        let root = arcadium_io::testing::ScratchDir::new("shared_client_settings_rollback");
        let primary = root.path().join("primary");
        let legacy = root.path().join("legacy");
        tokio::fs::create_dir_all(primary.join("config"))
            .await
            .unwrap();
        tokio::fs::create_dir_all(legacy.join("config"))
            .await
            .unwrap();
        tokio::fs::write(primary.join("profiles"), "keep-this-file")
            .await
            .unwrap();
        tokio::fs::write(primary.join("config/fps.json"), "primary")
            .await
            .unwrap();
        tokio::fs::write(legacy.join("config/fps.json"), "legacy")
            .await
            .unwrap();
        assert!(share_client_settings(&legacy, &primary).await.is_err());
        assert!(arcadium_io::read_link(legacy.join("config")).await.is_err());
        assert_eq!(
            tokio::fs::read_to_string(legacy.join("config/fps.json"))
                .await
                .unwrap(),
            "legacy"
        );
        assert_eq!(
            tokio::fs::read_to_string(primary.join("config/fps.json"))
                .await
                .unwrap(),
            "primary"
        );
        assert_eq!(
            tokio::fs::read_to_string(primary.join("profiles"))
                .await
                .unwrap(),
            "keep-this-file"
        );
    }

    #[tokio::test]
    async fn already_shared_roots_are_untouched_and_nested_roots_are_rejected() {
        let root = arcadium_io::testing::ScratchDir::new("shared_client_settings_paths");
        assert!(
            share_client_settings(root.path(), root.path())
                .await
                .unwrap()
                .is_empty()
        );
        assert!(
            share_client_settings(&root.path().join("nested"), root.path())
                .await
                .is_err()
        );
        assert!(!root.path().join(BACKUP_DIRECTORY).exists());
    }

    #[tokio::test]
    async fn an_existing_native_file_is_preserved_instead_of_replaced() {
        let root = arcadium_io::testing::ScratchDir::new("shared_client_settings_existing_file");
        let primary = root.path().join("primary");
        let legacy = root.path().join("legacy");
        tokio::fs::create_dir_all(&legacy).await.unwrap();
        tokio::fs::write(legacy.join("config"), "preserve-native-file")
            .await
            .unwrap();
        assert!(share_client_settings(&legacy, &primary).await.is_err());
        assert_eq!(
            tokio::fs::read_to_string(legacy.join("config"))
                .await
                .unwrap(),
            "preserve-native-file"
        );
    }

    #[tokio::test]
    async fn a_redirected_backup_directory_cannot_move_settings_outside_the_game() {
        let root = arcadium_io::testing::ScratchDir::new("shared_client_settings_backup_link");
        let primary = root.path().join("primary");
        let legacy = root.path().join("legacy");
        let outside = root.path().join("outside");
        tokio::fs::create_dir_all(&outside).await.unwrap();
        tokio::fs::create_dir_all(legacy.join("config"))
            .await
            .unwrap();
        tokio::fs::write(legacy.join("config/fps.json"), "native-position")
            .await
            .unwrap();
        arcadium_io::symlink_dir(&outside, legacy.join(BACKUP_DIRECTORY))
            .await
            .unwrap();
        assert!(share_client_settings(&legacy, &primary).await.is_err());
        assert_eq!(
            tokio::fs::read_to_string(legacy.join("config/fps.json"))
                .await
                .unwrap(),
            "native-position"
        );
        assert_eq!(std::fs::read_dir(outside).unwrap().count(), 0);
    }
}
