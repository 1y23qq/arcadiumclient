// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

use std::collections::HashSet;
use std::path::{Path, PathBuf};

use arcadium_db::dao::{artifact as artifact_dao, cluster_bundle as bundle_dao};
use arcadium_db::models::BundleTrackedArtifactRow;
use serde::Deserialize;

use crate::ctx::ContentCtx;
use crate::error::{ContentError, ContentResult};
use crate::packages::store::PackageStore;

const OWNED_BUNDLES: &[&str] = &[
    "Arcadium Client 1.8.9 Ornithe [HUD]",
    "Arcadium Client 1.8.9 Ornithe [Performance]",
    "Arcadium Client 1.8.9 Ornithe [QoL]",
    "Arcadium Client 1.8.9 Ornithe [Utility]",
];

#[derive(Deserialize)]
struct Policy {
    excluded: Vec<Excluded>,
}

#[derive(Deserialize)]
struct Excluded {
    #[serde(default, rename = "historicalArtifacts")]
    artifacts: Vec<HistoricalArtifact>,
}

#[derive(Deserialize)]
struct HistoricalArtifact {
    file: String,
    sha1: String,
}

fn historical_mod_hashes() -> ContentResult<HashSet<String>> {
    let policy: Policy = serde_json::from_slice(arcadium_common::distribution::BUNDLE_POLICY)?;
    Ok(policy
        .excluded
        .into_iter()
        .flat_map(|entry| entry.artifacts)
        .filter(|artifact| {
            artifact.file.starts_with("mods/")
                && artifact.sha1.len() == 40
                && artifact.sha1.bytes().all(|byte| byte.is_ascii_hexdigit())
        })
        .map(|artifact| artifact.sha1.to_ascii_lowercase())
        .collect())
}

fn is_retired(row: &BundleTrackedArtifactRow, hashes: &HashSet<String>) -> bool {
    row.bundle_name
        .as_deref()
        .is_some_and(|name| OWNED_BUNDLES.contains(&name))
        && hashes.contains(&row.hash.to_ascii_lowercase())
}

fn conflict(reason: String) -> ContentError {
    ContentError::RetiredBundleConflict { reason }
}

fn leaf_name(name: &str) -> bool {
    !name.is_empty()
        && !matches!(name, "." | "..")
        && !name
            .chars()
            .any(|character| matches!(character, '/' | '\\' | ':' | '\0'))
}

async fn matches_original(path: &Path, expected: &str) -> ContentResult<bool> {
    match tokio::fs::symlink_metadata(path).await {
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => return Ok(false),
        Err(error) => {
            return Err(conflict(format!(
                "Cannot inspect {} ({error}). Close Minecraft and retry.",
                path.display()
            )));
        }
        Ok(metadata) if !metadata.is_file() && !metadata.file_type().is_symlink() => {
            return Err(conflict(format!(
                "{} is no longer the original bundled file and was preserved. Move it out of the instance's mods folder and retry.",
                path.display()
            )));
        }
        Ok(_) => {}
    }
    let actual = arcadium_io::sha1_file(path).await.map_err(|error| conflict(format!(
        "Cannot read {} ({error}). Close Minecraft and any program using this file, then retry.", path.display())))?;
    if !actual.eq_ignore_ascii_case(expected) {
        return Err(conflict(format!(
            "{} has been modified and was preserved. Move it out of the instance's mods folder, then retry.",
            path.display()
        )));
    }
    Ok(true)
}

async fn retire_rows(
    db: &arcadium_db::DbPool,
    cluster_id: i64,
    roots: &[PathBuf],
    hashes: &HashSet<String>,
) -> ContentResult<()> {
    for row in bundle_dao::list_bundle_tracked(db, cluster_id).await? {
        if !is_retired(&row, hashes) {
            continue;
        }
        if !leaf_name(&row.cluster_file_name) {
            return Err(conflict("An old bundled mod has an unsafe tracked filename. Remove it through the instance's Mods page and retry.".into()));
        }
        let paths: Vec<_> = roots
            .iter()
            .map(|root| root.join(&row.cluster_file_name))
            .collect();
        for path in &paths {
            matches_original(path, &row.hash).await?;
        }
        for path in &paths {
            if matches_original(path, &row.hash).await? {
                match tokio::fs::remove_file(path).await {
                    Ok(()) => {}
                    Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
                    Err(error) => {
                        return Err(conflict(format!(
                            "Cannot remove old bundled mod {} ({error}). Close Minecraft and any program using this file, then retry.",
                            path.display()
                        )));
                    }
                }
            }
        }
        artifact_dao::unlink_cluster_artifact(db, cluster_id, &row.hash).await?;
        tracing::info!(cluster_id, file = %row.cluster_file_name, sha1 = %row.hash, "retired unchanged bundle-managed mod");
    }
    Ok(())
}

