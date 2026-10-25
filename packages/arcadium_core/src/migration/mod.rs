pub mod arcadium_v1;
pub mod vanilla;

use std::path::PathBuf;

use serde::{Deserialize, Serialize};

use crate::LauncherResult;
use arcadium_common::domain::GameLoader;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum MigrationSource {
    ArcadiumV1,
    Vanilla,
}

impl MigrationSource {
    pub const ALL: &'static [MigrationSource] =
        &[MigrationSource::ArcadiumV1, MigrationSource::Vanilla];

    pub fn id(self) -> &'static str {
        match self {
            MigrationSource::ArcadiumV1 => "arcadium_v1",
            MigrationSource::Vanilla => "vanilla",
        }
    }

    pub fn display_name(self) -> &'static str {
        match self {
            MigrationSource::ArcadiumV1 => "Arcadium",
            MigrationSource::Vanilla => "Minecraft",
        }
    }

    pub fn from_id(id: &str) -> Option<MigrationSource> {
        Self::ALL.iter().copied().find(|s| s.id() == id)
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SourceInstance {
    /// Source-local identifier (v1 the cluster row id)
    /// Purely informational
    pub instance_id: i64,
    pub folder_name: String,
    pub mc_version: String,
    /// Set for "migrated" clusters (e.g. 26.1 fabric -> 26.1.2 fabric)
    pub target_mc_version: Option<String>,
    pub mc_loader: GameLoader,
    pub target_mc_loader: Option<GameLoader>,
    /// Empty when the source has no category concept
    pub categories: Vec<String>,
    pub has_game_dir: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MigrationDetection {
    pub source: MigrationSource,
    pub root: PathBuf,
    pub instances: Vec<SourceInstance>,
}

impl SourceInstance {
    #[must_use]
    pub fn import_version(&self) -> &str {
        self.target_mc_version
            .as_deref()
            .unwrap_or(&self.mc_version)
    }

    #[must_use]
    pub fn import_loader(&self) -> GameLoader {
        self.target_mc_loader.unwrap_or(self.mc_loader)
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum ImportTarget {
    /// Copy into the shared `<launcher_dir>/.minecraft`
    Shared,
    /// Copy into the cluster's resolved game directory. Legacy worlds require manual import.
    Dedicated { new_cluster_id: i64 },
}

pub(crate) fn import_exclusions<'a>(
    base: &[&'a str],
    minecraft_version: Option<&str>,
) -> Vec<&'a str> {
    let mut excluded = base.to_vec();
    if minecraft_version.is_some_and(arcadium_common::distribution::uses_native_forge) {
        excluded.extend(["saves", "backups"]);
    }
    excluded
}

#[tracing::instrument]
pub async fn detect() -> LauncherResult<Option<MigrationDetection>> {
    for source in MigrationSource::ALL.iter().copied() {
        let detection = match source {
            MigrationSource::ArcadiumV1 => arcadium_v1::detect().await?,
            MigrationSource::Vanilla => vanilla::detect().await?,
        };

        if let Some(detection) = detection
            && !detection.instances.is_empty()
        {
            return Ok(Some(detection));
        }
    }
    Ok(None)
}

#[tracing::instrument(skip(state))]
pub async fn import_game_dir(
    state: &std::sync::Arc<crate::LauncherState>,
    source: MigrationSource,
    folder_name: &str,
    target: ImportTarget,
) -> LauncherResult<()> {
    match source {
        MigrationSource::ArcadiumV1 => {
            arcadium_v1::import_game_dir(state, folder_name, target).await
        }
        MigrationSource::Vanilla => vanilla::import_game_dir(state, target).await,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn legacy_import_copies_packs_without_copying_or_changing_unknown_worlds() {
        let source = arcadium_io::tempdir().await.unwrap();
        let target = arcadium_io::tempdir().await.unwrap();
        let source = source.dir_path();
        let target = target.dir_path();
        for path in [
            "saves/unknown/level.dat",
            "backups/unknown.zip",
            "resourcepacks/folder/pack.mcmeta",
        ] {
            let file = source.join(path);
            arcadium_io::create_dir_all(file.parent().unwrap())
                .await
                .unwrap();
            arcadium_io::write(file, b"original user data".to_vec())
                .await
                .unwrap();
        }
        let excluded = import_exclusions(&["mods"], Some("1.7.10"));
        arcadium_io::copy_dir(source, target, &excluded)
            .await
            .unwrap();
        assert!(!target.join("saves").exists());
        assert!(!target.join("backups").exists());
        assert!(target.join("resourcepacks/folder/pack.mcmeta").exists());
        assert_eq!(
            arcadium_io::read(source.join("saves/unknown/level.dat"))
                .await
                .unwrap(),
            b"original user data"
        );
        assert_eq!(
            arcadium_io::read(source.join("backups/unknown.zip"))
                .await
                .unwrap(),
            b"original user data"
        );
    }

    #[test]
    fn legacy_convenience_import_preserves_unknown_worlds_at_the_source() {
        let excluded = import_exclusions(&["mods", "versions"], Some("1.7.10"));
        assert!(excluded.contains(&"saves"));
        assert!(excluded.contains(&"backups"));
        assert!(!excluded.contains(&"resourcepacks"));
        assert!(!excluded.contains(&"screenshots"));
        assert!(!import_exclusions(&["mods"], Some("1.8.9")).contains(&"saves"));
        let modern = import_exclusions(&["mods"], Some("1.21.11"));
        assert!(!modern.contains(&"saves"));
        assert!(!modern.contains(&"backups"));
        assert!(!import_exclusions(&["mods"], None).contains(&"saves"));
    }

    #[test]
    fn vanilla_is_the_last_resort() {
        assert_eq!(MigrationSource::ALL.last(), Some(&MigrationSource::Vanilla));
    }

    #[test]
    fn import_version_prefers_resolved_target() {
        let mut instance = SourceInstance {
            instance_id: 1,
            folder_name: "26.1 fabric".to_string(),
            mc_version: "26.1".to_string(),
            target_mc_version: None,
            mc_loader: GameLoader::Fabric,
            target_mc_loader: None,
            categories: Vec::new(),
            has_game_dir: true,
        };
        assert_eq!(instance.import_version(), "26.1");

        instance.target_mc_version = Some("26.1.2".to_string());
        assert_eq!(instance.import_version(), "26.1.2");
    }

    #[test]
    fn import_loader_prefers_resolved_target() {
        let mut instance = SourceInstance {
            instance_id: 1,
            folder_name: "1.20.1 Forge".to_string(),
            mc_version: "1.20.1".to_string(),
            target_mc_version: None,
            mc_loader: GameLoader::Forge,
            target_mc_loader: None,
            categories: Vec::new(),
            has_game_dir: true,
        };
        assert_eq!(instance.import_loader(), GameLoader::Forge);

        instance.target_mc_loader = Some(GameLoader::NeoForge);
        assert_eq!(instance.import_loader(), GameLoader::NeoForge);
    }

    #[test]
    fn ids_round_trip() {
        for source in MigrationSource::ALL.iter().copied() {
            assert_eq!(MigrationSource::from_id(source.id()), Some(source));
        }
    }
}
