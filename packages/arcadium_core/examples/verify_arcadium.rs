#![recursion_limit = "512"]
use anyhow::Context;
use arcadium_core::LauncherState;
use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use std::time::Duration;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum NvidiumChoice {
    Enable,
    Keep,
}

impl NvidiumChoice {
    fn id(self) -> &'static str {
        match self {
            Self::Enable => "enable",
            Self::Keep => "keep",
        }
    }
}

fn nvidium_choice(arguments: &[String]) -> anyhow::Result<Option<NvidiumChoice>> {
    let mut choice = None;
    for argument in arguments {
        if argument == "--nvidium-choice" {
            anyhow::bail!("Use --nvidium-choice=enable or --nvidium-choice=keep");
        }
        if let Some(value) = argument.strip_prefix("--nvidium-choice=") {
            anyhow::ensure!(choice.is_none(), "Specify the Nvidium choice only once");
            choice = Some(match value {
                "enable" => NvidiumChoice::Enable,
                "keep" => NvidiumChoice::Keep,
                _ => anyhow::bail!("Use --nvidium-choice=enable or --nvidium-choice=keep"),
            });
        }
    }
    if choice.is_some() {
        anyhow::ensure!(
            arguments.iter().any(|value| value == "--offline-probe"),
            "Nvidium automatic answers require an isolated offline probe"
        );
        anyhow::ensure!(
            !arguments.iter().any(|value| matches!(
                value.as_str(),
                "--launch-real" | "--sign-in" | "--account-service" | "--prepare-only"
            )),
            "Nvidium automatic answers are only available for synthetic game launches"
        );
        anyhow::ensure!(
            arguments.iter().any(|value| value == "--minecraft=26.2"),
            "Nvidium automatic answers require --minecraft=26.2"
        );
    }
    Ok(choice)
}

fn prompt_answer(choice: Option<NvidiumChoice>, title: &str) -> Option<&'static str> {
    choice
        .filter(|_| title == "Nvidium recommended")
        .map(NvidiumChoice::id)
}

fn main() -> anyhow::Result<()> {
    let arguments: Vec<String> = std::env::args().collect();
    nvidium_choice(&arguments)?;
    if arguments.iter().any(|value| value == "--account-service") {
        let executable = std::env::var_os("ARCADIUM_VERIFICATION_LAUNCHER")
            .context("Set ARCADIUM_VERIFICATION_LAUNCHER to a built Arcadium launcher for native helper verification")?;
        let mut command = std::process::Command::new(executable);
        command.args(arguments.iter().skip(1));
        arcadium_common::process::no_window(&mut command);
        std::process::exit(command.status()?.code().unwrap_or(1));
    }
    tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .build()?
        .block_on(verify())
}

