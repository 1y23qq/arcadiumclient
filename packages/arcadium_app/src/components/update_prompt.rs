use freya::prelude::*;

use crate::components::{Button, Icon, IconType, OverlayPopup, Spinner, progress_track};
use crate::theme::colors;
use crate::ui::border_all_color;
use crate::updater::{self, UpdatePhase, UpdateStatus};

#[derive(PartialEq)]
pub struct UpdatePromptOverlay;

impl Component for UpdatePromptOverlay {
    fn render(&self) -> impl IntoElement {
        let mut status = use_state(UpdateStatus::default);
        use_hook(move || {
            let mut receiver = updater::subscribe();
            status.set(receiver.borrow().clone());
            spawn_forever(async move {
                while receiver.changed().await.is_ok() {
                    status.set(receiver.borrow_and_update().clone());
                }
            });
        });
        let state = status.read().clone();
        if state.phase == UpdatePhase::Idle {
            return rect().into_element();
        }
        let popup = OverlayPopup::new();
        let popup = if matches!(
            state.phase,
            UpdatePhase::AwaitingConfirmation | UpdatePhase::Failed
        ) {
            let version = state.version.clone();
            popup.on_close(move |_| updater::cancel_update(&version))
        } else {
            popup
        };
        popup
            .child(
                rect()
                    .width(Size::window_percent(100.))
                    .height(Size::window_percent(100.))
                    .center()
                    .child(update_card(&state)),
            )
            .into_element()
    }
}

fn update_card(state: &UpdateStatus) -> Element {
    let failed = state.phase == UpdatePhase::Failed;
    let confirmation = state.phase == UpdatePhase::AwaitingConfirmation;
    let label_text = match state.phase {
        UpdatePhase::Checking => "Checking for updates",
        UpdatePhase::AwaitingConfirmation => "Would you like to update now?",
        UpdatePhase::Approved => "Preparing update",
        UpdatePhase::Downloading => "Downloading update",
        UpdatePhase::Verifying => "Verifying download",
        UpdatePhase::Installing => "Installing update",
        UpdatePhase::Failed => "Update could not be installed",
        UpdatePhase::Idle => "",
    };
    let percent = if state.total == 0 {
        0.
    } else {
        100. * state.downloaded as f32 / state.total as f32
    };
    let mut card = rect()
        .vertical()
        .width(Size::px(430.))
        .max_width(Size::window_percent(90.))
        .font_family("Inter")
        .padding(Gaps::new_all(28.))
        .spacing(17.)
        .corner_radius(CornerRadius::new_all(14.))
        .background(Color::from_rgb(24, 29, 39))
        .border(border_all_color(1., colors::component_border()))
        .child(
            rect()
                .horizontal()
                .cross_align(Alignment::Center)
                .spacing(12.)
                .child(
                    Icon::new(IconType::DownloadCloud02)
                        .size(24.)
                        .color(colors::brand()),
                )
                .child(
                    label()
                        .text(if confirmation {
                            "Update available"
                        } else {
                            "Updating Arcadium Client"
                        })
                        .font_size(17.)
                        .font_weight(FontWeight::SEMI_BOLD)
                        .color(colors::fg_primary()),
                ),
        )
        .child(
            label()
                .text(label_text)
                .font_size(13.)
                .color(colors::fg_secondary()),
        );
    if confirmation {
        let update_version = state.version.clone();
        let cancel_version = state.version.clone();
        card = card
            .child(
                label()
                    .text(format!(
                        "Current version: {}\nNew version: {}",
                        env!("CARGO_PKG_VERSION"),
                        state.version
                    ))
                    .font_size(12.)
                    .color(colors::fg_secondary()),
            )
            .child(
                rect()
                    .horizontal()
                    .width(Size::fill())
                    .main_align(Alignment::End)
                    .spacing(9.)
                    .child(
                        Button::new()
                            .secondary()
                            .text("Cancel")
                            .on_press(move |_| updater::cancel_update(&cancel_version)),
                    )
                    .child(
                        Button::new()
                            .primary()
                            .text("Update now")
                            .on_press(move |_| updater::install_update(&update_version)),
                    ),
            );
    } else if failed {
        let cancel_version = state.version.clone();
        card = card
            .child(
                label()
                    .text("Try again or continue with the current version.")
                    .font_size(12.)
                    .color(colors::fg_secondary()),
            )
            .child(
                rect()
                    .horizontal()
                    .spacing(9.)
                    .child(
                        Button::new()
                            .secondary()
                            .text("Continue")
                            .on_press(move |_| updater::cancel_update(&cancel_version)),
                    )
                    .child(
                        Button::new()
                            .primary()
                            .text("Retry")
                            .on_press(|_| updater::retry()),
                    ),
            );
    } else {
        card = card.child(Spinner::new(20.));
        if state.total > 0 && state.phase != UpdatePhase::Installing {
            card = card
                .child(progress_track(
                    percent,
                    6.,
                    colors::brand(),
                    Color::from_rgb(46, 52, 65),
                ))
                .child(
                    label()
                        .text(format!(
                            "{percent:.0}%   {:.1} / {:.1} MB",
                            state.downloaded as f64 / 1_000_000.,
                            state.total as f64 / 1_000_000.
                        ))
                        .font_size(11.)
                        .color(colors::fg_secondary()),
                );
        }
        if !state.version.is_empty() {
            card = card.child(
                label()
                    .text(format!("Version {}", state.version))
                    .font_size(11.)
                    .color(colors::fg_secondary()),
            );
        }
        if state.phase != UpdatePhase::Checking {
            card = card.child(
                label()
                    .text("Arcadium will restart automatically.")
                    .font_size(12.)
                    .color(colors::fg_secondary()),
            );
        }
    }
    card.into_element()
}

