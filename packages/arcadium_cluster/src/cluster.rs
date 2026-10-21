use std::path::PathBuf;
use std::time::Duration;

use arcadium_db::models::ClusterId;
use arcadium_db::models::ClusterKind;
use arcadium_db::models::ClusterRow;
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

use crate::error::ClusterResult;
use arcadium_common::domain::GameLoader;
use arcadium_common::paths;

use crate::error::ClusterError;
use crate::stage::ClusterStage;

pub use arcadium_common::paths::DEDICATED_MARKER;

// takes a cluster out of the shared `mods` folder
pub async fn remove_mods_link(folder_name: &str) {
    let Ok(link) = paths::shared_mods_link(folder_name) else {
        return;
    };

    match arcadium_io::symlink_metadata(&link).await {
        Ok(meta) if meta.file_type().is_symlink() => {
            if let Err(err) = arcadium_io::remove_symlink_dir(&link).await {
                tracing::warn!(folder = folder_name, error = %err, "failed to clear cluster mods link");
            }
        }

        _ => {}
    }
}

pub const MODPACK_ICON_FILE: &str = ".arcadium/modpack-icon.png";

pub fn encode_tags(tags: &[String]) -> String {
    serde_json::to_string(tags).unwrap_or_else(|_| "[]".to_string())
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Cluster {
    pub id: ClusterId,
    pub name: String,
    pub folder_name: String,
    pub setting_profile_name: Option<String>,
    pub mc_version: String,
    pub mc_loader: GameLoader,
    pub mc_loader_version: Option<String>,
    pub stage: ClusterStage,
    pub created_at: Option<DateTime<Utc>>,
    pub last_played: Option<DateTime<Utc>>,
    #[serde(with = "serde_duration_secs")]
    pub overall_played: Duration,
    pub linked_modpack_hash: Option<String>,
    pub kind: ClusterKind,
    pub user_created: bool,
    pub description: Option<String>,
    pub tags: Vec<String>,
    pub cover_path: Option<String>,
}

impl PartialEq for Cluster {
    fn eq(&self, other: &Self) -> bool {
        self.id == other.id
    }
}

mod serde_duration_secs {
    use serde::{Deserialize, Deserializer, Serialize, Serializer};
    use std::time::Duration;

    pub fn serialize<S: Serializer>(duration: &Duration, serializer: S) -> Result<S::Ok, S::Error> {
        duration.as_secs().serialize(serializer)
    }

    pub fn deserialize<'de, D: Deserializer<'de>>(deserializer: D) -> Result<Duration, D::Error> {
        Ok(Duration::from_secs(u64::deserialize(deserializer)?))
    }
}

impl Cluster {
    pub fn try_from_row(row: ClusterRow) -> Result<Self, ClusterError> {
        let mc_loader = GameLoader::from_repr(row.mc_loader as u8)
            .ok_or(ClusterError::InvalidLoader(row.mc_loader))?;
        let stage =
            ClusterStage::from_repr(row.stage).ok_or(ClusterError::InvalidStage(row.stage))?;
        let kind = ClusterKind::from_repr(row.kind).ok_or(ClusterError::InvalidKind(row.kind))?;

        Ok(Self {
            id: row.id,
            name: row.name,
            folder_name: row.folder_name,
            setting_profile_name: row.setting_profile_name,
            mc_version: row.mc_version,
            mc_loader,
            mc_loader_version: row.mc_loader_version,
            stage,
            created_at: parse_timestamp(row.created_at),
            last_played: parse_timestamp(row.last_played),
            overall_played: Duration::from_secs(row.overall_played.unwrap_or(0).max(0) as u64),
            linked_modpack_hash: row.linked_modpack_hash,
            kind,
            user_created: row.user_created != 0,
            description: row.description,
            tags: serde_json::from_str(&row.tags).unwrap_or_default(),
            cover_path: row.cover_path,
        })
    }

    pub fn dir(&self) -> ClusterResult<PathBuf> {
        Ok(paths::cluster_dir(&self.folder_name)?)
    }

    pub fn dedicated_marker(&self) -> ClusterResult<PathBuf> {
        Ok(self.dir()?.join(DEDICATED_MARKER))
    }

    pub fn uses_dedicated_dir(&self) -> bool {
        self.is_isolated()
            || arcadium_common::distribution::requires_separate_game_dir(&self.mc_version)
            || paths::cluster_uses_dedicated_dir(&self.folder_name)
    }

    pub fn is_isolated(&self) -> bool {
        self.kind.is_isolated()
            || arcadium_common::distribution::requires_separate_game_dir(&self.mc_version)
    }

    pub fn uses_bundles(&self) -> bool {
        self.kind.uses_bundles()
    }

    pub fn lacks_mod_loader(&self) -> bool {
        self.is_isolated() && self.mc_loader == arcadium_common::domain::GameLoader::Vanilla
    }

    pub fn shares_content(&self, content_type: arcadium_common::domain::ContentType) -> bool {
        content_type.is_global() && !self.kind.is_isolated()
    }

    pub fn modpack_icon_file(&self) -> Option<PathBuf> {
        self.linked_modpack_hash.as_ref()?;
        Some(self.dir().ok()?.join(MODPACK_ICON_FILE))
    }

    pub fn cover_file(&self) -> Option<PathBuf> {
        let cover = self.cover_path.as_deref()?;
        let mut parts = std::path::Path::new(cover).components();
        let Some(std::path::Component::Normal(name)) = parts.next() else {
            return None;
        };
        if parts.next().is_some() {
            return None;
        }
        Some(self.dir().ok()?.join(name))
    }

    pub fn game_dir(&self) -> ClusterResult<PathBuf> {
        Ok(paths::cluster_game_dir(
            &self.folder_name,
            self.is_isolated(),
            &self.mc_version,
        )?)
    }

    /// Player data is shared; loader state still uses `game_dir`.
    pub fn user_game_dir(&self) -> ClusterResult<PathBuf> {
        if self.uses_bundles() && arcadium_common::distribution::supports_version(&self.mc_version)
        {
            Ok(paths::shared_minecraft_dir()?)
        } else {
            self.game_dir()
        }
    }

    pub fn as_link_target(&self) -> ClusterLinkTarget<'_> {
        ClusterLinkTarget {
            id: self.id,
            folder_name: &self.folder_name,
            mc_version: &self.mc_version,
            mc_loader: self.mc_loader,
        }
    }
}

