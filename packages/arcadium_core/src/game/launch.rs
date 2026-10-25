use std::fmt::Write as _;
use std::path::Path;
use std::process::Stdio;
use std::sync::Arc;
use std::time::Duration;

use chrono::{DateTime, Utc};
use interfrost::api::minecraft::ArgumentType;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::process::Command;

use crate::ClusterStage;
use crate::LauncherResult;
use crate::clusters::Cluster;
use crate::game::GameError;
use crate::game::session::SessionRecorder;
use crate::game::tail::spawn_log_tail;
use crate::settings::GameSettingsProfile;
use crate::state::LauncherState;
use arcadium_auth::MinecraftAccount;
use arcadium_common::paths;
use arcadium_discord::Presence;
use arcadium_events::{GroupedProgressSession, LaunchStage};
use arcadium_mc::{
    self as arguments, download_minecraft, download_version_info, game_files_missing,
    get_loader_version, resolve_minecraft_version,
};

pub fn is_running(state: &LauncherState, cluster_id: i64) -> bool {
    state.games.is_running(cluster_id)
}

#[derive(Debug, Clone)]
pub struct LaunchedGame {
    pub cluster_id: i64,
    pub pid: Option<u32>,
}

#[tracing::instrument(skip(state, account))]
pub async fn launch_cluster(
    state: &Arc<LauncherState>,
    cluster_id: i64,
    account: &MinecraftAccount,
    search_for_java: bool,
) -> LauncherResult<LaunchedGame> {
    tracing::info!(cluster_id, search_for_java, "launching cluster");

    let parallel = state.settings.read().allow_parallel_running_clusters;
    if !parallel && state.games.is_active(cluster_id) {
        tracing::warn!(
            cluster_id,
            "cluster already launching or running; refusing launch"
        );
        return Err(GameError::AlreadyRunning(cluster_id).into());
    }

    // A parallel attempt shares its entry with the session already under way, so
    // clearing it here would drop that game's kill sender and pid
    let adopted = state.games.is_active(cluster_id);

    let result = start(state, cluster_id, account, search_for_java).await;

    if result.is_err() && !adopted {
        state.games.remove(cluster_id);
        state
            .services
            .events
            .game_stage(cluster_id, LaunchStage::Exited);
    }

    result
}

