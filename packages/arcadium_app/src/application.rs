use crate::state::{AppChannel, AppState, LauncherInit};
use crate::{
    Actions, ConfirmLinkOverlay, EssentialConfirmOverlay, EssentialGuardState, EventPump,
    LinkConfirmState, StartMaximizedState, events, microsoft_java, router, use_provide_actions,
    use_provide_essential_guard, use_provide_link_confirm, use_provide_start_maximized,
};
use freya::prelude::*;
use freya::radio::use_init_radio_station;
use std::cell::Cell;

pub struct ArcadiumApp {
    pub needs_location: bool,
    pub start_maximized: bool,
    pub boot_launch: Cell<Option<String>>,
}

impl App for ArcadiumApp {
    fn render(&self) -> impl IntoElement {
        crate::updater::acknowledge_restart();
        let needs_location = self.needs_location;

        // Radio state is `!Send` so every writer runs on the UI thread `spawn_forever`
        let station = use_init_radio_station::<AppState, AppChannel>(move || AppState {
            launcher: LauncherInit {
                needs_location,
                ..LauncherInit::default()
            },
            ..AppState::default()
        });

        let boot_launch = self.boot_launch.take();

        let actions = use_hook(move || {
            let (signals_tx, signals_rx) = tokio::sync::mpsc::unbounded_channel();
            let (events_bus, events_rx) = arcadium_events::EventBus::channel();
            let actions = Actions::new(station, signals_tx, events_bus.clone());

            spawn_forever(
                EventPump {
                    events: events_rx,
                    signals: signals_rx,
                    station,
                }
                .run(),
            );

            if !needs_location {
                let startup = actions.clone();
                let rescue_bus = events_bus.clone();
                spawn_forever(async move {
                    crate::updater::check_at_startup().await;
                    match events::start_launcher(station, events_bus).await {
                        // Must follow startup `sync_bundles` needs the launcher handle and firing
                        // it early leaves `syncing_bundles` stuck disabling every launch button
                        Ok(()) => {
                            startup.sync_bundles();
                            microsoft_java::spawn_auto_install();
                        }
                        Err(err) => {
                            events::report_startup_failure(&station, &err);
                            crate::updater::spawn_update_check(false, rescue_bus);
                        }
                    }
                });
            }

            if needs_location {
                spawn_forever(crate::updater::check_at_startup());
            }

            if let Some(folder) = boot_launch {
                actions.request_launch_by_folder(folder);
            }

            actions
        });

        use_provide_actions(&actions);
        crate::theme::accent::use_shared_accent();

        let link_confirm = use_state(|| None::<String>);
        use_provide_link_confirm(LinkConfirmState(link_confirm));

        let essential_guard = use_state(|| None);
        use_provide_essential_guard(EssentialGuardState(essential_guard));

        use_provide_start_maximized(StartMaximizedState(self.start_maximized));

        rect()
            .width(Size::fill())
            .height(Size::fill())
            .child(ConfirmLinkOverlay)
            .child(EssentialConfirmOverlay)
            .child(router())
            .child(crate::components::UpdatePromptOverlay)
    }
}
