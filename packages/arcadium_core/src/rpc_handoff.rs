use std::io::{BufRead, Write};
use std::path::PathBuf;
use std::process::Stdio;
use std::time::Duration;

use arcadium_common::paths;
use arcadium_discord::{DiscordRpc, Presence};
use serde::{Deserialize, Serialize};
use tokio::io::{AsyncBufReadExt, AsyncWriteExt};

#[derive(Serialize, Deserialize)]
struct Game {
    pid: u32,
    started_at: u64,
    cluster: String,
    mc_version: String,
}

#[derive(Serialize, Deserialize)]
struct Handoff {
    owner_file: PathBuf,
    lease: String,
    games: Vec<Game>,
}

fn owner_file() -> crate::LauncherResult<PathBuf> {
    Ok(paths::config_dir()?.join("discord-owner.json"))
}

pub async fn claim_launcher() -> crate::LauncherResult<()> {
    let file = owner_file()?;
    arcadium_io::write_atomic(file, uuid::Uuid::new_v4().to_string().into_bytes()).await?;
    Ok(())
}

pub async fn handoff(state: &crate::LauncherState) -> crate::LauncherResult<bool> {
    if !state.settings.read().discord_enabled {
        return Ok(false);
    }
    let mut games = Vec::new();
    for id in state.games.running_ids() {
        let Some(pid) = state.games.pid(id) else {
            continue;
        };
        let Some(started_at) = crate::game::process_start_time(pid) else {
            continue;
        };
        let cluster = state.clusters.get(id).await?;
        games.push(Game {
            pid,
            started_at,
            cluster: cluster.name,
            mc_version: cluster.mc_version,
        });
    }
    if games.is_empty() {
        return Ok(false);
    }
    let owner_file = owner_file()?;
    let lease = uuid::Uuid::new_v4().to_string();
    let mut payload = serde_json::to_vec(&Handoff {
        owner_file: owner_file.clone(),
        lease: lease.clone(),
        games,
    })?;
    payload.push(b'\n');
    let mut command = tokio::process::Command::new(std::env::current_exe()?);
    command
        .arg("--discord-game")
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::null());
    arcadium_common::process::no_window(command.as_std_mut());
    #[cfg(unix)]
    {
        use std::os::unix::process::CommandExt;
        command.as_std_mut().process_group(0);
    }
    let mut child = command.spawn()?;
    let result = async {
        let mut input = child
            .stdin
            .take()
            .ok_or_else(|| std::io::Error::other("Discord helper input is unavailable"))?;
        input.write_all(&payload).await?;
        let output = child
            .stdout
            .take()
            .ok_or_else(|| std::io::Error::other("Discord helper output is unavailable"))?;
        let mut reader = tokio::io::BufReader::new(output);
        let mut ready = String::new();
        tokio::time::timeout(Duration::from_secs(5), reader.read_line(&mut ready))
            .await
            .map_err(|_| std::io::Error::other("Discord helper startup timed out"))??;
        if ready.trim() != "READY" {
            return Err(std::io::Error::other("Discord helper did not initialize"));
        }
        arcadium_io::write_atomic(owner_file, lease.into_bytes())
            .await
            .map_err(std::io::Error::other)?;
        state.discord.shutdown_for_handoff();
        input.write_all(b"GO\n").await?;
        input.shutdown().await?;
        Ok::<(), std::io::Error>(())
    }
    .await;
    if let Err(error) = result {
        let _ = child.kill().await;
        return Err(error.into());
    }
    Ok(true)
}

pub fn requested() -> bool {
    std::env::args().any(|arg| arg == "--discord-game")
}

pub fn run() -> Result<(), Box<dyn std::error::Error>> {
    let mut input = std::io::stdin().lock();
    let mut line = String::new();
    std::io::Read::take(&mut input, 64 * 1024).read_line(&mut line)?;
    let mut handoff: Handoff = serde_json::from_str(&line)?;
    if handoff.games.len() > 32 || uuid::Uuid::parse_str(&handoff.lease).is_err() {
        return Err("Invalid Discord handoff".into());
    }
    println!("READY");
    std::io::stdout().flush()?;
    line.clear();
    input.read_line(&mut line)?;
    if line.trim() != "GO"
        || std::fs::read_to_string(&handoff.owner_file).ok().as_deref()
            != Some(handoff.lease.as_str())
    {
        return Ok(());
    }
    handoff
        .games
        .retain(|game| crate::game::is_process_alive(game.pid, Some(game.started_at)));
    let Some(game) = handoff.games.first() else {
        return Ok(());
    };
    let rpc = DiscordRpc::spawn_with_presence(
        true,
        Presence::Playing {
            cluster: game.cluster.clone(),
            mc_version: game.mc_version.clone(),
        },
    );
    loop {
        if std::fs::read_to_string(&handoff.owner_file).ok().as_deref()
            != Some(handoff.lease.as_str())
        {
            rpc.shutdown_for_handoff();
            break;
        }
        handoff
            .games
            .retain(|game| crate::game::is_process_alive(game.pid, Some(game.started_at)));
        let Some(game) = handoff.games.first() else {
            let owns_presence = std::fs::read_to_string(&handoff.owner_file).ok().as_deref()
                == Some(handoff.lease.as_str());
            rpc.shutdown_and_wait(owns_presence);
            break;
        };
        rpc.set_presence(Presence::Playing {
            cluster: game.cluster.clone(),
            mc_version: game.mc_version.clone(),
        });
        std::thread::sleep(Duration::from_secs(1));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn handoff_pins_game_process_identity_without_account_secrets() {
        let handoff = Handoff {
            owner_file: "discord-owner.json".into(),
            lease: uuid::Uuid::new_v4().to_string(),
            games: vec![Game {
                pid: 12,
                started_at: 34,
                cluster: "Arcadium 1.8.9".into(),
                mc_version: "1.8.9".into(),
            }],
        };
        let text = serde_json::to_string(&handoff).unwrap();
        assert!(!text.contains("token") && !text.contains("username"));
        let restored: Handoff = serde_json::from_str(&text).unwrap();
        assert_eq!(restored.games[0].started_at, 34);
    }
}