async fn start(
    state: &Arc<LauncherState>,
    cluster_id: i64,
    account: &MinecraftAccount,
    search_for_java: bool,
) -> LauncherResult<LaunchedGame> {
    let events = state.services.events.clone();
    let stage = |s: LaunchStage| {
        state.games.set_stage(cluster_id, s);
        events.game_stage(cluster_id, s);
    };

    stage(LaunchStage::Checking);

    let mut existing = state.clusters.get(cluster_id).await?;
    if arcadium_common::distribution::uses_native_forge(&existing.mc_version)
        && existing.mc_loader != arcadium_common::GameLoader::Forge
        && existing.uses_bundles()
    {
        state.versions.sync(&state.services).await?;
        state.bundles.sync(&state.services.content()).await?;
        crate::clusters::apply_remote_migrations(state).await?;
        existing = state.clusters.get(cluster_id).await?;
    }
    if !arcadium_common::distribution::supports_version(&existing.mc_version) {
        return Err(crate::ClusterError::InvalidVersion(format!(
            "Arcadium supports Minecraft {}.",
            arcadium_common::distribution::MINECRAFT_VERSIONS.join(", ")
        ))
        .into());
    }
    if existing.mc_version == "1.7.10" && existing.mc_loader != arcadium_common::GameLoader::Forge {
        return Err(crate::LauncherError::Minecraft(
            "This Minecraft 1.7.10 installation still uses the retired renderer. Restart Arcadium to migrate it to Forge.".into(),
        ));
    }
    if arcadium_common::distribution::uses_modern_fabric(&existing.mc_version)
        && existing.mc_loader != arcadium_common::GameLoader::Fabric
    {
        return Err(crate::LauncherError::Minecraft(format!(
            "Arcadium {} requires Fabric.",
            existing.mc_version
        )));
    }

    let components = arcadium_common::distribution::components(&existing.mc_version).ok_or_else(|| {
        crate::LauncherError::Minecraft(format!(
            "Arcadium components for Minecraft {} are not included in this build. Reinstall the launcher.",
            existing.mc_version
        ))
    })?;

    let game_dir = existing.game_dir()?;
    let user_game_dir = existing.user_game_dir()?;
    let dedicated = existing.uses_dedicated_dir();

    // Reserve shared player data before either version can start.
    let shared_user_data = !dedicated || user_game_dir != game_dir;
    if let Err(other) =
        state
            .games
            .reserve_dir(cluster_id, user_game_dir.clone(), !shared_user_data)
    {
        if shared_user_data {
            tracing::warn!(cluster_id, other, "shared game dir busy; refusing launch");
            let name = running_cluster_name(state, other).await;
            return Err(GameError::SharedDirectoryBusy(name).into());
        }
        return Err(GameError::DirectoryInUse(other).into());
    }

    match state.clusters.mark_played(cluster_id).await {
        Ok(()) => events.signal(arcadium_events::Signal::ClustersChanged),
        Err(err) => tracing::debug!(cluster_id, error = %err, "could not record the launch time"),
    }

    let progress = GroupedProgressSession::start(
        &state.services.events,
        format!("Launching {}", existing.name),
    );

    if existing.uses_bundles()
        && let Err(err) = crate::clusters::apply_bundle_java_override(
            state,
            cluster_id,
            search_for_java,
            false,
            Some(&progress),
        )
        .await
    {
        tracing::warn!(cluster_id, error = %err, "failed to apply bundle java override");
    }

    let cluster = if existing.stage == ClusterStage::Ready {
        existing
    } else {
        stage(LaunchStage::Downloading);
        match crate::clusters::prepare_cluster_locked(
            state,
            cluster_id,
            false,
            search_for_java,
            false,
            Some(&progress),
        )
        .await
        {
            Ok(cluster) => cluster,
            Err(err) => {
                progress.finish();
                stage(LaunchStage::Exited);
                return Err(err);
            }
        }
    };
    if cluster.uses_bundles()
        && let Err(err) = arcadium_content::bundles::install_cluster_bundles(
            cluster_id,
            state.bundles.as_ref(),
            Some(&progress),
            &state.services.content(),
        )
        .await
    {
        tracing::warn!(cluster_id, error = %err, "failed to install bundle content");
        if matches!(
            &err,
            arcadium_content::ContentError::RetiredBundleConflict { .. }
        ) {
            progress.finish();
            stage(LaunchStage::Exited);
            return Err(err.into());
        }
    }

    let global = state.settings.read().global_game_settings.clone();
    let profile = state.clusters.resolve_settings(&global, &cluster).await?;

    let mc_version = arcadium_common::version::normalize_mc_version_input(&cluster.mc_version);

    let (version, updated, loader_version, version_info) = {
        let mut metadata = state.metadata.lock().await;

        let (version, _index, updated) =
            resolve_minecraft_version(&mut metadata, &state.services.mc(), &mc_version).await?;

        let loader_version = get_loader_version(
            &mut metadata,
            &state.services.mc(),
            &mc_version,
            cluster.mc_loader,
            cluster.mc_loader_version.as_deref(),
        )
        .await?;

        let version_info = download_version_info(
            &state.services.mc(),
            Some(&progress),
            &version,
            cluster.mc_loader,
            loader_version.as_ref(),
            false,
        )
        .await;

        (version, updated, loader_version, version_info)
    };

    let version_info = match version_info {
        Ok(info) => info,
        Err(err) => {
            progress.finish();
            stage(LaunchStage::Exited);
            return Err(err.into());
        }
    };

    let version_name = loader_version.as_ref().map_or_else(
        || version.id.clone(),
        |lv| format!("{}-{}", version.id, lv.id),
    );

    tracing::info!(
        cluster_id,
        mc_version = %version.id,
        loader = %cluster.mc_loader,
        loader_version = loader_version.as_ref().map(|lv| lv.id.as_str()).unwrap_or("none"),
        version_name = %version_name,
        libraries = version_info.libraries.len(),
        main_class = %version_info.main_class,
        "resolved launch metadata"
    );

    let java = if let Some(runtime) = state
        .java
        .runtime_for_profile(profile.java_path.as_deref())
        .await?
    {
        runtime
    } else {
        let major = version_info
            .java_version
            .as_ref()
            .map(|v| v.major_version)
            .ok_or(GameError::MissingJavaVersion)?;

        state
            .java
            .prepare(major, search_for_java, false, None)
            .await?
    };

    match game_files_missing(&version_info, &java.os_arch, updated) {
        Ok(true) => {
            tracing::info!(cluster_id, "missing game files; repairing");
            let _ = state
                .clusters
                .set_stage(cluster_id, ClusterStage::Repairing)
                .await;
            stage(LaunchStage::Downloading);
            if let Err(err) = download_minecraft(
                &state.services.mc(),
                &progress,
                &version_info,
                &java.os_arch,
                updated,
                false,
            )
            .await
            {
                progress.finish();
                stage(LaunchStage::Exited);
                return Err(err.into());
            }
            let _ = state
                .clusters
                .set_stage(cluster_id, ClusterStage::Ready)
                .await;
        }
        Ok(false) => {}
        Err(err @ arcadium_mc::McError::NoNativesForPlatform { .. }) => {
            progress.finish();
            stage(LaunchStage::Exited);
            return Err(err.into());
        }
        Err(err) => tracing::warn!(cluster_id, error = %err, "repair check failed"),
    }

    progress.finish();

    stage(LaunchStage::Launching);

    let cwd = game_dir;
    arcadium_io::create_dir_all(&cwd).await.ok();

    if cluster.uses_bundles()
        && arcadium_common::distribution::requires_separate_game_dir(&cluster.mc_version)
    {
        let primary = paths::shared_minecraft_dir()?;
        let backups = Box::pin(crate::game::share_client_settings(&cwd, &primary))
            .await
            .map_err(|error| {
                crate::LauncherError::Minecraft(format!(
                    "Could not share Minecraft 1.8.9 client settings: {error}"
                ))
            })?;
        if !backups.is_empty() {
            tracing::info!(
                ?backups,
                "Preserved version-specific client settings before sharing the 1.8.9 settings"
            );
        }
        Box::pin(crate::game::user_data_share::share_user_data(
            &cwd,
            &primary,
            &cluster.mc_version,
        ))
        .await
        .map_err(|error| {
            crate::LauncherError::Minecraft(format!("Could not share Minecraft user data: {error}"))
        })?;
    }

    if let Err(err) = crate::game::write_allowed_symlinks(&cwd).await {
        tracing::warn!(cluster_id, error = %err, "failed to write allowed_symlinks.txt");
    }

    let custom_args = profile.launch_args.clone().unwrap_or_default();
    let loader_version_id = loader_version.as_ref().map(|lv| lv.id.as_str());

    let mods_in_cluster =
        crate::game::uses_cluster_mods_folder(cluster.mc_loader, loader_version_id, &custom_args);

    let client_jar = paths::versions_dir()?
        .join(&version_name)
        .join(format!("{version_name}.jar"));
    let natives = paths::natives_dir()?.join(&version_name);
    arcadium_io::create_dir_all(&natives).await?;
    let libraries = paths::libraries_dir()?;
    let assets = paths::assets_dir()?;

    let arg_map = version_info.arguments.clone().unwrap_or_default();

    let classpaths = arguments::classpaths(
        &libraries,
        &version_info.libraries,
        &client_jar,
        &java.os_arch,
        updated,
    )?;

    let mut jvm_args = arguments::java_arguments(
        arg_map.get(&ArgumentType::Jvm).map(Vec::as_slice),
        &natives,
        &libraries,
        &classpaths,
        &version_name,
        profile
            .mem_max
            .unwrap_or_else(arcadium_common::default_mem_max),
        profile.launch_args.clone().unwrap_or_default(),
        &java.os_arch,
        java.major,
    )?;

    let mods_dir = paths::cluster_mods_dir(&cluster.folder_name)?;
    check_optimization_mods(&cwd, &mods_dir, mods_in_cluster, &jvm_args).await?;
    if let Err(err) =
        crate::game::materialize_content(&state.services, &cluster, &cwd, mods_in_cluster).await
    {
        tracing::warn!(cluster_id, error = %err, "failed to materialize cluster content");
    }
    let nvidium_gpu = if cluster.uses_bundles() {
        match super::nvidium::prepare(
            &state.services,
            &cluster,
            &user_game_dir,
            &cwd,
            mods_in_cluster,
        )
        .await
        {
            Ok(gpu) => gpu,
            Err(error) => {
                tracing::warn!(%error, "Nvidium recommendation did not complete");
                events
                    .notify("Could not enable Nvidium")
                    .body(error.to_string())
                    .error()
                    .send();
                None
            }
        }
    } else {
        None
    };
    remove_retired_hyinfo(&cwd, &mods_dir, RETIRED_HYINFO_SHA1).await?;
    if !dedicated {
        crate::game::link_cluster_logs(&cluster, &cwd).await;
    }
    jvm_args.push(format!(
        "-Darcadium.launcherExecutable={}",
        std::env::current_exe()?.display()
    ));
    jvm_args.push(format!(
        "-Darcadium.launcherDataDir={}",
        paths::config_dir()?.display()
    ));
    if std::env::var_os("ARCADIUM_USAGE_ENDPOINT").is_some() {
        let endpoint = crate::usage_presence::configured_endpoint()
            .map(|url| url.as_str().trim_end_matches('/').to_owned())
            .unwrap_or_default();
        jvm_args.push(format!("-Darcadium.usageEndpoint={endpoint}"));
    }
    for name in [
        "ArcadiumVulkan-1.8.9.jar",
        "ArcadiumVulkanGameTest-0.2.6.jar",
    ] {
        for folder in [&mods_dir, &cwd.join("mods")] {
            match tokio::fs::remove_file(folder.join(name)).await {
                Ok(()) => {}
                Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
                Err(error) => return Err(error.into()),
            }
        }
    }
    #[cfg(target_os = "macos")]
    {
        let resources = arcadium_common::install_layout::resources(&std::env::current_exe()?);
        let icon = resources.join("game-icon.png");
        jvm_args.push("-Xdock:name=Arcadium Client".to_string());
        if icon.is_file() {
            jvm_args.push(format!("-Xdock:icon={}", icon.display()));
        }
        if !jvm_args
            .iter()
            .any(|argument| argument == "-XstartOnFirstThread")
        {
            jvm_args.push("-XstartOnFirstThread".to_string());
        }
    }
    let components_dir = std::env::var_os("ARCADIUM_COMPONENTS_DIR")
        .map(std::path::PathBuf::from)
        .unwrap_or_else(|| {
            arcadium_common::install_layout::resources(&std::env::current_exe().unwrap_or_default())
                .join("components")
        });
    let mut native_bootstrap = None;
    for component in components {
        let file = &component.file;
        let sha1 = &component.sha1;
        if component.bootstrap {
            let source = components_dir.join(file);
            if !arcadium_net::matches_on_disk(&source, sha1).await {
                return Err(crate::LauncherError::Minecraft(format!(
                    "Arcadium bootstrap is missing or damaged: {file}. Reinstall the launcher."
                )));
            }
            native_bootstrap = Some(source);
            continue;
        }
        let destination = mods_dir.join(file);
        if !arcadium_net::matches_on_disk(&destination, sha1).await {
            let source = components_dir.join(file);
            if !arcadium_net::matches_on_disk(&source, sha1).await {
                return Err(crate::LauncherError::Minecraft(format!(
                    "Arcadium component is missing or damaged: {file}. Reinstall the launcher."
                )));
            }
            arcadium_io::write_atomic(&destination, arcadium_io::read(&source).await?).await?;
        }
    }
    if cluster.mc_version == "1.8.9" {
        let branding = mods_dir.join("ArcadiumBrand-1.8.9.jar");
        if !arcadium_net::matches_on_disk(&branding, arcadium_common::distribution::BRANDING_SHA1)
            .await
        {
            arcadium_io::write_atomic(&branding, arcadium_common::distribution::BRANDING_MOD)
                .await?;
        }
    }
    if let Some(arg) = crate::game::mods_folder_argument(
        cluster.mc_loader,
        loader_version_id,
        &custom_args,
        &mods_dir,
    ) {
        tracing::debug!(cluster_id, mods_dir = %mods_dir.display(), "redirecting fabric mods folder");
        jvm_args.push(arg);
    }

    let native_forge = arcadium_common::distribution::uses_native_forge(&cluster.mc_version);
    if native_forge {
        let retired = super::native_forge::retire_ornithe_mods(&cwd, &mods_dir).await?;
        if !retired.is_empty() {
            tracing::info!(
                ?retired,
                "Preserved retired Ornithe components outside the Forge mods folder"
            );
        }
        super::native_forge::prepare_mods(&cwd, &mods_dir).await?;
        jvm_args.push(format!("-Darcadium.runtimeGameDir={}", cwd.display()));
        jvm_args.push(format!(
            "-Darcadium.userGameDir={}",
            user_game_dir.display()
        ));
        configure_forge_bootstrap(
            &mut jvm_args,
            native_bootstrap.as_deref().ok_or_else(|| {
                crate::LauncherError::Minecraft("The native Forge bootstrap is missing.".into())
            })?,
        )?;
    }
    let main_class = if !native_forge
        && cluster.mc_loader == arcadium_common::GameLoader::Ornithe
        && arcadium_common::distribution::supports_forge_compat(&cluster.mc_version)
    {
        configure_forge_bootstrap(
            &mut jvm_args,
            &mods_dir.join("ArcadiumForgeCompat-1.8.9.jar"),
        )?;
        "io.github.arcadiumclient.forgecompat.ArcadiumLaunch"
    } else {
        version_info.main_class.as_str()
    };

    if !arcadium_common::distribution::supports_forge_compat(&cluster.mc_version) {
        reject_unsupported_forge_mods(&cwd, &mods_dir, &cluster.mc_version).await?;
    }

    let mut mc_args = arguments::minecraft_arguments(
        arg_map.get(&ArgumentType::Game).map(Vec::as_slice),
        version_info.minecraft_arguments.as_deref(),
        &account.access_token,
        &account.username,
        account.id,
        &version.id,
        &version_info.asset_index.id,
        if native_forge { &cwd } else { &user_game_dir },
        &assets,
        version.type_,
        profile.resolution.unwrap_or_default(),
        &java.os_arch,
    )?;
    arguments::append_profile_game_arguments(
        &mut mc_args,
        profile.force_fullscreen,
        profile.game_args.as_deref(),
    );

    if let Some(reason) = run_hook(profile.hook_pre.as_deref(), &cwd).await {
        events
            .notify("Pre-launch command failed")
            .body(reason)
            .error()
            .send();
    }

    check_optimization_mods(&cwd, &mods_dir, mods_in_cluster, &jvm_args).await?;

    tracing::info!(
        cluster_id,
        java = %java.absolute_path,
        jvm_args = jvm_args.len(),
        mc_args = mc_args.len(),
        cwd = %cwd.display(),
        "spawning minecraft process"
    );
    tracing::debug!(cluster_id, ?jvm_args, main_class, "jvm arguments");

    let (mut command, wrapper) = base_command(&profile, &java.absolute_path);

    if let Some(gpu) = profile.gpu().or(nvidium_gpu.as_deref()) {
        crate::game::gpu::select(&mut command, &java.absolute_path, gpu).await;
    }

    apply_env(&mut command, &profile);

    command
        .args(jvm_args)
        .arg(main_class)
        .args(mc_args)
        .current_dir(&cwd);

    let header = format!(
        "Arcadium {} · {} · {version_name} · Java {}\n\n{}\n\n",
        env!("CARGO_PKG_VERSION"),
        cluster.name,
        java.major,
        command_line(command.as_std(), main_class),
    );

    let log_path = arcadium_cluster::logs::cluster_output_log(&cluster)?;
    if let Some(parent) = log_path.parent() {
        arcadium_io::create_dir_all(parent).await.ok();
    }

    // A file not a pipe a pipe would die with the launcher and take the game's
    // next stdout write down with it
    // The cloned handle shares the file offset
    // so stdout and stderr interleave instead of overwriting
    let handles = match tokio::fs::File::create(&log_path).await {
        Ok(mut out) => {
            if let Err(err) = out.write_all(header.as_bytes()).await {
                tracing::warn!(cluster_id, error = %err, "failed to write the launch header");
            }
            match out.try_clone().await {
                Ok(err) => Ok((out.into_std().await, err.into_std().await)),
                Err(err) => Err(err),
            }
        }
        Err(err) => Err(err),
    };

    match handles {
        Ok((out, err)) => {
            command.stdout(Stdio::from(out)).stderr(Stdio::from(err));
        }
        Err(err) => {
            tracing::warn!(cluster_id, error = %err, "failed to open game log; discarding output");
            command.stdout(Stdio::null()).stderr(Stdio::null());
        }
    }
    command.stdin(Stdio::null());
    detach(&mut command);

    let mut child = command.spawn().map_err(|err| match &wrapper {
        Some(program) => GameError::wrapper_spawn(program, &err),
        None => GameError::Spawn(err.to_string()),
    })?;
    let pid = child.id();

    stage(LaunchStage::Running);
    state.games.set_pid(cluster_id, pid);
    state.discord.set_presence(Presence::Playing {
        cluster: cluster.name.clone(),
        mc_version: cluster.mc_version.clone(),
    });

    let recorder = SessionRecorder::start(
        state,
        cluster_id,
        profile
            .mem_max
            .unwrap_or_else(arcadium_common::default_mem_max),
        &java,
    )
    .await;

    // Pinned to the session row so that if the launcher exits first the next
    // start can tell whether the game is still playing
    if let (Some(recorder), Some(pid)) = (recorder.as_ref(), pid) {
        recorder
            .record_process(pid, crate::game::process_start_time(pid))
            .await;
    }

    let started_at = recorder
        .as_ref()
        .and_then(SessionRecorder::started_at)
        .unwrap_or_else(Utc::now);
    let crash_watch = crate::game::diagnosis::CrashWatch::new();
    let tail = spawn_log_tail(
        cluster_id,
        log_path,
        events.clone(),
        recorder.clone(),
        crash_watch.clone(),
    );

    let (kill_tx, kill_rx) = tokio::sync::oneshot::channel::<()>();
    state.games.register_kill(cluster_id, kill_tx);

    let state = Arc::clone(state);
    let post_hook = profile.hook_post.clone();
    tokio::spawn(async move {
        let cluster = cluster;
        let (status, stopped) = tokio::select! {
            status = child.wait() => (status, false),
            _ = kill_rx => {
                let _ = child.start_kill();
                (child.wait().await, true)
            }
        };

        tail.stop().await;

        let outcome = match status {
            Ok(status) => Exit::Observed {
                code: status.code().map(i64::from),
                success: status.success() || stopped || killed(&status),
                display: status.to_string(),
            },
            Err(err) => Exit::Failed(err.to_string()),
        };

        finalize_session(
            &state,
            &cluster,
            &cwd,
            dedicated,
            post_hook.as_deref(),
            recorder,
            SessionEnd {
                started_at,
                ended_at: Utc::now(),
                outcome,
                owns_slot: true,
                diagnosis: crash_watch.take(),
            },
        )
        .await;
    });

    Ok(LaunchedGame { cluster_id, pid })
}

