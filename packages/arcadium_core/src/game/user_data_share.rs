use std::collections::{BTreeMap, BTreeSet};
use std::fs;
use std::io;
use std::path::{Path, PathBuf};

const JOURNAL: &str = ".arcadium-shared-game-data.json";
const DIRECTORIES: &[&str] = &[
    "saves",
    "screenshots",
    "resourcepacks",
    "shaderpacks",
    "server-resource-packs",
    "schematics",
    "recordings",
    "replay_recordings",
];
const FILES: &[&str] = &[
    "options.txt",
    "optionsof.txt",
    "optionsshaders.txt",
    "servers.dat",
    "servers.dat_old",
    "usercache.json",
    "realms_persistence.json",
    "hotbar.nbt",
];

#[derive(Default, serde::Serialize, serde::Deserialize)]
struct Imports {
    primary: PathBuf,
    entries: BTreeMap<String, String>,
    #[serde(default)]
    completed: BTreeSet<String>,
}

fn save_imports(native: &Path, journal: &Path, imports: &Imports) -> io::Result<()> {
    let bytes = serde_json::to_vec_pretty(imports).map_err(io::Error::other)?;
    let temporary = native.join(format!(".arcadium-import-record-{}", uuid::Uuid::new_v4()));
    fs::write(&temporary, bytes)?;
    fs::rename(temporary, journal)
}

fn copy_entry(source: &Path, target: &Path) -> io::Result<()> {
    let metadata = fs::symlink_metadata(source)?;
    if metadata.file_type().is_symlink() {
        return Err(io::Error::other(format!(
            "A user data path is a link: {}",
            source.display()
        )));
    }
    if metadata.is_file() {
        fs::copy(source, target)?;
    } else if metadata.is_dir() {
        fs::create_dir(target)?;
        for child in fs::read_dir(source)? {
            let child = child?;
            copy_entry(&child.path(), &target.join(child.file_name()))?;
        }
    } else {
        return Err(io::Error::other("Unsupported user data file type."));
    }
    Ok(())
}

