use std::collections::HashSet;
use std::io::{Read, Seek};
use std::path::{Path, PathBuf};

use async_zip::base::read1::{ZipOptions, seek::ZipArchiveReader};
use futures_lite::io::{AsyncReadExt, BufReader, Cursor};
use futures_lite::{AsyncBufRead, AsyncSeek};
use futures_util::io::AllowStdIo;
use serde_json::Value;
use tokio::sync::OnceCell;

use super::PackageError;
use crate::{ContentError, ContentResult};

const MAX_ARCHIVE_BYTES: u64 = 512 * 1024 * 1024;
const MAX_METADATA_BYTES: u64 = 1024 * 1024;
const MAX_MOD_FILES: usize = 4096;

const IDENTITIES: &[(&str, &str)] = &[
    ("optifine", "OptiFine"),
    ("betterfps", "BetterFps"),
    ("foamfix", "FoamFix"),
    ("patcher", "Patcher"),
    ("memoryfix", "MemoryFix"),
    ("entityculling", "EntityCulling"),
    ("sodium", "Sodium"),
    ("lithium", "Lithium"),
    ("phosphor", "Phosphor"),
    ("starlight", "Starlight"),
    ("ferritecore", "FerriteCore"),
    ("rubidium", "Rubidium"),
    ("embeddium", "Embeddium"),
    ("argentum", "Argentum"),
];

const METADATA: &[&str] = &[
    "fabric.mod.json",
    "quilt.mod.json",
    "mcmod.info",
    "META-INF/mods.toml",
    "META-INF/neoforge.mods.toml",
    "META-INF/MANIFEST.MF",
];

const OPTIFINE_CLASSES: &[&str] = &[
    "optifine/OptiFineTweaker.class",
    "optifine/OptiFineForgeTweaker.class",
    "optifine/OptiFineClassTransformer.class",
];

/// Only compiled distribution hashes can exempt a user-visible archive.
pub async fn ensure_mod_allowed(path: &Path) -> ContentResult<()> {
    let managed = managed_hashes().await?;
    let path = path.to_path_buf();
    tokio::task::spawn_blocking(move || ensure_mod_allowed_sync(&path, managed))
        .await
        .map_err(std::io::Error::other)?
}

pub async fn check_mod_directories(roots: &[PathBuf]) -> ContentResult<()> {
    let roots = roots.to_vec();
    let files = tokio::task::spawn_blocking(move || collect_mod_archives(&roots))
        .await
        .map_err(std::io::Error::other)??;
    if files.is_empty() {
        return Ok(());
    }
    let managed = managed_hashes().await?;
    tokio::task::spawn_blocking(move || {
        for file in files {
            ensure_mod_allowed_sync(&file, managed)?;
        }
        Ok(())
    })
    .await
    .map_err(std::io::Error::other)?
}

fn collect_mod_archives(roots: &[PathBuf]) -> ContentResult<Vec<PathBuf>> {
    let mut directories = HashSet::new();
    let mut files = HashSet::new();
    for root in roots {
        for directory in [root.clone(), root.join("1.8.9"), root.join("1.7.10")] {
            let entries = match std::fs::read_dir(&directory) {
                Ok(entries) => entries,
                Err(error) if error.kind() == std::io::ErrorKind::NotFound => continue,
                Err(error) => return Err(error.into()),
            };
            if !directories.insert(std::fs::canonicalize(&directory)?) {
                continue;
            }
            for entry in entries {
                let path = entry?.path();
                if !path
                    .extension()
                    .is_some_and(|extension| extension.eq_ignore_ascii_case("jar"))
                    || !std::fs::metadata(&path)?.is_file()
                {
                    continue;
                }
                files.insert(std::fs::canonicalize(path)?);
                if files.len() > MAX_MOD_FILES {
                    return Err(ContentError::InvalidData {
                        reason: "Too many mod archives to inspect. Remove unused mods to continue."
                            .into(),
                    });
                }
            }
        }
    }
    let mut files: Vec<_> = files.into_iter().collect();
    files.sort();
    Ok(files)
}

