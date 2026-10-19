use std::collections::HashSet;
use std::fs;
use std::io::Read;
use std::path::{Component, Path, PathBuf};

use anyhow::{Context, Result, ensure};
use flate2::read::GzDecoder;

pub const APP_NAME: &str = "Arcadium Client.app";
const MAX_UNPACKED_BYTES: u64 = 1024 * 1024 * 1024;

pub fn app_bundle(executable: &Path) -> Option<PathBuf> {
    let macos = executable.parent()?;
    let contents = macos.parent()?;
    let app = contents.parent()?;
    (macos.file_name()? == "MacOS"
        && contents.file_name()? == "Contents"
        && app.extension()? == "app")
        .then(|| app.to_path_buf())
}

pub fn extract(archive: &Path, directory: &Path) -> Result<PathBuf> {
    ensure!(
        !directory.exists(),
        "Update extraction directory already exists"
    );
    fs::create_dir_all(directory)?;
    let result = extract_inner(archive, directory);
    if result.is_err() {
        let _ = fs::remove_dir_all(directory);
    }
    result
}

fn extract_inner(archive: &Path, directory: &Path) -> Result<PathBuf> {
    let decoder = GzDecoder::new(fs::File::open(archive)?);
    let mut archive = tar::Archive::new(decoder);
    let mut paths = HashSet::new();
    let mut total = 0u64;
    for entry in archive.entries()? {
        let mut entry = entry?;
        let path = entry.path()?.into_owned();
        ensure!(
            path.components()
                .all(|part| matches!(part, Component::Normal(_))),
            "Invalid update path"
        );
        ensure!(
            path.starts_with(APP_NAME) && !path.to_string_lossy().contains('\\'),
            "Update leaves the app bundle"
        );
        ensure!(
            paths.insert(path.clone()) && paths.len() <= 10_000,
            "Duplicate or excessive update entries"
        );
        let kind = entry.header().entry_type();
        ensure!(
            kind.is_file() || kind.is_dir(),
            "Update contains a link or special file"
        );
        total = total
            .checked_add(entry.size())
            .context("Update size overflow")?;
        ensure!(
            total <= MAX_UNPACKED_BYTES,
            "Update is too large after extraction"
        );
        let destination = directory.join(&path);
        if let Some(parent) = destination.parent() {
            fs::create_dir_all(parent)?;
        }
        entry.unpack(&destination)?;
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            let mode = entry.header().mode()? & 0o777;
            fs::set_permissions(directory.join(&path), fs::Permissions::from_mode(mode))?;
        }
    }
    let mut decoder = archive.into_inner();
    let mut trailing = Vec::new();
    decoder
        .by_ref()
        .take(64 * 1024)
        .read_to_end(&mut trailing)?;
    ensure!(
        trailing.len() < 64 * 1024 && trailing.iter().all(|byte| *byte == 0),
        "Invalid update archive trailer"
    );
    let app = directory.join(APP_NAME);
    ensure!(
        app.join("Contents/Info.plist").is_file(),
        "Update has no app metadata"
    );
    ensure!(
        app.join("Contents/MacOS/Arcadium").is_file(),
        "Update has no launcher executable"
    );
    Ok(app)
}

#[cfg(test)]
mod tests {
    use super::*;
    use flate2::{Compression, write::GzEncoder};

    fn fixture(link: bool) -> (PathBuf, PathBuf) {
        static NEXT: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);
        let sequence = NEXT.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
        let root = std::env::temp_dir().join(format!(
            "arcadium-bundle-{}-{}-{sequence}",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        fs::create_dir_all(&root).unwrap();
        let path = root.join("app.tar.gz");
        let encoder = GzEncoder::new(fs::File::create(&path).unwrap(), Compression::default());
        let mut tar = tar::Builder::new(encoder);
        for name in ["Contents/Info.plist", "Contents/MacOS/Arcadium"] {
            let mut header = tar::Header::new_gnu();
            header.set_size(4);
            header.set_mode(0o755);
            header.set_cksum();
            tar.append_data(&mut header, format!("{APP_NAME}/{name}"), &b"test"[..])
                .unwrap();
        }
        if link {
            let mut header = tar::Header::new_gnu();
            header.set_entry_type(tar::EntryType::Symlink);
            header.set_size(0);
            header.set_mode(0o755);
            header.set_link_name("/tmp/outside").unwrap();
            header.set_cksum();
            tar.append_data(&mut header, format!("{APP_NAME}/escape"), std::io::empty())
                .unwrap();
        }
        tar.into_inner().unwrap().finish().unwrap();
        (root, path)
    }

    #[test]
    fn a_bundle_extracts_and_preserves_its_executable() {
        let (root, archive) = fixture(false);
        let app = extract(&archive, &root.join("unpacked")).unwrap();
        assert_eq!(
            fs::read(app.join("Contents/MacOS/Arcadium")).unwrap(),
            b"test"
        );
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            assert_eq!(
                fs::metadata(app.join("Contents/MacOS/Arcadium"))
                    .unwrap()
                    .permissions()
                    .mode()
                    & 0o777,
                0o755
            );
        }
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn symlink_entries_are_rejected_and_partial_extraction_is_removed() {
        let (root, archive) = fixture(true);
        let unpacked = root.join("unpacked");
        assert!(extract(&archive, &unpacked).is_err());
        assert!(!unpacked.exists());
        fs::remove_dir_all(root).unwrap();
    }
}