async fn reject_unsupported_forge_mods(
    cwd: &Path,
    mods_dir: &Path,
    mc_version: &str,
) -> LauncherResult<()> {
    let mut roots = vec![cwd.join("mods"), mods_dir.to_path_buf()];
    roots.extend(roots.clone().into_iter().map(|root| root.join(mc_version)));
    roots.sort();
    roots.dedup();
    for root in roots {
        let mut entries = match tokio::fs::read_dir(&root).await {
            Ok(entries) => entries,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => continue,
            Err(error) => return Err(error.into()),
        };
        while let Some(entry) = entries.next_entry().await? {
            let path = entry.path();
            if !path
                .extension()
                .is_some_and(|extension| extension.eq_ignore_ascii_case("jar"))
            {
                continue;
            }
            if arcadium_content::packages::local_manifest::read_jar_loader(&path).await
                == Some(arcadium_common::GameLoader::Forge)
            {
                return Err(crate::LauncherError::Minecraft(format!(
                    "{} is a Forge mod. Forge mod compatibility is only available for Minecraft 1.8.9. Remove it from the Minecraft {mc_version} mods folder.",
                    entry.file_name().to_string_lossy()
                )));
            }
        }
    }
    Ok(())
}

fn configure_forge_bootstrap(jvm_args: &mut Vec<String>, component: &Path) -> LauncherResult<()> {
    let mut classpath = None;
    for (index, argument) in jvm_args.iter().enumerate() {
        if matches!(argument.as_str(), "-cp" | "-classpath" | "--class-path") {
            classpath = Some((index + 1, false));
        } else if argument.starts_with("--class-path=") {
            classpath = Some((index, true));
        }
    }
    let Some((index, inline)) = classpath else {
        return Err(crate::LauncherError::Minecraft(
            "The Minecraft classpath is missing.".into(),
        ));
    };
    let Some(value) = jvm_args.get(index) else {
        return Err(crate::LauncherError::Minecraft(
            "The Minecraft classpath is incomplete.".into(),
        ));
    };
    let raw = if inline {
        value.trim_start_matches("--class-path=")
    } else {
        value.as_str()
    };
    let mut paths: Vec<_> = std::env::split_paths(raw).collect();
    if !paths.iter().any(|path| path == component) {
        paths.push(component.to_path_buf());
    }
    let joined = std::env::join_paths(paths).map_err(|error| {
        crate::LauncherError::Minecraft(format!(
            "The Forge compatibility classpath is invalid: {error}"
        ))
    })?;
    let joined = joined.to_str().ok_or_else(|| {
        crate::LauncherError::Minecraft("The Forge compatibility path is not valid Unicode.".into())
    })?;
    jvm_args[index] = if inline {
        format!("--class-path={joined}")
    } else {
        joined.to_owned()
    };
    for argument in [
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED",
    ] {
        if !jvm_args.iter().any(|value| value == argument) {
            jvm_args.push(argument.to_owned());
        }
    }
    Ok(())
}