fn archive_error(path: &Path, reason: &'static str) -> ContentError {
    PackageError::InvalidModArchive {
        file: path
            .file_name()
            .unwrap_or(path.as_os_str())
            .to_string_lossy()
            .into_owned(),
        reason,
    }
    .into()
}

fn ensure_mod_allowed_sync(path: &Path, managed: &HashSet<String>) -> ContentResult<()> {
    let mut file = std::fs::File::open(path)?;
    let metadata = file.metadata()?;
    if !metadata.is_file() || metadata.len() > MAX_ARCHIVE_BYTES {
        return Err(archive_error(path, "archive exceeds the inspection limit"));
    }
    let name =
        futures_lite::future::block_on(inspect_archive(BufReader::new(AllowStdIo::new(&mut file))))
            .map_err(|reason| archive_error(path, reason))?;
    if let Some(name) = name {
        file.rewind()?;
        let mut hash = arcadium_io::Sha1Stream::new();
        let mut buffer = [0; 64 * 1024];
        let mut bounded = Read::take(file, MAX_ARCHIVE_BYTES + 1);
        let mut total = 0;
        loop {
            let read = bounded.read(&mut buffer)?;
            if read == 0 {
                break;
            }
            total += read as u64;
            if total > MAX_ARCHIVE_BYTES {
                return Err(archive_error(path, "archive exceeds the inspection limit"));
            }
            hash.update(&buffer[..read]);
        }
        if !managed.contains(&hash.finish()) {
            return Err(PackageError::OptimizationMod { name }.into());
        }
    }
    Ok(())
}

async fn read_entry<R: AsyncBufRead + AsyncSeek + Unpin>(
    archive: &mut ZipArchiveReader<R>,
    index: usize,
) -> Result<Vec<u8>, &'static str> {
    let file = archive
        .file(index)
        .await
        .map_err(|_| "invalid or oversized metadata entry")?;
    let mut data = Vec::new();
    file.take(MAX_METADATA_BYTES + 1)
        .read_to_end(&mut data)
        .await
        .map_err(|_| "corrupt or oversized metadata entry")?;
    if data.len() as u64 > MAX_METADATA_BYTES {
        return Err("metadata exceeds the inspection limit");
    }
    Ok(data)
}

async fn inspect_archive<R: AsyncBufRead + AsyncSeek + Unpin>(
    reader: R,
) -> Result<Option<&'static str>, &'static str> {
    let options = ZipOptions {
        max_uncompressed_size_per_file: MAX_METADATA_BYTES,
        max_compressed_size_per_file: MAX_METADATA_BYTES,
        ..ZipOptions::untrusted()
    };
    let mut archive = ZipArchiveReader::open_with_options(reader, options)
        .await
        .map_err(|_| "invalid ZIP structure or archive limit exceeded")?;
    let mut selected = Vec::new();
    let mut names = HashSet::new();
    for (index, entry) in archive.cdrs().iter().enumerate() {
        let name = String::from_utf8_lossy(entry.insecure_file_name.as_bytes());
        if METADATA.contains(&name.as_ref()) || OPTIFINE_CLASSES.contains(&name.as_ref()) {
            if !names.insert(name.to_string()) {
                return Err("duplicate mod identity entry");
            }
            selected.push((index, name.into_owned()));
        }
    }
    let mut optifine_classes = 0;
    for (index, name) in selected {
        let bytes = read_entry(&mut archive, index).await?;
        if OPTIFINE_CLASSES.contains(&name.as_str()) {
            if bytes.starts_with(b"\xca\xfe\xba\xbe") {
                optifine_classes += 1;
                if optifine_classes >= 2 {
                    return Ok(Some("OptiFine"));
                }
            }
        } else if let Some(identity) = metadata_identity(&name, &bytes) {
            return Ok(Some(identity));
        }
    }
    Ok(None)
}

fn known_id(id: Option<&str>) -> Option<&'static str> {
    let id = id?.trim();
    IDENTITIES
        .iter()
        .find_map(|&(known, name)| id.eq_ignore_ascii_case(known).then_some(name))
}

