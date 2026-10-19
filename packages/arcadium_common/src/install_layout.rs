use std::path::{Path, PathBuf};

pub fn resources(executable: &Path) -> PathBuf {
    let directory = executable.parent().unwrap_or_else(|| Path::new("."));
    if directory.file_name().is_some_and(|name| name == "MacOS")
        && directory
            .parent()
            .is_some_and(|contents| contents.file_name().is_some_and(|name| name == "Contents"))
    {
        directory.parent().unwrap().join("Resources")
    } else {
        directory.to_path_buf()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn components_follow_the_native_installation_layout() {
        assert_eq!(
            resources(Path::new(
                "/Applications/Arcadium Client.app/Contents/MacOS/Arcadium"
            )),
            PathBuf::from("/Applications/Arcadium Client.app/Contents/Resources")
        );
        assert_eq!(
            resources(Path::new("C:/Programs/Arcadium/Arcadium.exe")),
            PathBuf::from("C:/Programs/Arcadium")
        );
    }
}