async fn check_optimization_mods(
    cwd: &Path,
    mods_dir: &Path,
    mods_in_cluster: bool,
    jvm_args: &[String],
) -> LauncherResult<()> {
    let mut roots = vec![cwd.join("mods")];
    if mods_in_cluster {
        roots.push(mods_dir.to_path_buf());
    }
    if let Some(folder) = jvm_args
        .iter()
        .rev()
        .find_map(|argument| argument.strip_prefix("-Dfabric.modsFolder="))
    {
        roots.push(cwd.join(folder));
    }
    arcadium_content::packages::optimization_mods::check_mod_directories(&roots).await?;
    Ok(())
}

const RETIRED_HYINFO_FILE: &str = "HyInfo-0.1.0-1.8.9_ornithe.jar";
const RETIRED_HYINFO_SHA1: &str = "1d87e2b620058addb763189c1c4528e021cf5925";

async fn remove_retired_hyinfo(
    cwd: &Path,
    mods_dir: &Path,
    expected_sha1: &str,
) -> LauncherResult<()> {
    for root in [cwd.join("mods"), mods_dir.to_path_buf()] {
        for folder in [root.clone(), root.join("1.8.9")] {
            let path = folder.join(RETIRED_HYINFO_FILE);
            if !arcadium_net::matches_on_disk(&path, expected_sha1).await {
                continue;
            }
            // Unlink this entry, leaving a managed symlink or hard link's cache target intact.
            match tokio::fs::remove_file(&path).await {
                Ok(()) => tracing::info!(path = %path.display(), "removed retired bundled HyInfo"),
                Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
                Err(error) => return Err(error.into()),
            }
        }
    }
    Ok(())
}

