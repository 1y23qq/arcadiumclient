use std::path::{Path, PathBuf};

use crate::data::java_executable_relative_path;
use crate::error::{JavaError, JavaResult};
use arcadium_common::constants::JAVA_BIN;

#[tracing::instrument(level = "debug", skip_all)]
pub fn resolve_java_executable(selection: impl AsRef<Path>) -> JavaResult<PathBuf> {
    let selection = selection.as_ref();

    if selection.is_file() {
        return validate_executable(selection);
    }

    let relative = java_executable_relative_path();
    let candidates = [
        selection.join(&relative),
        selection.join(JAVA_BIN),
        selection.join("bin").join(JAVA_BIN),
        selection.join("Contents/Home/bin").join(JAVA_BIN),
    ];

    for candidate in candidates {
        if candidate.is_file() {
            return validate_executable(&candidate);
        }
    }

    if let Ok(entries) = std::fs::read_dir(selection) {
        for entry in entries.flatten() {
            let path = entry.path();
            if !path.is_dir() {
                continue;
            }

            let nested = path.join(&relative);
            if nested.is_file() {
                return validate_executable(&nested);
            }
            let bundled = path.join("Contents/Home/bin").join(JAVA_BIN);
            if bundled.is_file() {
                return validate_executable(&bundled);
            }
        }
    }

    tracing::warn!(path = %selection.display(), "no Java executable found in selection");

    Err(JavaError::InvalidJavaPath {
        path: selection.display().to_string(),
    })
}

fn validate_executable(path: &Path) -> JavaResult<PathBuf> {
    let file_name = path
        .file_name()
        .and_then(|name| name.to_str())
        .unwrap_or_default();

    if file_name != JAVA_BIN && file_name != "java.exe" && file_name != "javaw.exe" {
        return Err(JavaError::InvalidJavaPath {
            path: path.display().to_string(),
        });
    }

    Ok(path.to_path_buf())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_mac_jdk_bundle_home_and_download_folder_resolve_to_the_same_java() {
        let stamp = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_nanos();
        let directory = std::env::temp_dir().join(format!(
            "arcadium-java-bundle-{}-{stamp}",
            std::process::id()
        ));
        let bundle = directory.join("Test.jdk");
        let home = bundle.join("Contents/Home");
        let executable = home.join("bin").join(JAVA_BIN);
        std::fs::create_dir_all(executable.parent().unwrap()).unwrap();
        std::fs::write(&executable, b"test executable").unwrap();
        assert_eq!(resolve_java_executable(&bundle).unwrap(), executable);
        assert_eq!(resolve_java_executable(&home).unwrap(), executable);
        assert_eq!(resolve_java_executable(&directory).unwrap(), executable);
        std::fs::remove_dir_all(directory).unwrap();
    }
}