async fn verify() -> anyhow::Result<()> {
    let arguments: Vec<String> = std::env::args().collect();
    let nvidium_choice = nvidium_choice(&arguments)?;
    let data = PathBuf::from(arguments.get(1).expect("verification directory"));
    let data = if data.is_absolute() {
        data
    } else {
        std::env::current_dir()?.join(data)
    };
    arcadium_common::paths::set_launcher_dir(data.clone());
    let offline_probe = arguments.iter().any(|value| value == "--offline-probe");
    let minecraft = arguments
        .iter()
        .find_map(|value| value.strip_prefix("--minecraft="))
        .unwrap_or(arcadium_common::distribution::MINECRAFT_VERSION);
    anyhow::ensure!(
        arcadium_common::distribution::supports_version(minecraft),
        "Unsupported Minecraft version"
    );
    if offline_probe {
        let marker = data.join(".arcadium-verification-profile");
        anyhow::ensure!(
            marker.is_file()
                || !["auth.dat", "settings.json", "user_data.db"]
                    .iter()
                    .any(|name| data.join(name).exists()),
            "Offline verification requires a fresh test profile"
        );
        std::fs::create_dir_all(&data)?;
        std::fs::write(marker, "Arcadium offline launch verification\n")?;
        let mut settings = arcadium_core::settings::LauncherSettings {
            auto_update: false,
            discord_enabled: false,
            online_count_enabled: false,
            crash_reporting: false,
            seen_onboarding: true,
            ..Default::default()
        };
        settings.global_game_settings.launch_args = arguments
            .iter()
            .find_map(|value| value.strip_prefix("--launch-args="))
            .map(str::to_owned);
        arcadium_core::settings::store::save_settings(&settings).await?;
        arcadium_core::logger::init_debug()?;
        anyhow::ensure!(
            std::env::var_os("ARCADIUM_VERIFICATION_LAUNCHER").is_some(),
            "Offline launch verification requires ARCADIUM_VERIFICATION_LAUNCHER"
        );
    }
    let (events, mut receiver) = arcadium_events::EventBus::channel();
    let observed_nvidium_choices = Arc::new(Mutex::new(Vec::<String>::new()));
    let prompt_choices = Arc::clone(&observed_nvidium_choices);
    let prompt_directory = data.clone();
    let prompt_minecraft = minecraft.to_owned();
    tokio::spawn(async move {
        while let Some(event) = receiver.recv().await {
            if let arcadium_events::Event::Notification(arcadium_events::Notification::Prompt(
                prompt,
            )) = event
            {
                eprintln!("Verification requires a choice: {}", prompt.title);
                let answer = prompt_answer(nvidium_choice, &prompt.title).filter(|answer| {
                    prompt.choices.iter().any(|choice| choice.id == *answer)
                        || (*answer == "keep"
                            && prompt.dismiss.as_deref() == Some("Keep current renderer"))
                });
                if prompt.title == "Nvidium recommended" {
                    let observations = {
                        let mut choices = prompt_choices.lock().expect("Nvidium observations lock");
                        choices.push(answer.unwrap_or("dismissed").to_owned());
                        choices.clone()
                    };
                    let report = serde_json::json!({
                        "offlineProbe": offline_probe,
                        "minecraftVersion": prompt_minecraft,
                        "requestedNvidiumChoice": nvidium_choice.map(NvidiumChoice::id),
                        "observedNvidiumChoices": observations,
                        "choiceCount": prompt.choices.len(),
                        "dismissLabel": prompt.dismiss.as_deref(),
                    });
                    if let Err(error) = std::fs::write(
                        prompt_directory.join("nvidium-observations.json"),
                        serde_json::to_vec_pretty(&report).expect("Nvidium observation JSON"),
                    ) {
                        eprintln!("Could not record Nvidium observations: {error}");
                    }
                }
                let reply = answer
                    .filter(|answer| prompt.choices.iter().any(|choice| choice.id == *answer))
                    .map(arcadium_events::Answer::new);
                let _ = prompt.reply.send(reply);
            }
        }
    });
    let state = LauncherState::new(events).await?;
    state.versions.sync(&state.services).await?;
    state.bundles.sync(&state.services.content()).await?;
    arcadium_core::clusters::apply_remote_migrations(&state).await?;
    let clusters = arcadium_core::ensure_from_bundles(&state).await?;
    let list = state.clusters.list().await?;
    assert!(!list.is_empty());
    assert!(
        list.iter()
            .all(|cluster| arcadium_common::distribution::supports_version(&cluster.mc_version))
    );
    println!("Arcadium catalog: {} supported instance(s)", list.len());
    let cluster = list
        .iter()
        .find(|cluster| cluster.mc_version == minecraft)
        .context("Selected Minecraft version was not provisioned from the embedded catalog")?;
    let explicit_java = arguments
        .iter()
        .find_map(|value| value.strip_prefix("--java="));
    anyhow::ensure!(
        nvidium_choice != Some(NvidiumChoice::Enable) || explicit_java.is_some(),
        "Nvidium opt-in verification requires an explicit isolated --java runtime"
    );
    if let Some(folder) = explicit_java {
        let runtime = state.java.add_custom_runtime(PathBuf::from(folder)).await?;
        if offline_probe {
            if nvidium_choice == Some(NvidiumChoice::Enable) {
                anyhow::ensure!(
                    PathBuf::from(&runtime.absolute_path)
                        .canonicalize()?
                        .starts_with(data.canonicalize()?),
                    "Nvidium opt-in verification must use a Java runtime inside the synthetic profile"
                );
            }
            state.settings.write().global_game_settings.java_path = Some(runtime.absolute_path);
        }
    }
    if arguments.iter().any(|value| value == "--prepare") {
        arcadium_core::clusters::prepare_cluster_locked(
            &state, cluster.id, false, true, true, None,
        )
        .await?;
        arcadium_core::install_cluster_bundles(
            cluster.id,
            state.bundles.as_ref(),
            None,
            &state.services.content(),
        )
        .await?;
        let archives = state
            .bundles
            .archives_for(&state.services.content(), minecraft, cluster.mc_loader)
            .await?;
        anyhow::ensure!(
            !archives.is_empty(),
            "No bundled Mod archives matched the selected Minecraft loader"
        );
        println!("Minecraft {minecraft} and Arcadium bundles installed.");
    }
    if arguments.iter().any(|value| value == "--sign-in") {
        let session = state.auth.begin_microsoft_login().await?;
        std::process::Command::new("rundll32.exe")
            .arg("url.dll,FileProtocolHandler")
            .arg(session.auth_url())
            .spawn()?;
        let _account = tokio::time::timeout(
            Duration::from_secs(900),
            state.auth.finish_microsoft_login(session),
        )
        .await??;
        println!("Microsoft Minecraft account authenticated; credentials protected locally.");
    }
    let mut launched_pid = None;
    let mut verified_arguments = None;
    let mut nvidium_installation = None;
    if (offline_probe && !arguments.iter().any(|value| value == "--prepare-only"))
        || arguments.iter().any(|value| value == "--launch-real")
    {
        let account = if offline_probe {
            arcadium_auth::offline_account("ArcadiumTest".into())
        } else {
            state
                .auth
                .default_account_for_launch()
                .await?
                .context("Sign in before launch verification")?
        };
        if nvidium_choice.is_some() {
            anyhow::ensure!(
                account.kind == arcadium_auth::AccountKind::Offline
                    && account.username == "ArcadiumTest"
                    && account.access_token == "0"
                    && account.refresh_token.is_empty(),
                "Nvidium automatic answers require the synthetic account"
            );
        }
        let launched = arcadium_core::launch_cluster(&state, cluster.id, &account, true).await?;
        launched_pid = launched.pid;
        println!("Arcadium Minecraft process launched.");
        let qa_report = arguments
            .iter()
            .find_map(|value| value.strip_prefix("--qa-report="))
            .map(PathBuf::from);
        let nvidium_check = if let Some(choice) = nvidium_choice {
            async {
                let observations = observed_nvidium_choices
                    .lock()
                    .expect("Nvidium observations lock")
                    .clone();
                anyhow::ensure!(
                    observations == [choice.id()],
                    "The requested Nvidium recommendation was not observed exactly once"
                );
                if choice == NvidiumChoice::Enable {
                    let pid = launched
                        .pid
                        .context("The synthetic game process has no process ID")?;
                    verify_nvidium_installation(&state, cluster, pid)
                        .await
                        .map(Some)
                } else {
                    Ok(None)
                }
            }
            .await
        } else {
            Ok(None)
        };
        let startup_check = if let Err(error) = nvidium_check.as_ref() {
            Err(anyhow::anyhow!(error.to_string()))
        } else if let Some(report) = qa_report.as_ref() {
            anyhow::ensure!(offline_probe, "GUI QA requires an isolated offline profile");
            let pid = launched
                .pid
                .context("The synthetic game process has no process ID")?;
            let expected_directory = if arcadium_common::distribution::uses_native_forge(minecraft)
            {
                cluster.game_dir()?
            } else {
                cluster.user_game_dir()?
            };
            wait_for_qa_report(report, pid, &expected_directory)
                .await
                .map(Some)
        } else {
            tokio::time::sleep(Duration::from_secs(25)).await;
            (|| -> anyhow::Result<Option<Vec<String>>> {
                if let Some(pid) = launched.pid {
                    let system = sysinfo::System::new_all();
                    let process = system
                        .process(sysinfo::Pid::from_u32(pid))
                        .context("Minecraft exited during startup; inspect the game log")?;
                    if offline_probe {
                        let command: Vec<String> = process
                            .cmd()
                            .iter()
                            .map(|value| value.to_string_lossy().into_owned())
                            .collect();
                        let game_directory =
                            if arcadium_common::distribution::uses_native_forge(minecraft) {
                                cluster.game_dir()?
                            } else {
                                cluster.user_game_dir()?
                            }
                            .canonicalize()?;
                        anyhow::ensure!(
                            command.windows(2).any(|pair| pair[0] == "--gameDir"
                                && std::path::Path::new(&pair[1])
                                    .canonicalize()
                                    .is_ok_and(|value| value == game_directory)),
                            "Minecraft did not receive the expected initial game directory"
                        );
                        if minecraft == "1.7.10" {
                            anyhow::ensure!(
                            command.iter().any(|value| value
                                == "com.gtnewhorizons.retrofuturabootstrap.MainStartOnFirstThread"),
                            "The native legacy process did not use the Forge bootstrap"
                        );
                            anyhow::ensure!(
                            command
                                .iter()
                                .any(|value| value == "io.github.arcadiumclient.forge1710.bootstrap.ArcadiumForgeTweaker"),
                            "The native legacy process did not use the native Forge tweaker"
                        );
                            anyhow::ensure!(
                                !command.iter().any(|value| value
                                    == "io.github.arcadiumclient.forgecompat.ArcadiumLaunch"),
                                "Forge compatibility must not run on Minecraft 1.7.10"
                            );
                        }
                        return Ok(Some(command));
                    }
                } else {
                    anyhow::ensure!(
                        !offline_probe,
                        "The synthetic game process has no process ID"
                    );
                }
                Ok(None)
            })()
        };
        nvidium_installation = nvidium_check.ok().flatten();
        let stopped = if offline_probe {
            let stopped = state.games.kill(cluster.id) || qa_report.is_some();
            tokio::time::sleep(Duration::from_secs(2)).await;
            stopped
        } else {
            true
        };
        verified_arguments = startup_check?;
        anyhow::ensure!(stopped, "Could not stop the verification game process");
        if offline_probe {
            if let Some(pid) = launched_pid {
                let system = sysinfo::System::new_all();
                anyhow::ensure!(
                    system.process(sysinfo::Pid::from_u32(pid)).is_none(),
                    "The synthetic game process is still running after cleanup"
                );
            }
            println!("Native game startup verified and the synthetic process stopped.");
        } else {
            println!("Game process is running; verify the visible UI separately.");
        }
    }
    let _ = clusters;
    let required_java =
        if launched_pid.is_some() || arguments.iter().any(|value| value == "--prepare") {
            arcadium_core::required_java_major(&state, cluster.id).await?
        } else {
            None
        };
    std::fs::create_dir_all(&data)?;
    std::fs::write(
        data.join("verification.json"),
        serde_json::to_vec_pretty(
            &serde_json::json!({"catalogVerified":true,"minecraftVersion":minecraft,"instanceCount":list.len(),"app":"Arcadium Client",
                "gameDirectory":cluster.game_dir()?,"userGameDirectory":cluster.user_game_dir()?,"modsDirectory":arcadium_common::paths::cluster_mods_dir(&cluster.folder_name)?,
                "requiredJavaMajor":required_java,
                "loader":cluster.mc_loader.to_string(),"loaderBackend":cluster.mc_loader.modrinth_name(),"defaultLoaderVersion":
                if cluster.mc_loader == arcadium_common::GameLoader::Fabric { arcadium_common::distribution::default_fabric_loader(minecraft) } else { arcadium_common::distribution::default_ornithe_loader(minecraft) },
                "componentFiles":arcadium_common::distribution::components(minecraft).map(|entries|entries.iter().map(|entry|&entry.file).collect::<Vec<_>>()),
                "launchedPid":launched_pid,"offlineProbe":offline_probe,"actualOfflineArguments":verified_arguments,
                "requestedNvidiumChoice":nvidium_choice.map(NvidiumChoice::id),
                "observedNvidiumChoices":observed_nvidium_choices.lock().expect("Nvidium observations lock").clone(),
                "nvidiumInstallation":nvidium_installation}),
        )?,
    )?;
    Ok(())
}

