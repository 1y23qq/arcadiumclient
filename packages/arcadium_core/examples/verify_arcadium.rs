#![recursion_limit = "512"]
use anyhow::Context;
use arcadium_core::LauncherState;
use std::path::PathBuf;
use std::time::Duration;

fn main() -> anyhow::Result<()> {
    let arguments: Vec<String> = std::env::args().collect();
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
        let settings = arcadium_core::settings::LauncherSettings {
            auto_update: false,
            discord_enabled: false,
            online_count_enabled: false,
            crash_reporting: false,
            seen_onboarding: true,
            ..Default::default()
        };
        arcadium_core::settings::store::save_settings(&settings).await?;
        arcadium_core::logger::init_debug()?;
        anyhow::ensure!(
            std::env::var_os("ARCADIUM_VERIFICATION_LAUNCHER").is_some(),
            "Offline launch verification requires ARCADIUM_VERIFICATION_LAUNCHER"
        );
    }
    let (events, mut receiver) = arcadium_events::EventBus::channel();
    tokio::spawn(async move {
        while let Some(event) = receiver.recv().await {
            if let arcadium_events::Event::Notification(arcadium_events::Notification::Prompt(
                prompt,
            )) = event
            {
                eprintln!("Verification requires a choice: {}", prompt.title);
                let _ = prompt.reply.send(None);
            }
        }
    });
    let state = LauncherState::new(events).await?;
    state.versions.sync(&state.services).await?;
    state.bundles.sync(&state.services.content()).await?;
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
    if arguments.iter().any(|value| value == "--prepare") {
        if let Some(folder) = arguments
            .iter()
            .find_map(|value| value.strip_prefix("--java="))
        {
            state.java.add_custom_runtime(PathBuf::from(folder)).await?;
        }
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
        let launched = arcadium_core::launch_cluster(&state, cluster.id, &account, true).await?;
        launched_pid = launched.pid;
        println!("Arcadium Minecraft process launched.");
        tokio::time::sleep(Duration::from_secs(25)).await;
        let startup_check = (|| -> anyhow::Result<Option<Vec<String>>> {
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
                    let game_directory = cluster.game_dir()?.canonicalize()?;
                    anyhow::ensure!(
                        command.windows(2).any(|pair| pair[0] == "--gameDir"
                            && std::path::Path::new(&pair[1])
                                .canonicalize()
                                .is_ok_and(|value| value == game_directory)),
                        "The native process did not receive the selected version's game directory"
                    );
                    if minecraft == "1.7.10" {
                        anyhow::ensure!(
                            command
                                .iter()
                                .any(|value| value
                                    == "net.fabricmc.loader.impl.launch.knot.KnotClient"),
                            "The native legacy process did not use the Ornithe loader entry point"
                        );
                        anyhow::ensure!(
                            command
                                .iter()
                                .any(|value| value.contains("fabric-loader-0.19.5.jar")),
                            "The native legacy process did not use the pinned loader version"
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
        })();
        let stopped = if offline_probe {
            let stopped = state.games.kill(cluster.id);
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
                "gameDirectory":cluster.game_dir()?,"modsDirectory":arcadium_common::paths::cluster_mods_dir(&cluster.folder_name)?,
                "requiredJavaMajor":required_java,
                "loader":cluster.mc_loader.to_string(),"loaderBackend":cluster.mc_loader.modrinth_name(),"defaultLoaderVersion":arcadium_common::distribution::default_ornithe_loader(minecraft),
                "componentFiles":arcadium_common::distribution::components(minecraft).map(|entries|entries.iter().map(|entry|&entry.file).collect::<Vec<_>>()),
                "launchedPid":launched_pid,"offlineProbe":offline_probe,"actualOfflineArguments":verified_arguments}),
        )?,
    )?;
    Ok(())
}