pub(super) async fn retire_cluster(cluster_id: i64, ctx: &ContentCtx) -> ContentResult<()> {
    let result = async {
        let cluster = PackageStore::get_cluster(cluster_id, ctx).await?;
        if cluster.mc_version != "1.8.9" || !cluster.uses_bundles() {
            return Ok(());
        }
        let mut roots = vec![
            arcadium_common::paths::cluster_mods_dir(&cluster.folder_name)?,
            arcadium_common::paths::cluster_game_dir(&cluster.folder_name, cluster.is_isolated())?
                .join("mods"),
        ];
        roots.sort();
        roots.dedup();
        retire_rows(&ctx.db, cluster_id, &roots, &historical_mod_hashes()?).await
    }
    .await;
    result.map_err(|error| match error {
        ContentError::RetiredBundleConflict { .. } => error,
        _ => conflict(format!(
            "The old bundled-mod check failed ({error}). Restart Arcadium and retry."
        )),
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use arcadium_io::testing::ScratchDir;

    const ORIGINAL: &[u8] = b"synthetic retired mod fixture";
    const BUNDLE: &str = "Arcadium Client 1.8.9 Ornithe [Performance]";

    async fn database() -> arcadium_db::DbPool {
        let db = sqlx::sqlite::SqlitePoolOptions::new()
            .max_connections(1)
            .connect("sqlite::memory:")
            .await
            .unwrap();
        sqlx::query("CREATE TABLE cluster_artifacts (cluster_id INTEGER, hash TEXT, cluster_file_name TEXT, enabled INTEGER, bundle_name TEXT, bundle_version_id TEXT, package_id TEXT, installed_at TEXT)")
            .execute(&db).await.unwrap();
        db
    }

    async fn seed(
        db: &arcadium_db::DbPool,
        cluster: i64,
        name: &str,
        hash: &str,
        bundle: Option<&str>,
    ) {
        sqlx::query(
            "INSERT INTO cluster_artifacts VALUES (?, ?, ?, 1, ?, '0.3.6', 'fixture', NULL)",
        )
        .bind(cluster)
        .bind(hash)
        .bind(name)
        .bind(bundle)
        .execute(db)
        .await
        .unwrap();
    }

    async fn count(db: &arcadium_db::DbPool, cluster: i64) -> i64 {
        sqlx::query_scalar("SELECT COUNT(*) FROM cluster_artifacts WHERE cluster_id = ?")
            .bind(cluster)
            .fetch_one(db)
            .await
            .unwrap()
    }

    fn hashes() -> HashSet<String> {
        HashSet::from([arcadium_io::sha1_bytes(ORIGINAL)])
    }

    #[tokio::test]
    async fn removes_only_exact_original_bundle_managed_mod_and_its_db_link() {
        let temp = ScratchDir::new("retired-bundle-exact");
        let file = temp.join("old.jar");
        tokio::fs::write(&file, ORIGINAL).await.unwrap();
        let db = database().await;
        seed(
            &db,
            1,
            "old.jar",
            &arcadium_io::sha1_bytes(ORIGINAL),
            Some(BUNDLE),
        )
        .await;
        seed(
            &db,
            2,
            "old.jar",
            &arcadium_io::sha1_bytes(ORIGINAL),
            Some(BUNDLE),
        )
        .await;
        retire_rows(&db, 1, &[temp.path().to_owned()], &hashes())
            .await
            .unwrap();
        assert!(!file.exists());
        assert_eq!(count(&db, 1).await, 0);
        assert_eq!(count(&db, 2).await, 1);
    }

    #[tokio::test]
    async fn preserves_user_added_unrelated_bundle_and_unknown_hash_files() {
        let temp = ScratchDir::new("retired-bundle-user-data");
        let db = database().await;
        for (name, bundle, hash) in [
            ("manual.jar", None, arcadium_io::sha1_bytes(ORIGINAL)),
            (
                "other-pack.jar",
                Some("User's modpack"),
                arcadium_io::sha1_bytes(ORIGINAL),
            ),
            (
                "different.jar",
                Some(BUNDLE),
                "0000000000000000000000000000000000000000".into(),
            ),
        ] {
            tokio::fs::write(temp.join(name), ORIGINAL).await.unwrap();
            seed(&db, 1, name, &hash, bundle).await;
        }
        retire_rows(&db, 1, &[temp.path().to_owned()], &hashes())
            .await
            .unwrap();
        assert_eq!(count(&db, 1).await, 3);
        for name in ["manual.jar", "other-pack.jar", "different.jar"] {
            assert!(temp.join(name).exists());
        }
    }

    #[tokio::test]
    async fn modified_file_is_preserved_and_retirement_is_retryable() {
        let temp = ScratchDir::new("retired-bundle-modified");
        let db = database().await;
        let file = temp.join("old.jar");
        tokio::fs::write(&file, b"user modification").await.unwrap();
        seed(
            &db,
            1,
            "old.jar",
            &arcadium_io::sha1_bytes(ORIGINAL),
            Some(BUNDLE),
        )
        .await;
        let error = retire_rows(&db, 1, &[temp.path().to_owned()], &hashes())
            .await
            .unwrap_err();
        assert!(matches!(error, ContentError::RetiredBundleConflict { .. }));
        assert_eq!(tokio::fs::read(&file).await.unwrap(), b"user modification");
        assert_eq!(count(&db, 1).await, 1);
        tokio::fs::remove_file(file).await.unwrap();
        retire_rows(&db, 1, &[temp.path().to_owned()], &hashes())
            .await
            .unwrap();
        assert_eq!(count(&db, 1).await, 0);
    }

    #[tokio::test]
    async fn unsafe_tracked_name_cannot_escape_the_mods_directory() {
        let temp = ScratchDir::new("retired-bundle-paths");
        let db = database().await;
        for name in ["../outside.jar", "..\\outside.jar", "C:outside.jar"] {
            seed(
                &db,
                1,
                name,
                &arcadium_io::sha1_bytes(ORIGINAL),
                Some(BUNDLE),
            )
            .await;
        }
        assert!(matches!(
            retire_rows(&db, 1, &[temp.path().to_owned()], &hashes()).await,
            Err(ContentError::RetiredBundleConflict { .. })
        ));
        assert_eq!(count(&db, 1).await, 3);
    }

    #[cfg(windows)]
    #[tokio::test]
    async fn held_windows_file_blocks_launch_without_forgetting_its_db_tracking() {
        use std::os::windows::fs::OpenOptionsExt;
        let temp = ScratchDir::new("retired-bundle-held");
        let db = database().await;
        let file = temp.join("old.jar");
        tokio::fs::write(&file, ORIGINAL).await.unwrap();
        seed(
            &db,
            1,
            "old.jar",
            &arcadium_io::sha1_bytes(ORIGINAL),
            Some(BUNDLE),
        )
        .await;
        let held = std::fs::OpenOptions::new()
            .read(true)
            .share_mode(1)
            .open(&file)
            .unwrap();
        let error = retire_rows(&db, 1, &[temp.path().to_owned()], &hashes())
            .await
            .unwrap_err();
        assert!(error.to_string().contains("Close Minecraft"));
        assert!(file.exists());
        assert_eq!(count(&db, 1).await, 1);
        drop(held);
        retire_rows(&db, 1, &[temp.path().to_owned()], &hashes())
            .await
            .unwrap();
        assert!(!file.exists());
        assert_eq!(count(&db, 1).await, 0);
    }

    #[test]
    fn historical_policy_targets_mods_without_touching_resource_packs() {
        let actual = historical_mod_hashes().unwrap();
        assert!(actual.contains("e234afa5498ac6a5b5a986f2d547ad3761b31b8d"));
        for exempt in [
            "6324b84ba636bb3dee2cc6898fbae40051ea9dc6",
            "b4d397612db05f78c9625a05f5876b6ff29c5dcb",
            "b304a781dbdfa1e9f60aeb2128fd22130169bdfc",
            "fc2ef10fb0470accab38a7b413066becb04bcb53",
        ] {
            assert!(!actual.contains(exempt));
        }
        assert!(!actual.contains("3c2adaa32cf142aa5fd9d7ea02cfa48685e3d529"));
    }
}
