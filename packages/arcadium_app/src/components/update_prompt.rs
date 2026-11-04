use crate::theme::accent::AccentBorderExt;
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
                    .child(update_card(&state))
                    .child(UpdateDragRegion {
                        header: false,
                        children: Vec::new(),
                    }),
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
        .background(crate::theme::accent::themed_fill(Color::from_rgb(
            24, 29, 39,
        )))
        .themed_border(border_all_color(1., colors::component_border()))
        .child(UpdateDragRegion {
            header: true,
            children: vec![
                Icon::new(IconType::DownloadCloud02)
                    .size(24.)
                    .color(colors::brand())
                    .into_element(),
                label()
                    .text(if confirmation {
                        "Update available"
                    } else {
                        "Updating Arcadium Client"
                    })
                    .font_size(17.)
                    .font_weight(FontWeight::SEMI_BOLD)
                    .color(colors::fg_primary())
                    .into_element(),
            ],
        })
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

#[derive(Clone, Copy, PartialEq)]
struct UpdateWindowDrag;

#[derive(Clone, Copy, PartialEq)]
struct UpdateDragStart {
    window: (i32, i32),
    cursor: (f64, f64),
}

#[derive(PartialEq)]
struct UpdateDragSession {
    press: (f64, f64),
    anchor: Option<UpdateDragStart>,
    native: bool,
}

#[derive(PartialEq)]
struct UpdateDragRegion {
    header: bool,
    children: Vec<Element>,
}

impl Component for UpdateDragRegion {
    fn render(&self) -> impl IntoElement {
        let mut drag = use_state(|| None::<std::rc::Rc<std::cell::RefCell<UpdateDragSession>>>);
        let area = if self.header {
            rect()
                .horizontal()
                .cross_align(Alignment::Center)
                .spacing(12.)
                .a11y_role(AccessibilityRole::TitleBar)
        } else {
            rect()
                .position(Position::new_global().top(0.).left(0.))
                .width(Size::window_percent(100.))
                .height(Size::px(crate::theme::NAVBAR_HEIGHT_PX))
        };
        area.children(self.children.clone())
            .on_all_press(|event: Event<PressEventData>| event.stop_propagation())
            .on_pointer_down(move |event: Event<PointerEventData>| {
                if event.button() != Some(MouseButton::Left) {
                    return;
                }
                let point = event.global_location();
                let maximize = EventsCombos::<UpdateWindowDrag>::pressed(point).is_double();
                if maximize {
                    drag.set(None);
                    Platform::get().with_window(Platform::window_id(), move |window| {
                        window.set_maximized(!window.is_maximized());
                    });
                } else {
                    drag.set(Some(std::rc::Rc::new(std::cell::RefCell::new(
                        UpdateDragSession {
                            press: (point.x, point.y),
                            anchor: None,
                            native: false,
                        },
                    ))));
                }
            })
            .on_global_pointer_move(move |event: Event<PointerEventData>| {
                let session = drag.peek().clone();
                let Some(session) = session else {
                    return;
                };
                let point = event.global_location();
                EventsCombos::<UpdateWindowDrag>::moved(point);
                Platform::get().with_window(Platform::window_id(), move |window| {
                    let mut session = session.borrow_mut();
                    if session.native {
                        return;
                    }
                    if window.is_maximized() {
                        session.native = true;
                        let _ = window.drag_window();
                        return;
                    }
                    if let (Ok(inner), Ok(outer)) =
                        (window.inner_position(), window.outer_position())
                    {
                        let scale = window.scale_factor();
                        let press = session.press;
                        let start = *session.anchor.get_or_insert(UpdateDragStart {
                            window: (outer.x, outer.y),
                            cursor: (
                                inner.x as f64 + press.0 * scale,
                                inner.y as f64 + press.1 * scale,
                            ),
                        });
                        let x = start.window.0
                            + (inner.x as f64 + point.x * scale - start.cursor.0).round() as i32;
                        let y = start.window.1
                            + (inner.y as f64 + point.y * scale - start.cursor.1).round() as i32;
                        if outer.x != x || outer.y != y {
                            window.set_outer_position(winit::dpi::PhysicalPosition::new(x, y));
                        }
                    }
                });
            })
            .on_global_pointer_up(move |_: Event<PointerEventData>| {
                drag.set(None);
                EventsCombos::<UpdateWindowDrag>::released();
            })
    }
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
                    .background(crate::theme::accent::themed_fill(Color::from_rgb(
                        12, 16, 23,
                    )))
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