#[derive(Debug, Clone)]
pub struct ClusterLinkTarget<'a> {
    pub id: ClusterId,
    pub folder_name: &'a str,
    pub mc_version: &'a str,
    pub mc_loader: GameLoader,
}

fn parse_timestamp(value: Option<String>) -> Option<DateTime<Utc>> {
    value.and_then(|text| {
        DateTime::parse_from_rfc3339(&text)
            .ok()
            .map(|dt| dt.with_timezone(&Utc))
    })
}

#[cfg(test)]
mod shared_user_data_tests {
    use super::*;
    use arcadium_common::domain::ContentType;

    #[test]
    fn official_versions_share_user_data_but_keep_native_mods_and_caches_separate() {
        let primary = Cluster {
            id: 1,
            name: "Shared data test".into(),
            folder_name: "shared-data-test-189".into(),
            setting_profile_name: None,
            mc_version: "1.8.9".into(),
            mc_loader: GameLoader::Ornithe,
            mc_loader_version: None,
            stage: ClusterStage::from_repr(0).unwrap(),
            created_at: None,
            last_played: None,
            overall_played: Duration::ZERO,
            linked_modpack_hash: None,
            kind: ClusterKind::Arcadium,
            user_created: false,
            description: None,
            tags: Vec::new(),
            cover_path: None,
        };
        let mut legacy = primary.clone();
        legacy.id = 2;
        legacy.folder_name = "shared-data-test-1710".into();
        legacy.mc_version = "1.7.10".into();
        assert_eq!(
            primary.user_game_dir().unwrap(),
            legacy.user_game_dir().unwrap()
        );
        assert_ne!(legacy.game_dir().unwrap(), legacy.user_game_dir().unwrap());
        assert_ne!(primary.game_dir().unwrap(), legacy.game_dir().unwrap());
        assert!(legacy.shares_content(ContentType::ResourcePack));
        assert!(legacy.shares_content(ContentType::Shader));
        assert!(!legacy.shares_content(ContentType::Mod));
        legacy.kind = ClusterKind::Modded;
        assert_eq!(legacy.user_game_dir().unwrap(), legacy.game_dir().unwrap());
        assert!(!legacy.shares_content(ContentType::ResourcePack));
    }
}
