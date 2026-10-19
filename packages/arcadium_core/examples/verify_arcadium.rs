#![recursion_limit = "512"]
use arcadium_core::LauncherState;
use std::path::PathBuf;
use std::time::Duration;

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    let arguments: Vec<String> = std::env::args().collect();
    let data = PathBuf::from(arguments.get(1).expect("verification directory"));
    let data = if data.is_absolute() {
        data
    } else {
        std::env::current_dir()?.join(data)
    };
    arcadium_common::paths::set_launcher_dir(data.clone());
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
    assert!(list.iter().all(|cluster| cluster.mc_version == "1.8.9"));
    println!("Arcadium catalog: {} supported instance(s)", list.len());
    let cluster = list.first().unwrap();
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
        println!("Minecraft 1.8.9 and Arcadium bundles installed.");
    }
    if arguments.iter().any(|value| value == "--sign-in") {
        let session = state.auth.begin_microsoft_login().await?;
        std::process::Command::new("rundll32.exe")
            .arg("url.dll,FileProtocolHandler")
            .arg(session.auth_url())
            .spawn()?;
        let account = tokio::time::timeout(
            Duration::from_secs(900),
            state.auth.finish_microsoft_login(session),
        )
        .await??;
        println!("Microsoft Minecraft account authenticated; credentials protected locally.");
    }
    if arguments.iter().any(|value| value == "--launch-real") {
        let account = state
            .auth
            .default_account_for_launch()
            .await?
            .expect("Sign in before launch verification");
        let launched = arcadium_core::launch_cluster(&state, cluster.id, &account, true).await?;
        println!("Arcadium Minecraft process launched.");
        tokio::time::sleep(Duration::from_secs(25)).await;
        if let Some(pid) = launched.pid {
            let system = sysinfo::System::new_all();
            anyhow::ensure!(
                system.process(sysinfo::Pid::from_u32(pid)).is_some(),
                "Minecraft exited during startup; inspect the game log"
            );
        }
        println!("Game process is running; verify the visible UI separately.");
    }
    let _ = clusters;
    std::fs::create_dir_all(&data)?;
    std::fs::write(
        data.join("verification.json"),
        serde_json::to_vec_pretty(
            &serde_json::json!({"catalogVerified":true,"minecraftVersion":"1.8.9","instanceCount":list.len(),"app":"Arcadium Client"}),
        )?,
    )?;
    Ok(())
}