fn metadata_identity(name: &str, bytes: &[u8]) -> Option<&'static str> {
    match name {
        "fabric.mod.json" | "quilt.mod.json" | "mcmod.info" => {
            let value: Value = serde_json::from_slice(bytes).ok()?;
            match name {
                "fabric.mod.json" => known_id(value["id"].as_str()),
                "quilt.mod.json" => known_id(value["quilt_loader"]["id"].as_str()),
                _ => {
                    if let Some(name) = known_id(value["modid"].as_str()) {
                        return Some(name);
                    }
                    value
                        .as_array()
                        .or_else(|| value["modList"].as_array())?
                        .iter()
                        .find_map(|entry| known_id(entry["modid"].as_str()))
                }
            }
        }
        "META-INF/mods.toml" | "META-INF/neoforge.mods.toml" => {
            let value: toml::Value = toml::from_str(std::str::from_utf8(bytes).ok()?).ok()?;
            value
                .get("mods")?
                .as_array()?
                .iter()
                .find_map(|entry| known_id(entry.get("modId").and_then(toml::Value::as_str)))
        }
        "META-INF/MANIFEST.MF" => {
            let text = std::str::from_utf8(bytes).ok()?;
            let unfolded = text.replace("\r\n ", "").replace("\n ", "");
            unfolded.lines().find_map(|line| {
                let (key, value) = line.split_once(':')?;
                if !key.eq_ignore_ascii_case("TweakClass") {
                    return None;
                }
                match value.trim() {
                    "optifine.OptiFineTweaker" | "optifine.OptiFineForgeTweaker" => {
                        Some("OptiFine")
                    }
                    "me.guichaguri.betterfps.tweaker.BetterFpsTweaker" => Some("BetterFps"),
                    _ => None,
                }
            })
        }
        _ => None,
    }
}

async fn managed_hashes() -> ContentResult<&'static HashSet<String>> {
    static HASHES: OnceCell<HashSet<String>> = OnceCell::const_new();
    HASHES
        .get_or_try_init(|| async {
            tokio::task::spawn_blocking(load_managed_hashes)
                .await
                .map_err(std::io::Error::other)?
        })
        .await
}

fn load_managed_hashes() -> ContentResult<HashSet<String>> {
    use arcadium_common::distribution;
    let mut hashes: HashSet<_> = distribution::COMPONENTS
        .iter()
        .map(|&(_, hash)| hash.to_ascii_lowercase())
        .collect();
    hashes.insert(distribution::BRANDING_SHA1.to_ascii_lowercase());
    let components: std::collections::HashMap<String, Vec<distribution::ClientComponent>> =
        serde_json::from_slice(distribution::CLIENT_COMPONENTS)?;
    hashes.extend(
        components
            .values()
            .flatten()
            .map(|component| component.sha1.to_ascii_lowercase()),
    );
    let catalog: Value = serde_json::from_slice(distribution::BUNDLES)?;
    let versions = catalog["versions"]
        .as_object()
        .ok_or_else(invalid_distribution)?;
    let bundles: Vec<_> = versions
        .values()
        .map(|version| version.as_object().ok_or_else(invalid_distribution))
        .collect::<ContentResult<Vec<_>>>()?
        .into_iter()
        .flat_map(|loaders| loaders.values())
        .map(|bundles| bundles.as_array().ok_or_else(invalid_distribution))
        .collect::<ContentResult<Vec<_>>>()?
        .into_iter()
        .flatten()
        .collect();
    for bundle in bundles {
        let path = bundle["path"].as_str().ok_or_else(invalid_distribution)?;
        let bytes = distribution::bundle(path).ok_or_else(invalid_distribution)?;
        if arcadium_io::sha1_bytes(bytes)
            != bundle["sha1"].as_str().ok_or_else(invalid_distribution)?
        {
            return Err(invalid_distribution());
        }
        futures_lite::future::block_on(async {
            let mut archive =
                ZipArchiveReader::open_with_options(Cursor::new(bytes), ZipOptions::untrusted())
                    .await
                    .map_err(|_| invalid_distribution())?;
            let index = archive
                .find(b"modrinth.index.json")
                .map_err(|_| invalid_distribution())?
                .next()
                .ok_or_else(invalid_distribution)?;
            let index: Value = serde_json::from_slice(
                &read_entry(&mut archive, index)
                    .await
                    .map_err(|_| invalid_distribution())?,
            )?;
            for file in index["files"].as_array().ok_or_else(invalid_distribution)? {
                if file["path"].as_str().is_some_and(|path| {
                    path.starts_with("mods/") && path.to_ascii_lowercase().ends_with(".jar")
                }) {
                    let hash = file["hashes"]["sha1"]
                        .as_str()
                        .filter(|hash| {
                            hash.len() == 40 && hash.bytes().all(|b| b.is_ascii_hexdigit())
                        })
                        .ok_or_else(invalid_distribution)?;
                    hashes.insert(hash.to_ascii_lowercase());
                }
            }
            ContentResult::Ok(())
        })?;
    }
    for bytes in [
        distribution::MODERN_12111_LOCK,
        distribution::MODERN_262_LOCK,
    ] {
        let lock: Value = serde_json::from_slice(bytes)?;
        let optional = &lock["optionalNvidium"];
        for entry in std::iter::once(optional).chain(
            optional["compatibilityMods"]
                .as_array()
                .into_iter()
                .flatten(),
        ) {
            let hash = entry["sha1"]
                .as_str()
                .filter(|hash| hash.len() == 40 && hash.bytes().all(|b| b.is_ascii_hexdigit()))
                .ok_or_else(invalid_distribution)?;
            hashes.insert(hash.to_ascii_lowercase());
        }
    }
    Ok(hashes)
}