fn import_user_data(native: &Path, primary: &Path) -> io::Result<()> {
    fs::create_dir_all(native)?;
    fs::create_dir_all(primary)?;
    let native = fs::canonicalize(native)?;
    let primary = fs::canonicalize(primary)?;
    if native == primary {
        return Ok(());
    }
    if native.starts_with(&primary) || primary.starts_with(&native) {
        return Err(io::Error::other("Minecraft user data directories overlap."));
    }
    let journal = native.join(JOURNAL);
    if journal
        .symlink_metadata()
        .is_ok_and(|value| value.file_type().is_symlink())
    {
        return Err(io::Error::other(
            "The user data migration record is a link.",
        ));
    }
    let mut imports: Imports = match fs::read(&journal) {
        Ok(bytes) => serde_json::from_slice(&bytes).map_err(io::Error::other)?,
        Err(error) if error.kind() == io::ErrorKind::NotFound => Imports::default(),
        Err(error) => return Err(error),
    };
    if imports.primary != primary {
        imports = Imports {
            primary: primary.clone(),
            ..Imports::default()
        };
    }
    let mut entries = Vec::new();
    for directory in DIRECTORIES {
        let source = native.join(directory);
        match fs::symlink_metadata(&source) {
            Ok(metadata) if metadata.file_type().is_symlink() => continue,
            Ok(metadata) if metadata.is_dir() => {
                let target = primary.join(directory);
                fs::create_dir_all(&target)?;
                if !fs::canonicalize(&target)?.starts_with(&primary) {
                    return Err(io::Error::other(
                        "Shared user data points outside the Minecraft directory.",
                    ));
                }
                for entry in fs::read_dir(source)? {
                    entries.push(PathBuf::from(directory).join(entry?.file_name()));
                }
            }
            Ok(_) => {
                return Err(io::Error::other(
                    "A user data directory is not a directory.",
                ));
            }
            Err(error) if error.kind() == io::ErrorKind::NotFound => {}
            Err(error) => return Err(error),
        }
    }
    entries.extend(
        FILES
            .iter()
            .map(|name| PathBuf::from(*name))
            .filter(|name| native.join(name).exists()),
    );
    entries.sort();
    for relative in entries {
        let key = relative.to_string_lossy().replace('\\', "/");
        if imports.completed.contains(&key) {
            continue;
        }
        let target_relative = if let Some(previous) = imports.entries.get(&key) {
            let saved = PathBuf::from(previous);
            if saved.is_absolute()
                || saved
                    .components()
                    .any(|part| !matches!(part, std::path::Component::Normal(_)))
            {
                return Err(io::Error::other("Invalid user data migration destination."));
            }
            if primary.join(&saved).exists() {
                imports.completed.insert(key.clone());
                save_imports(&native, &journal, &imports)?;
                continue;
            }
            saved
        } else {
            let mut destination = relative.clone();
            if primary.join(&destination).exists() {
                if relative
                    .parent()
                    .is_none_or(|parent| parent.as_os_str().is_empty())
                {
                    imports.entries.insert(key.clone(), key.clone());
                    imports.completed.insert(key.clone());
                    save_imports(&native, &journal, &imports)?;
                    continue;
                }
                let name = relative.file_name().unwrap().to_string_lossy();
                let is_file = native.join(&relative).is_file();
                let mut index = 1;
                loop {
                    let suffix = if index == 1 {
                        String::new()
                    } else {
                        format!(" {index}")
                    };
                    let adjusted = if is_file {
                        let stem = relative.file_stem().unwrap().to_string_lossy();
                        let extension = relative
                            .extension()
                            .map(|value| format!(".{}", value.to_string_lossy()))
                            .unwrap_or_default();
                        format!("{stem} (1.7.10{suffix}){extension}")
                    } else {
                        format!("{name} (1.7.10{suffix})")
                    };
                    destination = relative.with_file_name(adjusted);
                    if !primary.join(&destination).exists() {
                        break;
                    }
                    index += 1;
                }
            }
            imports.entries.insert(
                key.clone(),
                destination.to_string_lossy().replace('\\', "/"),
            );
            save_imports(&native, &journal, &imports)?;
            destination
        };
        let staging = primary.join(format!(".arcadium-import-{}", uuid::Uuid::new_v4()));
        if let Err(error) = copy_entry(&native.join(&relative), &staging) {
            if staging.is_dir() {
                let _ = fs::remove_dir_all(&staging);
            } else {
                let _ = fs::remove_file(&staging);
            }
            return Err(error);
        }
        fs::rename(&staging, primary.join(target_relative))?;
        imports.completed.insert(key);
        save_imports(&native, &journal, &imports)?;
    }
    Ok(())
}

pub async fn share_user_data(native: &Path, primary: &Path) -> io::Result<()> {
    let native = native.to_owned();
    let primary = primary.to_owned();
    tokio::task::spawn_blocking(move || import_user_data(&native, &primary))
        .await
        .map_err(io::Error::other)?
}

#[cfg(test)]
mod tests {
    use super::*;

    fn write(root: &Path, name: &str, bytes: &[u8]) {
        let file = root.join(name);
        fs::create_dir_all(file.parent().unwrap()).unwrap();
        fs::write(file, bytes).unwrap();
    }

