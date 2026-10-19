use std::sync::{
    OnceLock,
    atomic::{AtomicBool, Ordering},
};
use std::time::Duration;

use arcadium_events::EventBus;
use arcadium_update::UpdateClient;
use tokio::sync::watch;

#[cfg(windows)]
mod helpers;
#[cfg(target_os = "macos")]
mod macos;

pub const UPDATE_CHOICE_INSTALL: &str = "update.install";
static BUSY: AtomicBool = AtomicBool::new(false);
static STATUS: OnceLock<watch::Sender<UpdateStatus>> = OnceLock::new();

#[derive(Clone, Debug, Default, PartialEq)]
pub enum UpdatePhase {
    #[default]
    Idle,
    Checking,
    AwaitingConfirmation,
    Approved,
    Downloading,
    Verifying,
    Installing,
    Failed,
}

#[derive(Clone, Debug, Default, PartialEq)]
pub struct UpdateStatus {
    pub phase: UpdatePhase,
    pub version: String,
    pub downloaded: u64,
    pub total: u64,
}

fn sender() -> &'static watch::Sender<UpdateStatus> {
    STATUS.get_or_init(|| watch::channel(UpdateStatus::default()).0)
}

pub fn subscribe() -> watch::Receiver<UpdateStatus> {
    sender().subscribe()
}
pub fn continue_without_update() {
    sender().send_replace(UpdateStatus::default());
}
pub fn install_update(version: &str) {
    answer_update(sender(), version, true);
}
pub fn cancel_update(version: &str) {
    answer_update(sender(), version, false);
}

fn answer_update(status: &watch::Sender<UpdateStatus>, version: &str, install: bool) -> bool {
    status.send_if_modified(|state| {
        if state.version != version
            || !(state.phase == UpdatePhase::AwaitingConfirmation
                || (!install && state.phase == UpdatePhase::Failed))
        {
            return false;
        }
        if install {
            state.phase = UpdatePhase::Approved;
        } else {
            *state = UpdateStatus::default();
        }
        true
    })
}

async fn request_installation(status: &watch::Sender<UpdateStatus>, version: &str) -> bool {
    let mut receiver = status.subscribe();
    status.send_replace(UpdateStatus {
        phase: UpdatePhase::AwaitingConfirmation,
        version: version.to_owned(),
        ..UpdateStatus::default()
    });
    loop {
        {
            let state = receiver.borrow_and_update();
            if state.version != version {
                return false;
            }
            match state.phase {
                UpdatePhase::Approved => return true,
                UpdatePhase::AwaitingConfirmation => {}
                _ => return false,
            }
        }
        if receiver.changed().await.is_err() {
            return false;
        }
    }
}

pub fn retry() {
    sender().send_if_modified(|state| {
        if state.phase != UpdatePhase::Failed {
            return false;
        }
        *state = UpdateStatus {
            phase: UpdatePhase::Checking,
            ..UpdateStatus::default()
        };
        true
    });
}

pub fn restore_startup_context() {
    if let Ok(version) = std::env::var("ARCADIUM_UPDATE_FAILED") {
        sender().send_replace(UpdateStatus {
            phase: UpdatePhase::Failed,
            version,
            ..UpdateStatus::default()
        });
        // Called before the runtime starts.
        unsafe {
            std::env::remove_var("ARCADIUM_UPDATE_FAILED");
        }
    }
}

pub fn spawn_update_check(_auto_install: bool, _events: EventBus) {
    tokio::spawn(check_at_startup());
}