#[cfg(test)]
mod tests {
    use super::*;
    use freya_testing::TestingRunner;
    use std::collections::HashMap;

    #[test]
    fn update_progress_and_recovery_actions_render_in_a_small_window() {
        let states = [
            (
                UpdatePhase::AwaitingConfirmation,
                "Would you like to update now?",
                "confirmation",
            ),
            (
                UpdatePhase::Downloading,
                "Downloading update",
                "downloading",
            ),
            (UpdatePhase::Installing, "Installing update", "installing"),
            (
                UpdatePhase::Failed,
                "Update could not be installed",
                "failed",
            ),
        ];
        for (phase, expected, name) in states {
            let status = UpdateStatus {
                phase,
                version: "0.4.0".into(),
                downloaded: 72_000_000,
                total: 147_000_000,
            };
            let app = move || {
                rect()
                    .width(Size::fill())
                    .height(Size::fill())
                    .center()
                    .background(Color::from_rgb(12, 16, 23))
                    .child(update_card(&status))
            };
            let (mut test, _) = TestingRunner::new(app, Size2D::new(600., 430.), |_| (), 1.);
            test.set_fonts(HashMap::from([(
                "Inter",
                include_bytes!("../../assets/fonts/Inter-Regular.ttf").as_slice(),
            )]));
            test.sync_and_update();
            let labels = test.find_many(|_, element| {
                Label::try_downcast(element).map(|label| label.text.to_string())
            });
            assert!(labels.iter().any(|label| label == expected), "{labels:?}");
            if name == "confirmation" {
                assert!(labels.iter().any(|label| label == "Cancel"));
                assert!(labels.iter().any(|label| label == "Update now"));
                assert!(
                    !labels
                        .iter()
                        .any(|label| label == "Arcadium will restart automatically.")
                );
            }
            let directory = std::path::Path::new(env!("CARGO_MANIFEST_DIR"))
                .join("../../build/update-ui-evidence");
            std::fs::create_dir_all(&directory).unwrap();
            test.render_to_file(directory.join(format!("{name}.png")));
        }
    }
}
