use std::path::Path;
use std::time::Duration;

use anyhow::{Context, Result, ensure};
use arcadium_update::{UpdatePlatform, bundle};

pub fn installed(executable: &Path) -> bool {
    bundle::app_bundle(executable).is_some_and(|app| app.join("Contents/Info.plist").is_file())
}

pub async fn install(
    archive: &Path,
    directory: &Path,
    version: &str,
    platform: UpdatePlatform,
) -> Result<()> {
    let executable = std::env::current_exe()?.canonicalize()?;
    let target =
        bundle::app_bundle(&executable).context("Launcher is not inside a macOS app bundle")?;
    let archive = archive.to_path_buf();
    let unpacked = directory.join("unpacked");
    let staged =
        tokio::task::spawn_blocking(move || bundle::extract(&archive, &unpacked)).await??;
    let script = directory.join("apply-update.sh");
    tokio::fs::write(&script, include_str!("macos_apply.sh")).await?;
    let ready = directory.join("installer-ready");
    let arch = match platform {
        UpdatePlatform::MacArm64 => "arm64",
        UpdatePlatform::MacX64 => "x86_64",
        _ => anyhow::bail!("Invalid macOS update platform"),
    };
    let mut helper = std::process::Command::new("/bin/sh")
        .arg(&script)
        .args([std::process::id().to_string().as_str()])
        .arg(&staged)
        .arg(&target)
        .arg(directory)
        .arg(version)
        .arg(arch)
        .env(
            "ARCADIUM_RESTART_ARGS",
            serde_json::to_string(&std::env::args().skip(1).collect::<Vec<_>>())?,
        )
        .stdin(std::process::Stdio::null())
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null())
        .spawn()?;
    let deadline = tokio::time::Instant::now() + Duration::from_secs(60);
    while !ready.is_file() {
        ensure!(
            helper.try_wait()?.is_none(),
            "Mac updater could not prepare the new application"
        );
        if tokio::time::Instant::now() >= deadline {
            let _ = helper.kill();
            anyhow::bail!("Mac updater did not acknowledge the update handoff");
        }
        tokio::time::sleep(Duration::from_millis(100)).await;
    }
    std::process::exit(0);
}