pub async fn check_at_startup() {
    if subscribe().borrow().phase == UpdatePhase::Failed {
        let mut receiver = subscribe();
        loop {
            let failed = receiver.borrow_and_update().phase == UpdatePhase::Failed;
            if !failed || receiver.changed().await.is_err() {
                break;
            }
        }
        if receiver.borrow().phase == UpdatePhase::Idle {
            return;
        }
    }
    if !can_self_update() {
        continue_without_update();
        return;
    }
    #[cfg(windows)]
    if helpers::active() {
        tracing::info!("Deferring the update while game services use the installed launcher");
        continue_without_update();
        return;
    }
    if BUSY.swap(true, Ordering::AcqRel) {
        return;
    }
    loop {
        sender().send_replace(UpdateStatus {
            phase: UpdatePhase::Checking,
            ..UpdateStatus::default()
        });
        match run_check().await {
            Ok(()) => {
                continue_without_update();
                break;
            }
            Err(error) => {
                tracing::warn!("Arcadium update failed: {error:#}");
                let mut receiver = subscribe();
                if receiver.borrow().phase == UpdatePhase::Checking {
                    continue_without_update();
                    break;
                }
                let mut state = receiver.borrow().clone();
                state.phase = UpdatePhase::Failed;
                sender().send_replace(state);
                loop {
                    let failed = receiver.borrow_and_update().phase == UpdatePhase::Failed;
                    if !failed || receiver.changed().await.is_err() {
                        break;
                    }
                }
                if receiver.borrow().phase == UpdatePhase::Idle {
                    break;
                }
            }
        }
    }
    BUSY.store(false, Ordering::Release);
}

fn can_self_update() -> bool {
    !cfg!(debug_assertions)
        && std::env::var_os("ARCADIUM_DISABLE_AUTOUPDATE").is_none()
        && std::env::var_os("ARCADIUM_DATA_DIR").is_none()
        && std::env::current_exe().ok().is_some_and(|p| {
            #[cfg(windows)]
            {
                p.parent()
                    .is_some_and(|parent| parent.join("Uninstall.exe").is_file())
            }
            #[cfg(target_os = "macos")]
            {
                macos::installed(&p)
            }
            #[cfg(not(any(windows, target_os = "macos")))]
            {
                let _ = p;
                false
            }
        })
}

async fn run_check() -> anyhow::Result<()> {
    let client = UpdateClient::new(env!("CARGO_PKG_VERSION"))?;
    let Some(candidate) = tokio::time::timeout(
        Duration::from_secs(10),
        client.check(env!("CARGO_PKG_VERSION")),
    )
    .await??
    else {
        return Ok(());
    };
    let version = candidate.version.to_string();
    if !request_installation(sender(), &version).await {
        return Ok(());
    }
    let directory = std::env::temp_dir()
        .join("ArcadiumUpdate")
        .join(uuid::Uuid::new_v4().to_string());
    sender().send_replace(UpdateStatus {
        phase: UpdatePhase::Downloading,
        version: version.clone(),
        downloaded: 0,
        total: candidate.installer.size,
    });
    let installer = client
        .download(&candidate, &directory, |downloaded, total, verifying| {
            sender().send_replace(UpdateStatus {
                phase: if verifying {
                    UpdatePhase::Verifying
                } else {
                    UpdatePhase::Downloading
                },
                version: version.clone(),
                downloaded,
                total,
            });
        })
        .await?;
    #[cfg(target_os = "macos")]
    {
        sender().send_replace(UpdateStatus {
            phase: UpdatePhase::Installing,
            version: version.clone(),
            downloaded: candidate.installer.size,
            total: candidate.installer.size,
        });
        return macos::install(&installer, &directory, &version, candidate.platform).await;
    }
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        if helpers::active() {
            tracing::info!("Deferring the update while game services use the installed launcher");
            return Ok(());
        }
        let executable = std::env::current_exe()?;
        let install_dir = executable
            .parent()
            .ok_or_else(|| anyhow::anyhow!("Launcher installation directory is unavailable"))?;
        sender().send_replace(UpdateStatus {
            phase: UpdatePhase::Installing,
            version,
            downloaded: candidate.installer.size,
            total: candidate.installer.size,
        });
        let ready = directory.join("installer-ready");
        let mut helper = std::process::Command::new(installer)
            .args(["/S", "/AUTOUPDATE", "/RESTART"])
            .arg(format!("/UPDATEPID={}", std::process::id()))
            .env(
                "ARCADIUM_RESTART_ARGS",
                serde_json::to_string(&std::env::args().skip(1).collect::<Vec<_>>())?,
            )
            .env("ARCADIUM_UPDATE_READY", &ready)
            .raw_arg(format!("/D={}", install_dir.display()))
            .creation_flags(0x08000000)
            .spawn()?;
        let deadline = tokio::time::Instant::now() + Duration::from_secs(60);
        while !ready.is_file() {
            if helper.try_wait()?.is_some() {
                anyhow::bail!("Installer exited before the update handoff");
            }
            if tokio::time::Instant::now() >= deadline {
                let _ = helper.kill();
                anyhow::bail!("Installer did not acknowledge the update handoff");
            }
            tokio::time::sleep(Duration::from_millis(100)).await;
        }
        tokio::time::sleep(Duration::from_millis(900)).await;
        std::process::exit(0);
    }
    #[cfg(not(any(windows, target_os = "macos")))]
    {
        let _ = installer;
        Ok(())
    }
}