    #[test]
    fn imports_worlds_and_user_files_without_overwriting_the_primary_or_runtime() {
        let temp = arcadium_io::testing::ScratchDir::new("shared_user_data");
        let native = temp.path().join("native");
        let primary = temp.path().join("primary");
        for (name, bytes) in [
            ("saves/Native/level.dat", b"native world".as_slice()),
            ("saves/Same/region/r.0.0.mca", b"native region"),
            ("screenshots/same.png", b"native screenshot"),
            ("resourcepacks/pack.zip", b"native pack"),
            ("options.txt", b"native controls"),
            ("servers.dat", b"native servers"),
            ("mods/native.jar", b"private mod"),
            ("oneconfig/.skiko/cache", b"private cache"),
        ] {
            write(&native, name, bytes);
        }
        write(&primary, "saves/Same/region/r.0.0.mca", b"primary region");
        write(&primary, "screenshots/same.png", b"primary screenshot");
        write(&primary, "options.txt", b"primary controls");
        import_user_data(&native, &primary).unwrap();
        assert_eq!(
            fs::read(primary.join("saves/Native/level.dat")).unwrap(),
            b"native world"
        );
        assert_eq!(
            fs::read(primary.join("saves/Same/region/r.0.0.mca")).unwrap(),
            b"primary region"
        );
        assert_eq!(
            fs::read(primary.join("saves/Same (1.7.10)/region/r.0.0.mca")).unwrap(),
            b"native region"
        );
        assert_eq!(
            fs::read(primary.join("screenshots/same (1.7.10).png")).unwrap(),
            b"native screenshot"
        );
        assert_eq!(
            fs::read(primary.join("options.txt")).unwrap(),
            b"primary controls"
        );
        assert_eq!(
            fs::read(primary.join("servers.dat")).unwrap(),
            b"native servers"
        );
        assert!(!primary.join("mods").exists());
        assert!(!primary.join("oneconfig").exists());
        assert_eq!(
            fs::read(native.join("saves/Native/level.dat")).unwrap(),
            b"native world"
        );
        import_user_data(&native, &primary).unwrap();
        assert!(!primary.join("saves/Same (1.7.10 2)").exists());
        assert!(!primary.join("screenshots/same (1.7.10 2).png").exists());
        let deleted = primary.join("saves/Native");
        assert!(deleted.starts_with(temp.path()));
        fs::remove_dir_all(&deleted).unwrap();
        fs::remove_file(primary.join("servers.dat")).unwrap();
        import_user_data(&native, &primary).unwrap();
        assert!(
            !deleted.exists(),
            "A deleted shared world must not be restored from its old native copy"
        );
        assert!(
            !primary.join("servers.dat").exists(),
            "Reset server data must not be restored from the old native copy"
        );
    }

    #[tokio::test]
    async fn an_external_destination_link_is_rejected_without_copying_into_it() {
        let temp = arcadium_io::testing::ScratchDir::new("shared_user_data_links");
        let native = temp.path().join("native");
        let primary = temp.path().join("primary");
        let outside = temp.path().join("outside");
        write(&native, "saves/World/level.dat", b"world");
        fs::create_dir_all(&primary).unwrap();
        fs::create_dir_all(&outside).unwrap();
        arcadium_io::symlink_dir(&outside, primary.join("saves"))
            .await
            .unwrap();
        assert!(import_user_data(&native, &primary).is_err());
        assert_eq!(fs::read_dir(outside).unwrap().count(), 0);
        assert_eq!(
            fs::read(native.join("saves/World/level.dat")).unwrap(),
            b"world"
        );
    }

    #[test]
    fn invalid_journal_paths_cannot_escape_the_shared_directory() {
        let temp = arcadium_io::testing::ScratchDir::new("shared_user_data_journal");
        let native = temp.path().join("native");
        let primary = temp.path().join("primary");
        write(&native, "screenshots/test.png", b"screenshot");
        fs::create_dir_all(&primary).unwrap();
        let journal = Imports {
            primary: fs::canonicalize(&primary).unwrap(),
            entries: BTreeMap::from([(
                "screenshots/test.png".to_owned(),
                "../outside.png".to_owned(),
            )]),
            ..Imports::default()
        };
        fs::write(native.join(JOURNAL), serde_json::to_vec(&journal).unwrap()).unwrap();
        assert!(import_user_data(&native, &primary).is_err());
        assert!(!temp.path().join("outside.png").exists());
    }

    #[test]
    fn runtime_and_user_directories_cannot_overlap() {
        let temp = arcadium_io::testing::ScratchDir::new("shared_user_data_overlap");
        assert!(import_user_data(temp.path(), &temp.path().join("nested")).is_err());
        assert!(import_user_data(temp.path(), temp.path()).is_ok());
    }
}
