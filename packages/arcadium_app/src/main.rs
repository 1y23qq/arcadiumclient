#![cfg_attr(
    all(not(debug_assertions), target_os = "windows"),
    windows_subsystem = "windows"
)]
#![recursion_limit = "256"]

use arcadium_app::application::ArcadiumApp;
use arcadium_app::ipc::{self, Claim};
use arcadium_app::{cli, constants, platform, theme};
use freya::prelude::*;
use std::cell::Cell;
use tokio::runtime::Builder;

fn main() {
    if arcadium_app::cookie_consent::requested() {
        if arcadium_app::cookie_consent::run().is_err() {
            std::process::exit(1);
        }
        return;
    }
    if arcadium_core::rpc_handoff::requested() {
        if arcadium_core::rpc_handoff::run().is_err() {
            std::process::exit(1);
        }
        return;
    }
    if arcadium_app::account_service::requested() {
        if arcadium_app::account_service::run().is_err() {
            std::process::exit(1);
        }
        return;
    }
    #[cfg(target_os = "linux")]
    unsafe {
        gtk_sys::gtk_disable_setlocale();
    }

    arcadium_app::updater::restore_startup_context();
    let cli = cli::parse();
    if let Some(directory) = std::env::var_os("ARCADIUM_DATA_DIR") {
        arcadium_common::paths::set_launcher_dir(std::path::PathBuf::from(directory));
    }

    let mut builder = Builder::new_multi_thread();
    builder.enable_all().max_blocking_threads(64);

    // Debug builds emit unoptimized async code that can overflow the default 2MB stack
    #[cfg(debug_assertions)]
    builder.thread_stack_size(3 * 1024 * 1024);

    let rt = builder.build().unwrap();
    let _tokio_guard = rt.enter();

    let adopted = rt.block_on(arcadium_core::relocate::adopt_legacy_dir());

    // no settings file is the sign of a fresh install, but not proof of one
    let never_set_up = arcadium_common::paths::settings_file()
        .map(|path| !path.exists())
        .unwrap_or(false);
    let has_database = arcadium_common::paths::database_file()
        .map(|path| path.exists())
        .unwrap_or(false);
    let was_damaged = arcadium_common::paths::damaged_settings_file()
        .map(|path| path.exists())
        .unwrap_or(false);
    let needs_location = never_set_up && !has_database && !was_damaged;

    let mut unprotected = None;
    match rt.block_on(ipc::claim(&cli)) {
        Claim::Forwarded => return,
        Claim::Primary(listener) => ipc::listen(listener),
        Claim::Solo(reason) => unprotected = Some(reason),
    }

    let settings = rt.block_on(arcadium_core::settings::store::load_settings(None));

    arcadium_common::consent::init(settings.declined_tos);

    let abandoned_move = settings
        .data_dir
        .clone()
        .filter(|dir| arcadium_core::relocate::incomplete(dir));

    if let Some(dir) = settings.data_dir.clone()
        && abandoned_move.is_none()
    {
        arcadium_common::paths::set_data_dir(dir);
    }

    if settings.log_debug {
        arcadium_core::logger::init_debug()
    } else {
        arcadium_core::logger::init()
    }
    .expect("Failed to initialize logger");

    match adopted {
        Ok(Some(from)) => {
            tracing::info!(from = %from.display(), "moved out of the old launcher folder")
        }
        Ok(None) => {}
        Err(err) => tracing::error!("{err}"),
    }

    if let Some(reason) = unprotected {
        tracing::warn!("no single-instance endpoint, a second launcher can start: {reason}");
    }

    match arcadium_app::shortcut::launcher_exe()
        .and_then(|exe| arcadium_app::protocol::register(&exe))
    {
        Ok(()) => {}
        Err(err) => tracing::warn!(
            "could not register the {}:// handler: {err:#}",
            arcadium_app::protocol::SCHEME
        ),
    }

    if let Some(dir) = abandoned_move {
        tracing::error!(
            path = %dir.display(),
            "a move of the game data folder never finished; staying on the old folder"
        );
    }

    let _sentry_guard = arcadium_core::reporting::init(settings.crash_reporting);

    #[cfg(target_os = "macos")]
    arcadium_app::platform::macos::loop_memory_collector();

    let start_maximized = settings.start_maximized;
    let show_tray_icon = settings.show_tray_icon;

    let window_config = WindowConfig::new_app(ArcadiumApp {
        needs_location,
        start_maximized,
        boot_launch: Cell::new(cli.launch),
    })
    .with_title(constants::WINDOW_TITLE)
    .with_app_id(constants::WINDOW_APP_ID)
    .with_icon(LaunchConfig::window_icon(include_bytes!(
        "../icons/128x128.png"
    )))
    .with_size(1200., 800.)
    .with_min_size(800., 600.)
    .with_transparency(false)
    .with_background(Color::TRANSPARENT)
    // A half-copied data folder is unrecoverable, so the window refuses to
    // close while one is being moved; the move screen says as much
    .with_on_close(|_, _| {
        if arcadium_core::relocate::in_progress() {
            tracing::warn!("close request ignored, the data folder is still being moved");
        } else {
            ipc::send(ipc::IpcCommand::Close);
        }
        CloseDecision::KeepOpen
    });

    #[cfg(target_os = "macos")]
    let window_config = window_config
        .with_decorations(true)
        .with_window_attributes(|attrs, _| platform::macos::window_attributes(attrs))
        .with_window_handle(move |window| {
            platform::macos::initialize_window(window, start_maximized);
        });

    #[cfg(not(target_os = "macos"))]
    let window_config = window_config
        .with_decorations(false)
        .with_window_attributes(move |attrs, _| attrs.with_maximized(start_maximized));

    let mut launch_config = LaunchConfig::new()
        .with_window(window_config)
        .with_gpu_resource_cache_limit(
            std::env::var("ARCADIUM_GPU_CACHE_BYTES")
                .ok()
                .and_then(|v| v.parse::<usize>().ok())
                .unwrap_or(96 * 1024 * 1024),
        )
        .with_plugin(freya::borderless::BorderlessPlugin::new())
        .with_plugin(freya::metrics::MetricsPlugin::default())
        .with_default_font(theme::DEFAULT_FONT);

    if show_tray_icon && platform::tray::available() {
        launch_config = launch_config.with_tray(platform::tray::build, platform::tray::handle);
    }

    for (font, bytes) in theme::load_fonts() {
        launch_config = launch_config.with_font(font, bytes);
    }

    launch(launch_config);
}