pub fn acknowledge_restart() {
    #[cfg(target_os = "macos")]
    {
        static ACKNOWLEDGED: AtomicBool = AtomicBool::new(false);
        if !ACKNOWLEDGED.swap(true, Ordering::AcqRel) {
            if let Some(path) = std::env::var_os("ARCADIUM_UPDATE_BOOT_READY") {
                if let Err(error) = std::fs::write(path, b"ready") {
                    tracing::warn!("Could not acknowledge the restarted application: {error}");
                }
            }
        }
    }
}

pub fn spawn_simulated_update() {
    tokio::spawn(async {
        if BUSY.swap(true, Ordering::AcqRel) {
            return;
        }
        if !request_installation(sender(), "Preview").await {
            BUSY.store(false, Ordering::Release);
            return;
        }
        for downloaded in 0..=100 {
            sender().send_replace(UpdateStatus {
                phase: UpdatePhase::Downloading,
                version: "Preview".into(),
                downloaded,
                total: 100,
            });
            tokio::time::sleep(Duration::from_millis(30)).await;
        }
        sender().send_replace(UpdateStatus {
            phase: UpdatePhase::Verifying,
            version: "Preview".into(),
            downloaded: 100,
            total: 100,
        });
        tokio::time::sleep(Duration::from_secs(1)).await;
        continue_without_update();
        BUSY.store(false, Ordering::Release);
    });
}

#[cfg(test)]
mod tests {
    use super::*;

    async fn pending_request(
        status: &watch::Sender<UpdateStatus>,
        version: &str,
    ) -> tokio::task::JoinHandle<bool> {
        let mut receiver = status.subscribe();
        let status = status.clone();
        let version = version.to_owned();
        let request = tokio::spawn(async move { request_installation(&status, &version).await });
        {
            let waiting = tokio::time::timeout(
                Duration::from_secs(2),
                receiver.wait_for(|state| state.phase == UpdatePhase::AwaitingConfirmation),
            )
            .await
            .unwrap()
            .unwrap();
            assert_eq!(waiting.downloaded, 0);
        }
        request
    }

    #[tokio::test]
    async fn download_waits_for_explicit_approval_and_cancel_keeps_the_current_version() {
        let status = watch::channel(UpdateStatus::default()).0;
        let request = pending_request(&status, "0.4.0").await;
        tokio::task::yield_now().await;
        assert!(!request.is_finished());
        assert_eq!(status.borrow().phase, UpdatePhase::AwaitingConfirmation);
        assert!(answer_update(&status, "0.4.0", false));
        assert!(
            !tokio::time::timeout(Duration::from_secs(2), request)
                .await
                .unwrap()
                .unwrap()
        );
        assert_eq!(*status.borrow(), UpdateStatus::default());
        assert!(!answer_update(&status, "0.4.0", true));
    }

