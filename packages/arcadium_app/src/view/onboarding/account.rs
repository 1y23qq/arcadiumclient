use arcadium_auth::MinecraftAccount;
use freya::prelude::*;
use freya::query::MutationStateData;

use crate::components::{Avatar, Button, Icon, IconType, TextInput, use_microsoft_login};
use crate::hooks::{
    AddOfflineAccountKeys, mutation_is_running, try_default_account, use_add_offline_account,
    use_current_account,
};
use crate::routes::Route;
use crate::theme::colors;
use crate::view::onboarding::{
    onboarding_illustration, onboarding_nav, onboarding_page, step_heading,
};

#[derive(PartialEq)]
pub struct OnboardingAccount;

impl Component for OnboardingAccount {
    fn render(&self) -> impl IntoElement {
        let account_query = use_current_account();
        let msa = use_microsoft_login();
        let add_offline = use_add_offline_account();
        let username = use_state(String::new);
        let show_cracked = use_state(|| false);
        let offline_error = match &*add_offline.read().state() {
            MutationStateData::Settled {
                res: Err(error), ..
            }
            | MutationStateData::Loading {
                res: Some(Err(error)),
            } => Some(error.to_string()),
            _ => None,
        };

        let account = try_default_account(&account_query);
        let has_account = account.is_some();

        let content = rect()
            .vertical()
            .width(Size::fill())
            .spacing(24.)
            .child(step_heading(
                "Account",
                "Choose a Microsoft or Cracked account.",
            ))
            .child(match &account {
                Some(account) => account_preview(account).into_element(),
                None => {
                    let start = msa.clone();
                    sign_in_card(
                        msa.pending || mutation_is_running(&add_offline),
                        msa.error.clone().or(offline_error),
                        move |_| start.start(),
                        username,
                        show_cracked,
                        move |_| {
                            add_offline.mutate(AddOfflineAccountKeys {
                                username: username.peek().trim().to_string(),
                            });
                        },
                    )
                    .into_element()
                }
            })
            .into_element();

        let page = onboarding_page(
            onboarding_illustration(IconType::OnboardingAccount),
            content,
            onboarding_nav(
                Some(Route::OnboardingMigration {}),
                Route::OnboardingPreferences {},
                has_account,
            ),
        );

        rect()
            .width(Size::fill())
            .height(Size::fill())
            .child(page)
            .maybe_child(msa.popup())
    }
}

fn account_preview(account: &MinecraftAccount) -> impl IntoElement {
    rect()
        .horizontal()
        .width(Size::fill())
        .spacing(24.)
        .child(
            rect()
                .horizontal()
                .spacing(12.)
                .cross_align(Alignment::Center)
                .child(
                    Avatar::new(account.id.to_string())
                        .width(Size::px(48.))
                        .height(Size::px(48.)),
                )
                .child(
                    rect()
                        .vertical()
                        .spacing(4.)
                        .child(
                            label()
                                .text(account.username.clone())
                                .font_size(16.)
                                .font_weight(FontWeight::SEMI_BOLD)
                                .color(colors::fg_primary()),
                        )
                        .child(
                            label()
                                .text(account.id.to_string())
                                .font_size(12.)
                                .color(colors::fg_secondary()),
                        ),
                ),
        )
        .into_element()
}

fn sign_in_card(
    pending: bool,
    error: Option<String>,
    on_add: impl FnMut(Event<PressEventData>) + 'static,
    username: State<String>,
    mut show_cracked: State<bool>,
    on_add_cracked: impl FnMut(Event<PressEventData>) + 'static,
) -> impl IntoElement {
    rect()
        .vertical()
        .spacing(12.)
        .cross_align(Alignment::Start)
        .child(
            Button::new()
                .primary()
                .large()
                .enabled(!pending)
                .on_press(on_add)
                .child(Icon::new(IconType::Globe01).size(16.))
                .text(if pending {
                    "Signing in..."
                } else {
                    "Microsoft"
                }),
        )
        .child(
            Button::new()
                .secondary()
                .enabled(!pending)
                .on_press(move |_| {
                    let next = !*show_cracked.peek();
                    show_cracked.set(next);
                })
                .text("Cracked"),
        )
        .maybe(*show_cracked.read(), |element| {
            element.child(
                rect()
                    .vertical()
                    .width(Size::fill())
                    .spacing(10.)
                    .child(TextInput::new(username).placeholder("Username"))
                    .child(
                        label()
                            .text("For singleplayer and offline-mode servers.")
                            .font_size(12.)
                            .color(colors::fg_secondary()),
                    )
                    .child(
                        Button::new()
                            .primary()
                            .enabled(!pending && !username.read().trim().is_empty())
                            .on_press(on_add_cracked)
                            .text("Add account"),
                    ),
            )
        })
        .maybe_child(error.map(|message| {
            rect()
                .horizontal()
                .cross_align(Alignment::Center)
                .spacing(6.)
                .child(
                    Icon::new(IconType::AlertTriangle)
                        .size(13.)
                        .color(colors::danger()),
                )
                .child(label().text(message).font_size(12.).color(colors::danger()))
                .into_element()
        }))
        .into_element()
}

#[cfg(test)]
mod tests {
    use super::*;
    use freya_testing::TestingRunner;
    use std::collections::HashMap;

    #[test]
    fn cracked_onboarding_can_be_selected_without_microsoft() {
        let app = || {
            let (username, mut added) = use_consume::<(State<String>, State<usize>)>();
            let show = use_state(|| false);
            rect()
                .padding(20.)
                .width(Size::fill())
                .height(Size::fill())
                .child(sign_in_card(
                    false,
                    None,
                    |_| {},
                    username,
                    show,
                    move |_| {
                        assert_eq!(username.peek().as_str(), "OfflineUser");
                        let next = *added.peek() + 1;
                        added.set(next);
                    },
                ))
        };
        let (mut test, (mut username, added)) = TestingRunner::new(
            app,
            Size2D::new(520., 420.),
            |runner| {
                runner
                    .provide_root_context(|| (State::create(String::new()), State::create(0usize)))
            },
            1.,
        );
        test.set_fonts(HashMap::from([(
            "Inter",
            include_bytes!("../../../assets/fonts/Inter-Regular.ttf").as_slice(),
        )]));
        test.sync_and_update();
        let click_label = |test: &mut TestingRunner, text: &str| {
            let position = {
                let node = test
                    .find(|node, element| {
                        Label::try_downcast(element)
                            .filter(|label| label.text == text)
                            .map(|_| node)
                    })
                    .unwrap();
                let area = node.layout().area;
                (
                    area.min_x() as f64 + area.size.width as f64 / 2.,
                    area.min_y() as f64 + area.size.height as f64 / 2.,
                )
            };
            test.click_cursor(position);
            test.sync_and_update();
        };
        click_label(&mut test, "Cracked");
        assert!(
            test.find(|_, element| Label::try_downcast(element)
                .filter(|label| label.text == "For singleplayer and offline-mode servers.")
                .map(|_| ()))
                .is_some()
        );
        click_label(&mut test, "Add account");
        assert_eq!(*added.peek(), 0);
        test.run_in(|| username.set("OfflineUser".into()));
        test.sync_and_update();
        click_label(&mut test, "Add account");
        assert_eq!(*added.peek(), 1);
        let directory = std::path::Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("../../build/accounts-gradient-test");
        std::fs::create_dir_all(&directory).unwrap();
        test.render_to_file(directory.join("cracked-onboarding.png"));
    }
}