#[cfg(test)]
mod retired_mod_tests {
    use super::{RETIRED_HYINFO_FILE, RETIRED_HYINFO_SHA1, remove_retired_hyinfo};
    use std::path::Path;

    const ORIGINAL: &[u8] = b"retired bundled mod fixture";

    async fn write(path: &Path, bytes: &[u8]) {
        tokio::fs::create_dir_all(path.parent().unwrap())
            .await
            .unwrap();
        tokio::fs::write(path, bytes).await.unwrap();
    }

    #[tokio::test]
    async fn removes_only_verified_names_in_both_mod_roots_and_version_subfolders() {
        let root = arcadium_io::testing::ScratchDir::new("retire-hyinfo-roots");
        let cwd = root.join("game");
        let cluster_mods = root.join("cluster/mods");
        let folders = [
            cwd.join("mods"),
            cwd.join("mods/1.8.9"),
            cluster_mods.clone(),
            cluster_mods.join("1.8.9"),
        ];
        for folder in &folders {
            write(&folder.join(RETIRED_HYINFO_FILE), ORIGINAL).await;
            write(&folder.join("HyBridge.jar"), ORIGINAL).await;
        }
        let config = cwd.join("config/hyinfo.json");
        write(&config, b"user settings").await;
        let hash = arcadium_io::sha1_bytes(ORIGINAL);
        remove_retired_hyinfo(&cwd, &cluster_mods, &hash)
            .await
            .unwrap();
        remove_retired_hyinfo(&cwd, &cluster_mods, &hash)
            .await
            .unwrap();
        for folder in folders {
            assert!(!folder.join(RETIRED_HYINFO_FILE).exists());
            assert_eq!(
                tokio::fs::read(folder.join("HyBridge.jar")).await.unwrap(),
                ORIGINAL
            );
        }
        assert_eq!(tokio::fs::read(config).await.unwrap(), b"user settings");
    }

    #[tokio::test]
    async fn preserves_modified_files_disabled_copies_and_other_versions() {
        let root = arcadium_io::testing::ScratchDir::new("retire-hyinfo-preserve");
        let modified = root.join("mods").join(RETIRED_HYINFO_FILE);
        let disabled = root
            .join("mods")
            .join(format!("{RETIRED_HYINFO_FILE}.disabled"));
        let other_version = root.join("mods/1.12.2").join(RETIRED_HYINFO_FILE);
        write(&modified, b"user modified jar").await;
        write(&disabled, ORIGINAL).await;
        write(&other_version, ORIGINAL).await;
        remove_retired_hyinfo(
            root.path(),
            &root.join("cluster/mods"),
            &arcadium_io::sha1_bytes(ORIGINAL),
        )
        .await
        .unwrap();
        assert_eq!(
            tokio::fs::read(modified).await.unwrap(),
            b"user modified jar"
        );
        assert_eq!(tokio::fs::read(disabled).await.unwrap(), ORIGINAL);
        assert_eq!(tokio::fs::read(other_version).await.unwrap(), ORIGINAL);
    }

    #[tokio::test]
    async fn unlinks_managed_files_without_deleting_the_artifact_cache() {
        let root = arcadium_io::testing::ScratchDir::new("retire-hyinfo-links");
        let artifact = root.join("cache/artifact.jar");
        write(&artifact, ORIGINAL).await;
        let cluster_mods = root.join("cluster/mods");
        let links = [
            root.join("mods").join(RETIRED_HYINFO_FILE),
            cluster_mods.join("1.8.9").join(RETIRED_HYINFO_FILE),
        ];
        for path in &links {
            tokio::fs::create_dir_all(path.parent().unwrap())
                .await
                .unwrap();
            arcadium_io::symlink_file(&artifact, path).await.unwrap();
        }
        remove_retired_hyinfo(
            root.path(),
            &cluster_mods,
            &arcadium_io::sha1_bytes(ORIGINAL),
        )
        .await
        .unwrap();
        for path in links {
            assert!(tokio::fs::symlink_metadata(path).await.is_err());
        }
        assert_eq!(tokio::fs::read(artifact).await.unwrap(), ORIGINAL);
    }

    #[tokio::test]
    async fn preserves_managed_links_when_their_target_has_changed() {
        let root = arcadium_io::testing::ScratchDir::new("retire-hyinfo-link-changed");
        let artifact = root.join("cache/artifact.jar");
        write(&artifact, b"modified artifact").await;
        let link = root.join("mods").join(RETIRED_HYINFO_FILE);
        tokio::fs::create_dir_all(link.parent().unwrap())
            .await
            .unwrap();
        arcadium_io::symlink_file(&artifact, &link).await.unwrap();
        remove_retired_hyinfo(
            root.path(),
            &root.join("cluster/mods"),
            &arcadium_io::sha1_bytes(ORIGINAL),
        )
        .await
        .unwrap();
        assert!(tokio::fs::symlink_metadata(link).await.is_ok());
        assert_eq!(
            tokio::fs::read(artifact).await.unwrap(),
            b"modified artifact"
        );
    }

    #[cfg(unix)]
    #[tokio::test]
    async fn preserves_dangling_symlinks_without_hashable_bytes() {
        let root = arcadium_io::testing::ScratchDir::new("retire-hyinfo-link-missing");
        let link = root.join("mods").join(RETIRED_HYINFO_FILE);
        tokio::fs::create_dir_all(link.parent().unwrap())
            .await
            .unwrap();
        tokio::fs::symlink(root.join("missing.jar"), &link)
            .await
            .unwrap();
        remove_retired_hyinfo(root.path(), &root.join("cluster/mods"), RETIRED_HYINFO_SHA1)
            .await
            .unwrap();
        assert!(
            tokio::fs::symlink_metadata(link)
                .await
                .unwrap()
                .file_type()
                .is_symlink()
        );
    }