fn invalid_distribution() -> ContentError {
    ContentError::InvalidData {
        reason: "Arcadium's bundled mod manifest is damaged. Reinstall the launcher.".into(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use arcadium_io::testing::ScratchDir;

    async fn jar(path: &Path, entries: &[(&str, &[u8])]) {
        if let Some(parent) = path.parent() {
            tokio::fs::create_dir_all(parent).await.unwrap();
        }
        let file = tokio::fs::File::create(path).await.unwrap();
        let mut writer = async_zip::tokio::write::ZipFileWriter::with_tokio(file);
        for &(name, bytes) in entries {
            let entry =
                async_zip::ZipEntryBuilder::new(name.into(), async_zip::Compression::Stored);
            writer.write_entry_whole(entry, bytes).await.unwrap();
        }
        let mut output = writer.close().await.unwrap();
        futures_lite::io::AsyncWriteExt::flush(&mut output)
            .await
            .unwrap();
    }

    fn assert_blocked(result: ContentResult<()>, expected: &'static str) {
        match result {
            Err(ContentError::Package(PackageError::OptimizationMod { name })) => {
                assert_eq!(name, expected);
            }
            other => panic!("Expected {expected} rejection, got {other:?}"),
        }
    }

    #[tokio::test]
    async fn renamed_optifine_is_identified_without_mod_metadata() {
        let dir = ScratchDir::new("optimizer-renamed");
        let file = dir.join("ordinary-mod.jar");
        jar(
            &file,
            &[
                (
                    "optifine/OptiFineTweaker.class",
                    b"\xca\xfe\xba\xbe\0\0\0\x34",
                ),
                (
                    "optifine/OptiFineClassTransformer.class",
                    b"\xca\xfe\xba\xbe\0\0\0\x34",
                ),
            ],
        )
        .await;
        assert_blocked(ensure_mod_allowed(&file).await, "OptiFine");
    }

    #[tokio::test]
    async fn explicit_tweakers_are_detected_in_folded_manifests() {
        let dir = ScratchDir::new("optimizer-tweaker");
        let file = dir.join("renamed.jar");
        jar(
            &file,
            &[(
                "META-INF/MANIFEST.MF",
                b"Manifest-Version: 1.0\r\nTweakClass: optifine.Opti\r\n FineTweaker\r\n",
            )],
        )
        .await;
        assert_blocked(ensure_mod_allowed(&file).await, "OptiFine");
        jar(
            &file,
            &[(
                "META-INF/MANIFEST.MF",
                b"TweakClass: me.guichaguri.betterfps.tweaker.BetterFpsTweaker\n",
            )],
        )
        .await;
        assert_blocked(ensure_mod_allowed(&file).await, "BetterFps");
    }

    #[tokio::test]
    async fn ordinary_coremods_mixins_and_fps_huds_are_allowed() {
        let dir = ScratchDir::new("optimizer-ordinary");
        let file = dir.join("OptiFine-Sodium-BetterFps.jar");
        jar(
            &file,
            &[
                (
                    "fabric.mod.json",
                    br#"{"id":"fpshud","name":"Better FPS HUD","depends":{"sodium":"*"}}"#,
                ),
                (
                    "mcmod.info",
                    br#"[{"modid":"samplecoremod","description":"OptiFine integration"}]"#,
                ),
                (
                    "META-INF/MANIFEST.MF",
                    b"FMLCorePlugin: example.CorePlugin\nMixinConfigs: mixins.sample.json\n",
                ),
                (
                    "optifine/OptiFineTweaker.class",
                    b"\xca\xfe\xba\xbe\0\0\0\x34",
                ),
            ],
        )
        .await;
        ensure_mod_allowed(&file).await.unwrap();
    }

    #[tokio::test]
    async fn declared_ids_are_exact_and_checked_across_metadata_formats() {
        let dir = ScratchDir::new("optimizer-metadata");
        let file = dir.join("anything.jar");
        let entries: &[(&str, &[u8], &str)] = &[
            ("fabric.mod.json", br#"{"id":"sodium"}"#, "Sodium"),
            (
                "quilt.mod.json",
                br#"{"quilt_loader":{"id":"lithium"}}"#,
                "Lithium",
            ),
            (
                "mcmod.info",
                br#"[{"modid":"sample"},{"modid":"FoamFix"}]"#,
                "FoamFix",
            ),
            (
                "mcmod.info",
                br#"{"modList":[{"modid":"betterfps"}]}"#,
                "BetterFps",
            ),
            ("mcmod.info", br#"[{"modid":"patcher"}]"#, "Patcher"),
            ("mcmod.info", br#"[{"modid":"memoryfix"}]"#, "MemoryFix"),
            (
                "fabric.mod.json",
                br#"{"id":"entityculling"}"#,
                "EntityCulling",
            ),
            (
                "META-INF/mods.toml",
                b"[[mods]]\nmodId = 'embeddium'\n",
                "Embeddium",
            ),
            (
                "META-INF/neoforge.mods.toml",
                b"[[mods]]\nmodId = 'ferritecore'\n",
                "FerriteCore",
            ),
        ];
        for &(metadata, bytes, expected) in entries {
            jar(&file, &[(metadata, bytes)]).await;
            assert_blocked(ensure_mod_allowed(&file).await, expected);
        }
        for id in [
            "sodiumoptionsapi",
            "sodium_extra",
            "patcherutils",
            "modernfix",
            "vanillafix",
            "cera",
            "sciophobia",
        ] {
            let json = format!(r#"{{"id":"{id}"}}"#);
            jar(&file, &[("fabric.mod.json", json.as_bytes())]).await;
            ensure_mod_allowed(&file).await.unwrap();
        }
    }

    #[tokio::test]
    async fn managed_exemption_requires_the_actual_archive_hash() {
        let dir = ScratchDir::new("optimizer-managed");
        let file = dir.join("ArcadiumCore-1.8.9.jar");
        jar(&file, &[("fabric.mod.json", br#"{"id":"argentum"}"#)]).await;
        let hashes = HashSet::from([arcadium_io::sha1_file(&file).await.unwrap()]);
        ensure_mod_allowed_sync(&file, &hashes).unwrap();
        jar(
            &file,
            &[(
                "fabric.mod.json",
                br#"{"id":"argentum","version":"tampered"}"#,
            )],
        )
        .await;
        assert_blocked(ensure_mod_allowed_sync(&file, &hashes), "Argentum");
        assert_blocked(ensure_mod_allowed(&file).await, "Argentum");
    }

    #[tokio::test]
    async fn embedded_managed_manifest_is_valid_and_does_not_trust_file_names() {
        let hashes = managed_hashes().await.unwrap();
        assert!(hashes.len() > 50);
        assert!(hashes.contains(arcadium_common::distribution::BRANDING_SHA1));
        assert!(!hashes.contains("ArcadiumCore-1.8.9.jar"));
        let lock: Value =
            serde_json::from_slice(arcadium_common::distribution::MODERN_12111_LOCK).unwrap();
        for entry in lock["optionalNvidium"]["compatibilityMods"]
            .as_array()
            .unwrap()
        {
            assert!(hashes.contains(entry["sha1"].as_str().unwrap()));
            assert!(!hashes.contains(entry["file"].as_str().unwrap()));
        }
    }

    #[tokio::test]
    async fn corrupt_duplicate_and_oversized_metadata_fail_closed() {
        let dir = ScratchDir::new("optimizer-invalid");
        let file = dir.join("invalid.jar");
        tokio::fs::write(&file, b"not a ZIP").await.unwrap();
        assert!(matches!(
            ensure_mod_allowed(&file).await,
            Err(ContentError::Package(
                PackageError::InvalidModArchive { .. }
            ))
        ));
        jar(
            &file,
            &[("fabric.mod.json", b"{}"), ("fabric.mod.json", b"{}")],
        )
        .await;
        assert!(ensure_mod_allowed(&file).await.is_err());
        jar(
            &file,
            &[(
                "fabric.mod.json",
                &vec![b' '; MAX_METADATA_BYTES as usize + 1],
            )],
        )
        .await;
        assert!(ensure_mod_allowed(&file).await.is_err());
        let file = std::fs::File::create(&file).unwrap();
        file.set_len(MAX_ARCHIVE_BYTES + 1).unwrap();
        assert!(ensure_mod_allowed(&dir.join("invalid.jar")).await.is_err());
    }

    #[tokio::test]
    async fn launch_inventory_scans_active_roots_and_only_the_current_version_folder() {
        let dir = ScratchDir::new("optimizer-directories");
        let root = dir.join("mods");
        let blocked = [("fabric.mod.json", br#"{"id":"optifine"}"#.as_slice())];
        for name in [
            "disabled.jar.disabled",
            "ignored.disabled",
            "1.12.2/other.jar",
            "nested/other.jar",
        ] {
            jar(&root.join(name), &blocked).await;
        }
        jar(
            &root.join("ordinary.JAR"),
            &[("mcmod.info", b"[{\"modid\":\"ordinary\"}]")],
        )
        .await;
        check_mod_directories(&[root.clone(), root.join(".")])
            .await
            .unwrap();
        let redirected = dir.join("custom-native-mods");
        jar(&redirected.join("1.8.9/renamed.JAR"), &blocked).await;
        assert_blocked(check_mod_directories(&[root, redirected]).await, "OptiFine");
    }

    #[tokio::test]
    async fn repeated_launch_validation_rechecks_bytes_with_unchanged_file_metadata() {
        let dir = ScratchDir::new("optimizer-recheck");
        let root = dir.join("mods");
        let file = root.join("ordinary.jar");
        jar(&file, &[("fabric.mod.json", br#"{"id":"frames"}"#)]).await;
        let before = std::fs::metadata(&file).unwrap();
        check_mod_directories(&[root.clone()]).await.unwrap();
        jar(&file, &[("fabric.mod.json", br#"{"id":"sodium"}"#)]).await;
        std::fs::OpenOptions::new()
            .write(true)
            .open(&file)
            .unwrap()
            .set_times(std::fs::FileTimes::new().set_modified(before.modified().unwrap()))
            .unwrap();
        assert_eq!(before.len(), std::fs::metadata(&file).unwrap().len());
        assert_blocked(check_mod_directories(&[root]).await, "Sodium");
    }

    #[tokio::test]
    async fn batch_validation_keeps_deterministic_first_error() {
        let dir = ScratchDir::new("optimizer-order");
        let root = dir.join("mods");
        jar(&root.join("z.jar"), &[("fabric.mod.json", br#"{"id":"sodium"}"#)]).await;
        jar(&root.join("a.jar"), &[("mcmod.info", br#"[{"modid":"foamfix"}]"#)]).await;
        assert_blocked(check_mod_directories(&[root]).await, "FoamFix");
    }
}