    #[tokio::test]
    async fn approval_is_for_the_displayed_version_and_cannot_be_changed_by_stale_buttons() {
        let status = watch::channel(UpdateStatus::default()).0;
        let request = pending_request(&status, "0.4.0").await;
        assert!(!answer_update(&status, "0.3.9", true));
        assert!(!answer_update(&status, "0.3.9", false));
        assert!(!request.is_finished());
        assert!(answer_update(&status, "0.4.0", true));
        assert!(!answer_update(&status, "0.4.0", false));
        assert!(!answer_update(&status, "0.4.0", true));
        assert!(
            tokio::time::timeout(Duration::from_secs(2), request)
                .await
                .unwrap()
                .unwrap()
        );
        assert_eq!(status.borrow().phase, UpdatePhase::Approved);
    }

    #[tokio::test]
    async fn unrelated_progress_never_grants_permission_and_failed_updates_can_be_dismissed() {
        for phase in [
            UpdatePhase::Checking,
            UpdatePhase::Downloading,
            UpdatePhase::Installing,
        ] {
            let status = watch::channel(UpdateStatus::default()).0;
            let request = pending_request(&status, "0.4.0").await;
            status.send_modify(|state| state.phase = phase.clone());
            assert!(
                !tokio::time::timeout(Duration::from_secs(2), request)
                    .await
                    .unwrap()
                    .unwrap()
            );
            assert!(!answer_update(&status, "0.4.0", false));
        }
        let status = watch::channel(UpdateStatus {
            phase: UpdatePhase::Failed,
            version: "0.4.0".into(),
            ..UpdateStatus::default()
        })
        .0;
        assert!(!answer_update(&status, "0.4.0", true));
        assert!(answer_update(&status, "0.4.0", false));
        assert_eq!(*status.borrow(), UpdateStatus::default());
    }

    #[tokio::test(flavor = "multi_thread", worker_threads = 2)]
    async fn real_dialog_supports_cancel_escape_backdrop_and_explicit_update() {
        use freya::prelude::*;
        use freya_testing::TestingRunner;
        use std::collections::HashMap;

        for action in ["Cancel", "Escape", "Backdrop", "Update now"] {
            let request = pending_request(sender(), "0.4.0").await;
            let app = || {
                rect()
                    .width(Size::fill())
                    .height(Size::fill())
                    .background(crate::theme::accent::themed_fill(Color::from_rgb(
                        12, 16, 23,
                    )))
                    .child(crate::components::UpdatePromptOverlay)
            };
            let (mut test, _) = TestingRunner::new(app, Size2D::new(600., 430.), |_| (), 1.);
            test.set_fonts(HashMap::from([(
                "Inter",
                include_bytes!("../assets/fonts/Inter-Regular.ttf").as_slice(),
            )]));
            test.sync_and_update();
            test.poll_n(Duration::from_millis(5), 8);
            assert!(!request.is_finished());
            let labels = test.find_many(|_, element| {
                Label::try_downcast(element).map(|label| label.text.to_string())
            });
            assert!(
                labels.iter().any(|label| label == "Update available"),
                "{labels:?}"
            );
            match action {
                "Escape" => test.press_key(Key::Named(NamedKey::Escape)),
                "Backdrop" => test.click_cursor((10., 10.)),
                button => {
                    let point = {
                        let node = test
                            .find(|node, element| {
                                Label::try_downcast(element)
                                    .filter(|label| label.text == button)
                                    .map(|_| node)
                            })
                            .expect("update action");
                        let area = node.layout().area;
                        (
                            area.min_x() as f64 + area.size.width as f64 / 2.,
                            area.min_y() as f64 + area.size.height as f64 / 2.,
                        )
                    };
                    test.click_cursor(point);
                }
            }
            test.poll_n(Duration::from_millis(5), 8);
            let approved = tokio::time::timeout(Duration::from_secs(2), request)
                .await
                .unwrap()
                .unwrap();
            assert_eq!(approved, action == "Update now");
            assert_eq!(
                sender().borrow().phase,
                if approved {
                    UpdatePhase::Approved
                } else {
                    UpdatePhase::Idle
                }
            );
            continue_without_update();
        }
    }
}