    #[tokio::test]
    async fn production_hash_preserves_unknown_bytes_and_directories() {
        assert_eq!(
            RETIRED_HYINFO_SHA1,
            "1d87e2b620058addb763189c1c4528e021cf5925"
        );
        let root = arcadium_io::testing::ScratchDir::new("retire-hyinfo-unknown");
        let unknown = root.join("mods").join(RETIRED_HYINFO_FILE);
        let directory = root.join("cluster/mods").join(RETIRED_HYINFO_FILE);
        write(&unknown, ORIGINAL).await;
        write(&directory.join("user-file.txt"), ORIGINAL).await;
        remove_retired_hyinfo(root.path(), &root.join("cluster/mods"), RETIRED_HYINFO_SHA1)
            .await
            .unwrap();
        assert_eq!(tokio::fs::read(unknown).await.unwrap(), ORIGINAL);
        assert_eq!(
            tokio::fs::read(directory.join("user-file.txt"))
                .await
                .unwrap(),
            ORIGINAL
        );
    }
}

#[cfg(test)]
mod optimization_tests {
    use super::check_optimization_mods;
    use std::path::Path;

    async fn optimizer(path: &Path) {
        tokio::fs::create_dir_all(path.parent().unwrap())
            .await
            .unwrap();
        let file = tokio::fs::File::create(path).await.unwrap();
        let mut writer = async_zip::tokio::write::ZipFileWriter::with_tokio(file);
        writer
            .write_entry_whole(
                async_zip::ZipEntryBuilder::new(
                    "META-INF/MANIFEST.MF".into(),
                    async_zip::Compression::Stored,
                ),
                b"TweakClass: optifine.OptiFineTweaker\n",
            )
            .await
            .unwrap();
        let mut output = writer.close().await.unwrap();
        futures_lite::io::AsyncWriteExt::flush(&mut output)
            .await
            .unwrap();
    }

    #[tokio::test]
    async fn the_last_fabric_folder_property_is_resolved_against_the_game_directory() {
        let dir = arcadium_io::testing::ScratchDir::new("launch-optimizer-custom");
        optimizer(&dir.join("old-mods/renamed.jar")).await;
        let args = vec![
            "-Dfabric.modsFolder=old-mods".into(),
            "-Dfabric.modsFolder=active mods".into(),
        ];
        check_optimization_mods(dir.path(), &dir.join("cluster-mods"), false, &args)
            .await
            .unwrap();
        optimizer(&dir.join("active mods/1.8.9/renamed.JAR")).await;
        let error = check_optimization_mods(dir.path(), &dir.join("cluster-mods"), false, &args)
            .await
            .unwrap_err();
        assert!(error.to_string().contains("Remove OptiFine to continue."));
    }

    #[tokio::test]
    async fn redirected_native_mods_do_not_hide_game_directory_forge_mods() {
        let dir = arcadium_io::testing::ScratchDir::new("launch-optimizer-both");
        optimizer(&dir.join("mods/1.8.9/direct.jar")).await;
        let error = check_optimization_mods(dir.path(), &dir.join("cluster-mods"), true, &[])
            .await
            .unwrap_err();
        assert!(error.to_string().contains("Remove OptiFine to continue."));
        tokio::fs::remove_file(dir.join("mods/1.8.9/direct.jar"))
            .await
            .unwrap();
        optimizer(&dir.join("cluster-mods/renamed.jar")).await;
        assert!(
            check_optimization_mods(dir.path(), &dir.join("cluster-mods"), true, &[])
                .await
                .is_err()
        );
        check_optimization_mods(dir.path(), &dir.join("cluster-mods"), false, &[])
            .await
            .unwrap();
    }
}

#[cfg(test)]
mod legacy_mod_tests {
    use super::reject_unsupported_forge_mods;

    #[tokio::test]
    async fn legacy_forge_mods_are_rejected_without_removing_user_files() {
        let root = arcadium_io::testing::ScratchDir::new("legacy-forge-rejection");
        let directory = root.join("mods/1.7.10");
        tokio::fs::create_dir_all(&directory).await.unwrap();
        let path = directory.join("example-forge.jar");
        let file = tokio::fs::File::create(&path).await.unwrap();
        let mut writer = async_zip::tokio::write::ZipFileWriter::with_tokio(file);
        writer
            .write_entry_whole(
                async_zip::ZipEntryBuilder::new(
                    "mcmod.info".into(),
                    async_zip::Compression::Stored,
                ),
                b"[{\"modid\":\"legacy_example\",\"name\":\"Example\"}]",
            )
            .await
            .unwrap();
        let mut output = writer.close().await.unwrap();
        futures_lite::io::AsyncWriteExt::flush(&mut output)
            .await
            .unwrap();
        drop(output);
        let error =
            reject_unsupported_forge_mods(root.path(), &root.join("cluster/mods"), "1.7.10")
                .await
                .unwrap_err()
                .to_string();
        assert!(error.contains("example-forge.jar"));
        assert!(error.contains("Forge mod compatibility is only available for Minecraft 1.8.9"));
        assert!(path.is_file());
    }

    #[tokio::test]
    async fn a_fabric_jar_with_forge_metadata_is_not_misidentified() {
        let root = arcadium_io::testing::ScratchDir::new("legacy-multiloader-metadata");
        let directory = root.join("mods");
        tokio::fs::create_dir_all(&directory).await.unwrap();
        let file = tokio::fs::File::create(directory.join("fabric-with-credits.jar"))
            .await
            .unwrap();
        let mut writer = async_zip::tokio::write::ZipFileWriter::with_tokio(file);
        for (name, bytes) in [
            (
                "fabric.mod.json",
                b"{\"schemaVersion\":1,\"id\":\"example_fabric\",\"version\":\"1.0\"}".as_slice(),
            ),
            ("mcmod.info", b"[]".as_slice()),
        ] {
            writer
                .write_entry_whole(
                    async_zip::ZipEntryBuilder::new(name.into(), async_zip::Compression::Stored),
                    bytes,
                )
                .await
                .unwrap();
        }
        let mut output = writer.close().await.unwrap();
        futures_lite::io::AsyncWriteExt::flush(&mut output)
            .await
            .unwrap();
        drop(output);
        reject_unsupported_forge_mods(root.path(), &root.join("cluster/mods"), "1.7.10")
            .await
            .unwrap();
    }
}

#[cfg(test)]
mod forge_bootstrap_tests {
    use super::configure_forge_bootstrap;
    use std::path::{Path, PathBuf};

