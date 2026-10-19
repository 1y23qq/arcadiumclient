use std::ffi::OsString;
use std::path::Path;
use sysinfo::{ProcessRefreshKind, ProcessesToUpdate, System, UpdateKind};

fn owns_helper(expected: &Path, executable: Option<&Path>, arguments: &[OsString]) -> bool {
    executable.is_some_and(|path| dunce::canonicalize(path).ok().as_deref() == Some(expected))
        && arguments
            .iter()
            .any(|arg| arg == "--discord-game" || arg == "--account-service")
}

fn using(executable: &Path) -> bool {
    let Ok(expected) = dunce::canonicalize(executable) else {
        return false;
    };
    let mut system = System::new();
    system.refresh_processes_specifics(
        ProcessesToUpdate::All,
        true,
        ProcessRefreshKind::nothing()
            .with_exe(UpdateKind::OnlyIfNotSet)
            .with_cmd(UpdateKind::OnlyIfNotSet),
    );
    system.processes().iter().any(|(pid, process)| {
        pid.as_u32() != std::process::id() && owns_helper(&expected, process.exe(), process.cmd())
    })
}

pub(super) fn active() -> bool {
    std::env::current_exe()
        .ok()
        .is_some_and(|path| using(&path))
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn another_installation_or_ordinary_launcher_does_not_block_updates() {
        let executable = std::env::current_exe().unwrap();
        let expected = dunce::canonicalize(&executable).unwrap();
        assert!(owns_helper(
            &expected,
            Some(&executable),
            &["Arcadium.exe".into(), "--discord-game".into()]
        ));
        assert!(owns_helper(
            &expected,
            Some(&executable),
            &["Arcadium.exe".into(), "--account-service".into()]
        ));
        assert!(!owns_helper(
            &expected,
            Some(&executable),
            &["Arcadium.exe".into()]
        ));
        assert!(!owns_helper(
            &expected,
            Some(Path::new("different-install/Arcadium.exe")),
            &["--discord-game".into()]
        ));
    }

    #[test]
    #[ignore = "requires ARCADIUM_TEST_EXE pointing to a built native launcher"]
    fn live_account_helper_defers_replacement_only_until_it_exits() {
        use std::io::{BufRead, Write};
        use std::process::{Command, Stdio};
        let executable = std::path::PathBuf::from(std::env::var_os("ARCADIUM_TEST_EXE").unwrap());
        let profile = std::env::temp_dir().join(format!(
            "arcadium-update-helper-test-{}",
            uuid::Uuid::new_v4()
        ));
        let mut command = Command::new(&executable);
        command
            .arg("--account-service")
            .env("ARCADIUM_DATA_DIR", &profile)
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::null());
        arcadium_common::process::no_window(&mut command);
        let mut child = command.spawn().unwrap();
        let mut input = child.stdin.take().unwrap();
        input.write_all(b"{\"id\":1,\"op\":\"list\"}\n").unwrap();
        let mut reader = std::io::BufReader::new(child.stdout.take().unwrap());
        let mut response = String::new();
        reader.read_line(&mut response).unwrap();
        assert!(response.contains("\"ok\":true"));
        let detected = using(&executable);
        input
            .write_all(b"{\"id\":2,\"op\":\"shutdown\"}\n")
            .unwrap();
        drop(input);
        assert!(child.wait().unwrap().success());
        assert!(detected);
        assert!(!using(&executable));
        if profile.exists() {
            std::fs::remove_dir_all(profile).unwrap();
        }
    }
}