async fn verify_nvidium_installation(
    state: &LauncherState,
    cluster: &arcadium_core::clusters::Cluster,
    pid: u32,
) -> anyhow::Result<serde_json::Value> {
    use sha2::{Digest, Sha512};

    let deadline = tokio::time::Instant::now() + Duration::from_secs(5);
    let command = loop {
        let system = sysinfo::System::new_all();
        let process = system
            .process(sysinfo::Pid::from_u32(pid))
            .context("The synthetic game process exited before Nvidium verification")?;
        let command = process
            .cmd()
            .iter()
            .map(|value| value.to_string_lossy().into_owned())
            .collect::<Vec<_>>();
        if command
            .iter()
            .any(|value| value.starts_with("-Dfabric.modsFolder="))
            && command.iter().any(|value| value == "--gameDir")
        {
            break command;
        }
        anyhow::ensure!(
            tokio::time::Instant::now() < deadline,
            "The synthetic process command line was unavailable for Nvidium verification"
        );
        tokio::time::sleep(Duration::from_millis(50)).await;
    };
    let mods = command
        .iter()
        .find_map(|value| value.strip_prefix("-Dfabric.modsFolder="))
        .map(PathBuf::from)
        .context("The synthetic process did not select an explicit active mods folder")?;
    let game_dir = command
        .windows(2)
        .find(|pair| pair[0] == "--gameDir")
        .map(|pair| PathBuf::from(&pair[1]))
        .context("The synthetic process did not specify its game directory")?;
    anyhow::ensure!(
        game_dir.canonicalize()? == cluster.user_game_dir()?.canonicalize()?,
        "Nvidium verification did not target the synthetic user game directory"
    );
    let lock: serde_json::Value =
        serde_json::from_slice(arcadium_common::distribution::MODERN_262_LOCK)?;
    let pinned = &lock["optionalNvidium"];
    let file = pinned["file"]
        .as_str()
        .context("Pinned Nvidium filename is absent")?;
    let path = mods.join(file);
    let expected_sha1 = pinned["sha1"]
        .as_str()
        .context("Pinned Nvidium hash is absent")?;
    let bytes = tokio::fs::read(&path)
        .await
        .context("Nvidium was not materialized before the synthetic game launch")?;
    let sha512 = Sha512::digest(&bytes)
        .iter()
        .map(|byte| format!("{byte:02x}"))
        .collect::<String>();
    anyhow::ensure!(
        arcadium_io::sha1_file(&path).await? == expected_sha1
            && Some(bytes.len() as u64) == pinned["bytes"].as_u64()
            && Some(sha512.as_str()) == pinned["sha512"].as_str(),
        "The active Nvidium file does not match the pinned release"
    );
    let links = arcadium_content::packages::store::PackageStore::list_linked_artifacts(
        cluster.id,
        &state.services.content(),
    )
    .await?;
    anyhow::ensure!(
        links
            .iter()
            .any(|link| link.hash == expected_sha1 && link.enabled),
        "The active Nvidium release is not enabled in the normal package database"
    );
    let options = tokio::fs::read_to_string(game_dir.join("options.txt")).await?;
    anyhow::ensure!(
        options
            .lines()
            .any(|line| line == "preferredGraphicsBackend:\"opengl\""),
        "Nvidium was enabled without the native OpenGL option"
    );
    Ok(
        serde_json::json!({"file":path,"sha1":expected_sha1,"sha512":sha512,
        "databaseEnabled":true,"graphicsBackend":"opengl","verifiedBeforeStartupCheck":true}),
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    fn arguments(extra: &[&str]) -> Vec<String> {
        ["verify_arcadium", "synthetic-profile"]
            .into_iter()
            .chain(extra.iter().copied())
            .map(str::to_owned)
            .collect()
    }

    #[test]
    fn explicit_nvidium_choices_require_synthetic_262_game_launches() {
        for choice in ["enable", "keep"] {
            let flag = format!("--nvidium-choice={choice}");
            assert_eq!(
                nvidium_choice(&arguments(&["--offline-probe", "--minecraft=26.2", &flag]))
                    .unwrap()
                    .unwrap()
                    .id(),
                choice
            );
            assert!(nvidium_choice(&arguments(&["--minecraft=26.2", &flag])).is_err());
            assert!(
                nvidium_choice(&arguments(&[
                    "--offline-probe",
                    "--minecraft=1.21.11",
                    &flag
                ]))
                .is_err()
            );
            for forbidden in [
                "--launch-real",
                "--sign-in",
                "--account-service",
                "--prepare-only",
            ] {
                assert!(
                    nvidium_choice(&arguments(&[
                        "--offline-probe",
                        "--minecraft=26.2",
                        &flag,
                        forbidden
                    ]))
                    .is_err()
                );
            }
        }
        assert!(
            nvidium_choice(&arguments(&["--launch-real"]))
                .unwrap()
                .is_none()
        );
    }

    #[test]
    fn malformed_or_repeated_choices_are_rejected() {
        for flags in [
            vec!["--nvidium-choice"],
            vec!["--nvidium-choice=yes"],
            vec!["--nvidium-choice=enable", "--nvidium-choice=keep"],
        ] {
            let mut args = arguments(&["--offline-probe", "--minecraft=26.2"]);
            args.extend(flags.into_iter().map(str::to_owned));
            assert!(nvidium_choice(&args).is_err());
        }
    }

    #[test]
    fn only_the_exact_recommendation_prompt_can_receive_an_explicit_answer() {
        assert_eq!(
            prompt_answer(Some(NvidiumChoice::Enable), "Nvidium recommended"),
            Some("enable")
        );
        assert_eq!(
            prompt_answer(Some(NvidiumChoice::Keep), "Nvidium recommended"),
            Some("keep")
        );
        assert_eq!(prompt_answer(None, "Nvidium recommended"), None);
        assert_eq!(
            prompt_answer(Some(NvidiumChoice::Enable), "Java required"),
            None
        );
        assert_eq!(
            prompt_answer(Some(NvidiumChoice::Enable), "Nvidium recommended again"),
            None
        );
    }
}

async fn wait_for_qa_report(
    report: &std::path::Path,
    pid: u32,
    expected_game_directory: &std::path::Path,
) -> anyhow::Result<Vec<String>> {
    let deadline = tokio::time::Instant::now() + Duration::from_secs(240);
    let mut command = Vec::new();
    loop {
        let system = sysinfo::System::new_all();
        let process = system.process(sysinfo::Pid::from_u32(pid));
        if command.is_empty()
            && let Some(process) = process
        {
            command = process
                .cmd()
                .iter()
                .map(|value| value.to_string_lossy().into_owned())
                .collect();
            if let Some(parent) = report.parent() {
                std::fs::create_dir_all(parent)?;
                std::fs::write(
                    parent.join("native-command.json"),
                    serde_json::to_vec_pretty(&command)?,
                )?;
            }
        }
        if report.is_file() {
            let result: serde_json::Value = serde_json::from_slice(&std::fs::read(report)?)?;
            anyhow::ensure!(
                result["passed"] == true,
                "In-game QA failed: {}",
                result["error"]
            );
            let expected = expected_game_directory.canonicalize()?;
            anyhow::ensure!(
                command.windows(2).any(|pair| pair[0] == "--gameDir"
                    && std::path::Path::new(&pair[1])
                        .canonicalize()
                        .is_ok_and(|value| value == expected)),
                "Minecraft did not receive the expected QA game directory"
            );
            return Ok(command);
        }
        anyhow::ensure!(
            process.is_some(),
            "Minecraft exited before writing its QA report"
        );
        anyhow::ensure!(
            tokio::time::Instant::now() < deadline,
            "In-game QA report timed out"
        );
        tokio::time::sleep(Duration::from_millis(500)).await;
    }
}