    #[test]
    fn appends_to_the_effective_classpath_and_preserves_other_arguments() {
        let first = std::env::join_paths(["old.jar"])
            .unwrap()
            .to_str()
            .unwrap()
            .to_owned();
        let effective = std::env::join_paths(["minecraft.jar", "loader.jar"])
            .unwrap()
            .to_str()
            .unwrap()
            .to_owned();
        let mut arguments = vec![
            "-Xmx4G".into(),
            "-cp".into(),
            first.clone(),
            "--class-path".into(),
            effective,
            "-Dexample=value".into(),
        ];
        configure_forge_bootstrap(&mut arguments, Path::new("Arcadium Forge Compat.jar")).unwrap();
        assert_eq!(arguments[0], "-Xmx4G");
        assert_eq!(arguments[2], first);
        assert_eq!(arguments[5], "-Dexample=value");
        let paths: Vec<_> = std::env::split_paths(&arguments[4]).collect();
        assert_eq!(
            paths,
            vec![
                PathBuf::from("minecraft.jar"),
                PathBuf::from("loader.jar"),
                PathBuf::from("Arcadium Forge Compat.jar")
            ]
        );
        configure_forge_bootstrap(&mut arguments, Path::new("Arcadium Forge Compat.jar")).unwrap();
        assert_eq!(std::env::split_paths(&arguments[4]).count(), 3);
        assert_eq!(
            arguments
                .iter()
                .filter(|value| value.as_str() == "--add-opens=java.base/java.lang=ALL-UNNAMED")
                .count(),
            1
        );
    }

    #[test]
    fn supports_inline_classpaths_and_rejects_missing_values() {
        let mut arguments = vec!["--class-path=minecraft.jar".into()];
        configure_forge_bootstrap(&mut arguments, Path::new("compat.jar")).unwrap();
        assert!(arguments[0].starts_with("--class-path="));
        let paths: Vec<_> =
            std::env::split_paths(arguments[0].trim_start_matches("--class-path=")).collect();
        assert_eq!(paths.len(), 2);
        assert!(configure_forge_bootstrap(&mut vec![], Path::new("compat.jar")).is_err());
        assert!(
            configure_forge_bootstrap(&mut vec!["-cp".into()], Path::new("compat.jar")).is_err()
        );
    }
}

async fn running_cluster_name(state: &Arc<LauncherState>, cluster_id: i64) -> String {
    state
        .clusters
        .get(cluster_id)
        .await
        .map_or_else(|_| format!("Cluster {cluster_id}"), |cluster| cluster.name)
}

/// Cuts the game loose from the launcher's process group/console so signals
/// aimed at the launcher (Ctrl-C console close) don't reach the game
fn detach(command: &mut Command) {
    #[cfg(unix)]
    {
        use std::os::unix::process::CommandExt;
        command.as_std_mut().process_group(0);
    }

    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const DETACHED_PROCESS: u32 = 0x0000_0008;
        const CREATE_NEW_PROCESS_GROUP: u32 = 0x0000_0200;
        command
            .as_std_mut()
            .creation_flags(DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP);
    }
}

#[cfg(unix)]
fn killed(status: &std::process::ExitStatus) -> bool {
    use std::os::unix::process::ExitStatusExt;
    const SIGKILL: i32 = 9;
    const JVM_SIGTERM_EXIT: i32 = 143;
    status.signal() == Some(SIGKILL) || status.code() == Some(JVM_SIGTERM_EXIT)
}

#[cfg(not(unix))]
fn killed(_status: &std::process::ExitStatus) -> bool {
    false
}

pub(crate) enum Exit {
    Observed {
        code: Option<i64>,
        success: bool,
        display: String,
    },
    Failed(String),
    /// Game exited while the launcher was closed time recovered from the log
    /// exit code unrecoverable
    Inferred,
}

pub(crate) struct SessionEnd {
    pub started_at: DateTime<Utc>,
    pub ended_at: DateTime<Utc>,
    pub outcome: Exit,
    /// False for a stale session recovered from the database whose cluster has a
    /// live game book its playtime but clearing the slot would report the live
    /// game as exited
    pub owns_slot: bool,
    /// `None` for a clean exit an unrecognised crash or a session recovered
    /// after the fact with no log watched
    pub diagnosis: Option<crate::game::diagnosis::CrashDiagnosis>,
}

/// Shared by the live exit path and by recovery of sessions that outlived the
/// launcher
pub(crate) async fn finalize_session(
    state: &Arc<LauncherState>,
    cluster: &Cluster,
    cwd: &Path,
    dedicated: bool,
    post_hook: Option<&str>,
    recorder: Option<SessionRecorder>,
    end: SessionEnd,
) {
    let cluster_id = cluster.id;
    let played = (end.ended_at - end.started_at).to_std().unwrap_or_default();

    if end.owns_slot {
        state.games.remove(cluster_id);
        state
            .services
            .events
            .game_stage(cluster_id, LaunchStage::Exited);

        if state.games.running_ids().is_empty() {
            state.discord.set_presence(Presence::Idle);
        }
    }

    if played > Duration::from_secs(1) {
        let _ = state.clusters.add_playtime(cluster_id, played).await;
    }

    if let Some(recorder) = recorder {
        let code = match &end.outcome {
            Exit::Observed { code, .. } => *code,
            Exit::Failed(_) | Exit::Inferred => None,
        };
        recorder.finish_at(&end.ended_at.to_rfc3339(), code).await;
    }

    if let Some(reason) = run_hook(post_hook, cwd).await {
        state
            .services
            .events
            .notify("Post-exit command failed")
            .body(reason)
            .error()
            .send();
    }

    if dedicated {
        // The folder stays materialized so it remains a real Minecraft directory
        // for external tools adopting drop-ins now keeps the UI right on close
        crate::game::import_manual_content(&state.services, cluster, cwd).await;
    } else {
        if let Err(err) = crate::game::dematerialize_content(&state.services, cluster, cwd).await {
            tracing::warn!(cluster_id, error = %err, "failed to clear shared content on exit");
        }
        crate::game::unlink_cluster_logs(cwd).await;
    }

    let name = &cluster.name;
    let crashed = !matches!(end.outcome, Exit::Observed { success: true, .. });

    match end.outcome {
        Exit::Observed { success: true, .. } => {}
        Exit::Observed { display, .. } => state
            .services
            .events
            .notify("Game crashed")
            .body(format!("{name} exited with {display}"))
            .error()
            .send(),
        Exit::Failed(err) => state
            .services
            .events
            .notify("Game error")
            .body(format!("{name}: {err}"))
            .error()
            .send(),
        // Nothing was watching so there is no crash to report
        Exit::Inferred => {}
    }

    // Only when the game actually died a `ZipException` it recovered from is not
    // worth interrupting a finished session over
    if crashed && let Some(diagnosis) = end.diagnosis {
        offer_repair(state, cluster_id, &diagnosis).await;
    }
}

/// Asks rather than acting verification can re-download much of the game too
/// much to start unprompted
/// The offer returns on the next crash
#[tracing::instrument(skip(state, diagnosis), level = "debug")]
pub async fn offer_repair(
    state: &Arc<LauncherState>,
    cluster_id: i64,
    diagnosis: &crate::game::diagnosis::CrashDiagnosis,
) {
    enum Answer {
        Verify,
    }

    let answer = state
        .services
        .events
        .ask(
            arcadium_events::Prompt::new("A damaged file crashed the game", diagnosis.body())
                .option(
                    arcadium_events::Choice::primary("verify", "Verify and repair"),
                    Answer::Verify,
                )
                .dismiss("Not now"),
        )
        .await;

    match answer {
        Ok(Some(_)) => {}
        // Dismissed or nobody there to ask neither is consent to re-download
        Ok(None) => {
            tracing::info!(cluster_id, "user declined the post-crash repair");
            return;
        }
        Err(err) => {
            tracing::warn!(cluster_id, "could not offer a post-crash repair: {err}");
            return;
        }
    }

    match crate::verify::verify_cluster_files(state, cluster_id).await {
        Ok(report) => {
            state
                .services
                .events
                .notify("Repair complete")
                .body(report.summary())
                .send();
        }
        Err(err) => {
            tracing::error!(cluster_id, "post-crash repair failed: {err:#}");
            state
                .services
                .events
                .notify("Repair failed")
                .body(err.to_string())
                .error()
                .send();
        }
    }
}

fn base_command(profile: &GameSettingsProfile, java_path: &str) -> (Command, Option<String>) {
    let Some(wrapper) = profile
        .hook_wrapper
        .as_deref()
        .map(str::trim)
        .filter(|hook| !hook.is_empty())
    else {
        return (Command::new(java_path), None);
    };

    let mut split = wrapper.split_whitespace();
    let Some(program) = split.next() else {
        return (Command::new(java_path), None);
    };

    let mut command = Command::new(program);
    command.args(split);
    command.arg(java_path);
    (command, Some(program.to_string()))
}

fn command_line(command: &std::process::Command, main_class: &str) -> String {
    #[cfg(not(windows))]
    const CONTINUATION: &str = " \\\n  ";
    #[cfg(windows)]
    const CONTINUATION: &str = " `\n  ";

    let mut out = String::new();
    if let Some(dir) = command.get_current_dir() {
        let _ = writeln!(out, "cd {}", shell_quote(&dir.to_string_lossy()));
    }

    for (key, value) in command.get_envs() {
        let (key, Some(value)) = (key.to_string_lossy(), value) else {
            continue;
        };
        let value = shell_quote(&value.to_string_lossy());
        #[cfg(not(windows))]
        let _ = write!(out, "{key}={value} ");
        #[cfg(windows)]
        let _ = writeln!(out, "$env:{key} = {value}");
    }

    #[cfg(windows)]
    {
        out += "& ";
    }
    out += &shell_quote(&command.get_program().to_string_lossy());

    for arg in command.get_args() {
        let arg = arg.to_string_lossy();
        let arg = arcadium_cluster::logs::censor(&arg);
        out += if arg.starts_with('-') || arg == main_class {
            CONTINUATION
        } else {
            " "
        };
        out += &shell_quote(&arg);
    }

    out
}

#[cfg(not(windows))]
fn shell_quote(arg: &str) -> String {
    let plain = !arg.is_empty()
        && arg
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b"_-./=:,+@%".contains(&b));
    if plain {
        arg.to_string()
    } else {
        format!("'{}'", arg.replace('\'', r"'\''"))
    }
}

#[cfg(windows)]
fn shell_quote(arg: &str) -> String {
    format!("'{}'", arg.replace('\'', "''"))
}

fn apply_env(command: &mut Command, profile: &GameSettingsProfile) {
    command.env_remove("_JAVA_OPTIONS");

    if let Some(env) = &profile.launch_env {
        for pair in env.split_whitespace() {
            if let Some((key, value)) = pair.split_once('=') {
                command.env(key, value);
            }
        }
    }
}

#[tracing::instrument(skip(cwd), fields(hook), level = "debug")]
async fn run_hook(hook: Option<&str>, cwd: &Path) -> Option<String> {
    let hook = hook.map(str::trim).filter(|h| !h.is_empty())?;

    #[cfg(windows)]
    let mut command = {
        let mut c = Command::new("cmd");
        c.args(["/C", hook]);
        c
    };
    #[cfg(not(windows))]
    let mut command = {
        let mut c = Command::new("sh");
        c.args(["-c", hook]);
        c
    };

    command.current_dir(cwd);
    command.stderr(Stdio::piped());
    command.stdin(Stdio::null());
    arcadium_common::process::no_window(command.as_std_mut());

    let mut child = match command.spawn() {
        Ok(child) => child,
        Err(err) => {
            tracing::warn!("hook '{hook}' could not start: {err}");
            return Some(format!("The command shell could not be started: {err}"));
        }
    };

    let collector = child.stderr.take().map(|mut pipe| {
        tokio::spawn(async move {
            let mut buf = Vec::new();
            let _ = pipe.read_to_end(&mut buf).await;
            buf
        })
    });

    let status = match child.wait().await {
        Ok(status) => status,
        Err(err) => {
            if let Some(collector) = collector {
                collector.abort();
            }
            tracing::warn!("hook '{hook}' could not be waited on: {err}");
            return Some(format!("The command could not be waited on: {err}"));
        }
    };

    if status.success() {
        if let Some(collector) = collector {
            collector.abort();
        }
        return None;
    }

    let detail = match collector {
        Some(mut collector) => {
            match tokio::time::timeout(Duration::from_millis(200), &mut collector).await {
                Ok(Ok(buf)) => stderr_detail(&buf),
                _ => {
                    collector.abort();
                    None
                }
            }
        }
        None => None,
    };

    let message = match (detail, status.code()) {
        (Some(detail), _) => detail,
        (None, Some(code)) => format!("The command exited with code {code}"),
        (None, None) => "The command was stopped before it finished".to_string(),
    };

    tracing::warn!("hook '{hook}' failed: {message}");
    Some(message)
}

fn stderr_detail(bytes: &[u8]) -> Option<String> {
    let text = String::from_utf8_lossy(bytes);
    let line = text
        .lines()
        .rev()
        .map(str::trim)
        .find(|line| !line.is_empty())?;

    const MAX: usize = 300;
    if line.chars().count() <= MAX {
        return Some(line.to_string());
    }

    let mut clipped: String = line.chars().take(MAX).collect();
    clipped.push('…');
    Some(clipped)
}
