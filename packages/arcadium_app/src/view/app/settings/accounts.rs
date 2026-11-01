use crate::theme::accent::AccentBorderExt;
use arcadium_auth::{AccountKind, MinecraftAccount};
use chrono::{DateTime, Utc};
use freya::animation::{AnimNum, Function, OnFinish, use_animation};
use freya::prelude::*;
use freya::query::{MutationCapability, MutationStateData, UseMutation};
use uuid::Uuid;

use super::{section_header, settings_page};
use crate::components::{
    Avatar, Button, Dropdown, Icon, IconType, OverlayPopup, PlayerModel, TextInput,
    use_microsoft_login,
};
use crate::hooks::{
    AddOfflineAccountKeys, AddSessionAccountKeys, RefreshAccountKeys, RemoveAccountKeys,
    SetDefaultAccountKeys, try_accounts, try_default_account, use_accounts,
    use_add_offline_account, use_add_session_account, use_current_account, use_refresh_account,
    use_remove_account, use_set_default_account,
};
use crate::theme::colors;
use crate::ui::border_all_color;

/// The shader scales the model off `res.y` alone so height makes the player bigger width only needs to clear the arms
const MODEL_WIDTH_PX: f32 = 160.;
const MODEL_HEIGHT_PX: f32 = 264.;

const AVATAR_SIZE_PX: f32 = 36.;

#[derive(PartialEq)]
pub struct SettingsAccounts;

impl Component for SettingsAccounts {
    fn render(&self) -> impl IntoElement {
        let accounts_query = use_accounts();
        let default_query = use_current_account();

        let msa = use_microsoft_login();
        let add_offline = use_add_offline_account();
        let set_default = use_set_default_account();
        let remove = use_remove_account();
        let refresh = use_refresh_account();

        let mut username = use_state(String::new);
        let mut show_offline = use_state(|| false);
        let mut show_session = use_state(|| false);
        let mut closing_offline = use_state(|| false);

        use_side_effect(move || {
            if !*closing_offline.read() {
                return;
            }
            match &*add_offline.read().state() {
                MutationStateData::Settled { res: Ok(_), .. } => {
                    closing_offline.set(false);
                    show_offline.set(false);
                    username.set(String::new());
                }
                MutationStateData::Settled { res: Err(_), .. } => {
                    closing_offline.set(false);
                }
                _ => {}
            }
        });

        let accounts = try_accounts(&accounts_query).unwrap_or_default();
        let default_account = try_default_account(&default_query);
        let default_id = default_account.as_ref().map(|a| a.id);

        let offline_name = username.read().trim().to_string();
        let offline_uuid = (!offline_name.is_empty())
            .then(|| arcadium_auth::offline_uuid(&offline_name).to_string());

        let offline_error = mutation_err_text(&add_offline);

        let on_confirm_offline = move |_| {
            let name = username.peek().trim().to_string();
            if name.is_empty() {
                return;
            }
            add_offline.mutate(AddOfflineAccountKeys { username: name });
            closing_offline.set(true);
        };

        let mut rows: Vec<Element> = accounts
            .iter()
            .map(|account| {
                AccountRow {
                    id: account.id,
                    username: account.username.clone(),
                    kind: account.kind,
                    expires: account.expires,
                    is_default: Some(account.id) == default_id,
                    set_default,
                    remove,
                    refresh,
                }
                .into_element()
            })
            .collect();
        if rows.is_empty() {
            rows.push(empty_state());
        }

        settings_page()
            .child(hero(
                default_account,
                msa.pending,
                msa.error.clone(),
                move |_| show_offline.set(true),
                move |_| show_session.set(true),
                {
                    let msa = msa.clone();
                    move |_| msa.start()
                },
            ))
            .child(section_header("YOUR ACCOUNTS"))
            .children(rows)
            .maybe_child(show_offline.read().then(|| {
                offline_dialog(
                    username,
                    offline_uuid,
                    offline_error,
                    on_confirm_offline,
                    show_offline,
                )
            }))
            .maybe_child(msa.popup())
            .maybe_child(show_session.read().then(|| SessionAccountDialog {
                visible: show_session,
            }))
            .into_element()
    }
}

fn hero(
    account: Option<MinecraftAccount>,
    microsoft_pending: bool,
    error: Option<String>,
    on_open_offline: impl FnMut(Event<PressEventData>) + 'static,
    on_open_session: impl FnMut(Event<PressEventData>) + 'static,
    on_add_microsoft: impl FnMut(Event<PressEventData>) + 'static,
) -> impl IntoElement {
    let (name, subtitle) = match &account {
        Some(account) => (account.username.clone(), kind_label(account.kind)),
        None => (
            "No active account".to_string(),
            "Add an account to start playing",
        ),
    };

    rect()
        .horizontal()
        .width(Size::fill())
        .content(Content::Flex)
        .cross_align(Alignment::Center)
        .spacing(16.)
        .padding(Gaps::new_all(16.))
        .corner_radius(CornerRadius::new_all(12.))
        .background(colors::page_elevated())
        .child(model_frame(account.as_ref().map(|account| account.id)))
        .child(
            rect()
                .vertical()
                .width(Size::flex(1.0))
                .spacing(16.)
                .child(
                    rect()
                        .vertical()
                        .width(Size::fill())
                        .spacing(2.)
                        .child(
                            label()
                                .text("ACTIVE ACCOUNT")
                                .font_size(11.)
                                .font_weight(FontWeight::MEDIUM)
                                .color(colors::fg_secondary()),
                        )
                        .child(
                            label()
                                .text(name)
                                .font_size(24.)
                                .font_weight(FontWeight::SEMI_BOLD)
                                .max_lines(1)
                                .color(colors::fg_primary()),
                        )
                        .child(
                            label()
                                .text(subtitle)
                                .font_size(12.)
                                .color(colors::fg_secondary()),
                        ),
                )
                .child(
                    rect()
                        .vertical()
                        .width(Size::fill())
                        .spacing(8.)
                        .child(
                            rect()
                                .horizontal()
                                .width(Size::fill())
                                .content(Content::wrap_spacing(8.))
                                .spacing(8.)
                                .child(
                                    Button::new()
                                        .primary()
                                        .enabled(!microsoft_pending)
                                        .on_press(on_add_microsoft)
                                        .child(Icon::new(IconType::Globe01).size(16.))
                                        .text(if microsoft_pending {
                                            "Signing in..."
                                        } else {
                                            "Add Microsoft"
                                        }),
                                )
                                .child(
                                    Button::new()
                                        .secondary()
                                        .enabled(!microsoft_pending)
                                        .on_press(on_open_offline)
                                        .child(Icon::new(IconType::Plus).size(16.))
                                        .text("Add Cracked"),
                                )
                                .child(
                                    Button::new()
                                        .secondary()
                                        .enabled(!microsoft_pending)
                                        .on_press(on_open_session)
                                        .text("Add Session"),
                                ),
                        )
                        .map(error, |el, msg| {
                            el.child(hint_line(IconType::AlertTriangle, msg, colors::danger()))
                        }),
                ),
        )
        .into_element()
}

fn model_frame(id: Option<Uuid>) -> impl IntoElement {
    rect()
        .vertical()
        .cross_align(Alignment::Center)
        .spacing(6.)
        .child(
            rect()
                .width(Size::px(MODEL_WIDTH_PX))
                .height(Size::px(MODEL_HEIGHT_PX))
                .center()
                .overflow(Overflow::Clip)
                .corner_radius(CornerRadius::new_all(12.))
                .background(colors::component_bg())
                .themed_border(border_all_color(1., colors::component_border()))
                .child(match id {
                    Some(id) => PlayerModel::new(id)
                        .yaw(-0.5)
                        .width(Size::fill())
                        .height(Size::fill())
                        .into_element(),
                    None => Icon::new(IconType::Users01)
                        .size(28.)
                        .color(colors::fg_secondary())
                        .into_element(),
                }),
        )
        .maybe_child(id.map(|_| {
            label()
                .text("Drag to rotate")
                .font_size(10.)
                .color(colors::fg_secondary())
                .into_element()
        }))
        .into_element()
}

fn mutation_err_text<M>(mutation: &UseMutation<M>) -> Option<String>
where
    M: MutationCapability,
    M::Err: std::fmt::Display,
{
    match &*mutation.read().state() {
        MutationStateData::Settled { res: Err(err), .. } => Some(err.to_string()),
        MutationStateData::Loading {
            res: Some(Err(err)),
        } => Some(err.to_string()),
        _ => None,
    }
}

fn offline_dialog(
    username: State<String>,
    uuid_preview: Option<String>,
    error: Option<String>,
    on_confirm: impl FnMut(Event<PressEventData>) + 'static,
    mut show_offline: State<bool>,
) -> impl IntoElement {
    OverlayPopup::new()
        .on_close(move |()| show_offline.set(false))
        .child(
            rect()
                .width(Size::window_percent(100.))
                .height(Size::window_percent(100.))
                .center()
                .child(
                    rect()
                        .vertical()
                        .width(Size::px(380.))
                        .max_width(Size::window_percent(90.))
                        .spacing(16.)
                        .padding(Gaps::new_all(20.))
                        .corner_radius(CornerRadius::new_all(16.))
                        .background(colors::page_elevated())
                        .themed_border(border_all_color(1., colors::component_border()))
                        .child(
                            label()
                                .text("Add Cracked account")
                                .font_size(18.)
                                .font_weight(FontWeight::SEMI_BOLD)
                                .color(colors::fg_primary()),
                        )
                        .child(
                            rect()
                                .vertical()
                                .width(Size::fill())
                                .spacing(6.)
                                .child(field_label("Username"))
                                .child(TextInput::new(username).placeholder("Offline username")),
                        )
                        .child(
                            label()
                                .text("For singleplayer and offline-mode servers.")
                                .font_size(12.)
                                .color(colors::fg_secondary()),
                        )
                        .child(
                            rect()
                                .vertical()
                                .width(Size::fill())
                                .spacing(6.)
                                .child(field_label("UUID"))
                                .child(
                                    label()
                                        .text(uuid_preview.unwrap_or_else(|| "-".to_string()))
                                        .font_size(12.)
                                        .color(colors::fg_secondary()),
                                ),
                        )
                        .map(error, |el, msg| {
                            el.child(hint_line(IconType::AlertTriangle, msg, colors::danger()))
                        })
                        .child(
                            rect()
                                .horizontal()
                                .width(Size::fill())
                                .main_align(Alignment::End)
                                .spacing(8.)
                                .child(
                                    Button::new()
                                        .ghost()
                                        .on_press(move |_| show_offline.set(false))
                                        .text("Cancel"),
                                )
                                .child(
                                    Button::new()
                                        .primary()
                                        .on_press(on_confirm)
                                        .child(Icon::new(IconType::Plus).size(16.))
                                        .text("Add account"),
                                ),
                        ),
                ),
        )
        .into_element()
}

fn field_label(text: &str) -> impl IntoElement {
    label()
        .text(text.to_string())
        .font_size(11.)
        .font_weight(FontWeight::MEDIUM)
        .color(colors::fg_secondary())
        .into_element()
}

#[derive(PartialEq)]
struct SessionAccountDialog {
    visible: State<bool>,
}

impl Component for SessionAccountDialog {
    fn render(&self) -> impl IntoElement {
        let mut token = use_state(String::new);
        let refresh = use_state(|| false);
        let mut client_id = use_state(String::new);
        let mut error = use_state(|| None::<String>);
        let mut request = use_state(|| None::<tokio_util::sync::CancellationToken>);
        let add_session = use_add_session_account();
        let mut visible = self.visible;

        use_drop(move || {
            if let Some(cancel) = request.peek().as_ref() {
                cancel.cancel();
            }
        });

        let on_close = EventHandler::new(move |()| {
            if let Some(cancel) = request.peek().as_ref() {
                cancel.cancel();
            }
            token.set(String::new());
            client_id.set(String::new());
            visible.set(false);
        });
        let on_confirm = EventHandler::new(move |()| {
            let value = token.peek().trim().to_string();
            if value.is_empty() || request.peek().is_some() {
                return;
            }
            let keys = AddSessionAccountKeys::new(
                value,
                *refresh.peek(),
                client_id.peek().trim().to_string(),
            );
            let cancel = keys.cancellation_token();
            request.set(Some(cancel.clone()));
            token.set(String::new());
            error.set(None);
            spawn(async move {
                let result = add_session.mutate_async(keys).await;
                if cancel.is_cancelled() {
                    return;
                }
                request.set(None);
                match &*result.state() {
                    MutationStateData::Settled { res: Ok(_), .. } => visible.set(false),
                    MutationStateData::Settled { res: Err(err), .. } => {
                        if !err.is_login_cancelled() {
                            error.set(Some(err.to_string()));
                        }
                    }
                    _ => {}
                }
            });
        });

        session_dialog(
            token,
            refresh,
            client_id,
            request.read().is_some(),
            error.read().clone(),
            on_confirm,
            on_close,
        )
    }
}

#[expect(clippy::too_many_arguments)]
fn session_dialog(
    token: State<String>,
    mut refresh: State<bool>,
    client_id: State<String>,
    busy: bool,
    error: Option<String>,
    on_confirm: EventHandler<()>,
    on_close: EventHandler<()>,
) -> impl IntoElement {
    let close_scrim = on_close.clone();
    let submit = on_confirm.clone();
    let can_submit = !busy && !token.read().trim().is_empty();
    let is_refresh = *refresh.read();
    OverlayPopup::new()
        .on_close(move |()| close_scrim.call(()))
        .child(
            rect()
                .width(Size::window_percent(100.))
                .height(Size::window_percent(100.))
                .center()
                .child(
                    rect()
                        .vertical()
                        .width(Size::px(420.))
                        .max_width(Size::window_percent(90.))
                        .max_height(Size::window_percent(90.))
                        .spacing(16.)
                        .padding(Gaps::new_all(20.))
                        .corner_radius(CornerRadius::new_all(16.))
                        .background(colors::page_elevated())
                        .themed_border(border_all_color(1., colors::component_border()))
                        .child(
                            label()
                                .text("Add Session account")
                                .font_size(18.)
                                .font_weight(FontWeight::SEMI_BOLD)
                                .color(colors::fg_primary()),
                        )
                        .child(
                            ScrollView::new()
                                .width(Size::fill())
                                .height(Size::auto())
                                .max_height(Size::window_percent(60.))
                                .child(
                                    rect()
                                        .vertical()
                                        .width(Size::fill())
                                        .spacing(12.)
                                        .child(
                                            Dropdown::new(
                                                if is_refresh { "Microsoft refresh token" } else { "Auto-detect token type" },
                                                vec!["Auto-detect token type".into(), "Microsoft refresh token".into()],
                                            )
                                            .width(Size::fill())
                                            .height(Size::px(34.))
                                            .on_select(move |index: usize| {
                                                if !busy { refresh.set(index == 1); }
                                            }),
                                        )
                                        .child(
                                            rect()
                                                .vertical()
                                                .width(Size::fill())
                                                .spacing(6.)
                                                .child(field_label(if is_refresh { "Microsoft refresh token" } else { "Session token" }))
                                                .child(
                                                    TextInput::new(token)
                                                        .placeholder("Paste a token")
                                                        .mode(InputMode::new_password())
                                                        .enabled(!busy)
                                                        .auto_focus(true)
                                                        .on_submit(move |_| submit.call(())),
                                                ),
                                        )
                                        .maybe_child(is_refresh.then(|| {
                                            rect()
                                                .vertical()
                                                .width(Size::fill())
                                                .spacing(6.)
                                                .child(field_label("OAuth client ID (optional)"))
                                                .child(TextInput::new(client_id).enabled(!busy))
                                                .child(
                                                    label()
                                                        .text("Leave empty for automatic client selection.")
                                                        .font_size(12.)
                                                        .color(colors::fg_secondary()),
                                                )
                                        }))
                                        .map(error, |el, msg| el.child(hint_line(IconType::AlertTriangle, msg, colors::danger()))),
                                ),
                        )
                        .child(
                            rect()
                                .horizontal()
                                .width(Size::fill())
                                .main_align(Alignment::End)
                                .spacing(8.)
                                .child(Button::new().ghost().on_press(move |_| on_close.call(())).text("Cancel"))
                                .child(
                                    Button::new()
                                        .primary()
                                        .enabled(can_submit)
                                        .on_press(move |_| on_confirm.call(()))
                                        .text(if busy { "Verifying..." } else { "Add account" }),
                                ),
                        ),
                ),
        )
        .into_element()
}

/// Sits in whatever is left of the hero beside the model so the text must wrap
fn hint_line(icon: IconType, text: String, color: Color) -> impl IntoElement {
    rect()
        .horizontal()
        .width(Size::fill())
        .content(Content::Flex)
        .cross_align(Alignment::Center)
        .spacing(6.)
        .child(Icon::new(icon).size(13.).color(color))
        .child(
            label()
                .text(text)
                .font_size(12.)
                .width(Size::flex(1.0))
                .color(color),
        )
        .into_element()
}

const REFRESH_SPIN_TIME: u64 = 800;

const REFRESHING_OPACITY: f32 = 0.7;

struct AccountRow {
    id: Uuid,
    username: String,
    kind: AccountKind,
    expires: DateTime<Utc>,
    is_default: bool,
    set_default: crate::hooks::UseSetDefaultAccount,
    remove: crate::hooks::UseRemoveAccount,
    refresh: crate::hooks::UseRefreshAccount,
}

impl PartialEq for AccountRow {
    fn eq(&self, other: &Self) -> bool {
        self.id == other.id
            && self.username == other.username
            && self.kind == other.kind
            && self.expires == other.expires
            && self.is_default == other.is_default
    }
}

impl Component for AccountRow {
    fn render_key(&self) -> DiffKey {
        DiffKey::from(&self.id)
    }

    fn render(&self) -> impl IntoElement {
        let id = self.id;
        let is_default = self.is_default;
        let set_default = self.set_default;
        let remove = self.remove;
        let refresh = self.refresh;

        let is_microsoft = self.kind == AccountKind::Microsoft;
        let expired = self.kind != AccountKind::Offline && self.expires <= Utc::now();

        let mut refreshing = use_state(|| false);
        let is_refreshing = *refreshing.read();

        let spin = use_animation(|conf| {
            conf.on_finish(OnFinish::restart());
            AnimNum::new(0., 360.)
                .time(REFRESH_SPIN_TIME)
                .function(Function::Linear)
        });

        use_side_effect_with_deps(&is_refreshing, move |&is_refreshing| {
            let mut spin = spin;
            if is_refreshing {
                spin.start();
            } else {
                spin.reset();
            }
        });

        let rotation = if is_refreshing {
            spin.get().value()
        } else {
            0.
        };

        let border_color = if expired {
            colors::danger()
        } else if is_default {
            colors::brand()
        } else {
            colors::component_border()
        };

        rect()
            .horizontal()
            .width(Size::fill())
            .cross_align(Alignment::Center)
            .content(Content::Flex)
            .spacing(12.)
            .padding(Gaps::new_all(12.))
            .corner_radius(CornerRadius::new_all(12.))
            .background(colors::page_elevated())
            .themed_border(border_all_color(1., border_color))
            .opacity(if is_refreshing {
                REFRESHING_OPACITY
            } else {
                1.
            })
            .a11y_role(AccessibilityRole::Button)
            .maybe(!is_default, |el| {
                el.on_press(move |_| set_default.mutate(SetDefaultAccountKeys { id: Some(id) }))
            })
            .child(
                Avatar::new(id.to_string())
                    .width(Size::px(AVATAR_SIZE_PX))
                    .height(Size::px(AVATAR_SIZE_PX)),
            )
            .child(
                rect()
                    .vertical()
                    .width(Size::flex(1.0))
                    .spacing(4.)
                    .child(
                        rect()
                            .horizontal()
                            .cross_align(Alignment::Center)
                            .spacing(6.)
                            .child(
                                label()
                                    .text(self.username.clone())
                                    .font_size(15.)
                                    .font_weight(FontWeight::MEDIUM)
                                    .max_lines(1)
                                    .color(colors::fg_primary()),
                            )
                            .maybe_child(is_default.then(default_badge))
                            .maybe_child(expired.then(expired_badge)),
                    )
                    // The kind rides with the id a third badge and a full uuid do not both fit
                    .child(
                        label()
                            .text(format!("{} · {id}", kind_label(self.kind)))
                            .font_size(11.)
                            .max_lines(1)
                            .color(colors::fg_secondary()),
                    ),
            )
            .maybe_child(is_microsoft.then(|| {
                Button::new()
                    .ghost()
                    .icon()
                    .tooltip("Refresh this account")
                    .on_press(move |e: Event<PressEventData>| {
                        e.stop_propagation();
                        if *refreshing.peek() {
                            return;
                        }
                        refreshing.set(true);
                        spawn(async move {
                            refresh.mutate_async(RefreshAccountKeys { id }).await;
                            refreshing.set(false);
                        });
                    })
                    .child(
                        rect().rotate(rotation).child(
                            Icon::new(IconType::RefreshCw01)
                                .size(18.)
                                .color(if expired {
                                    colors::danger()
                                } else {
                                    colors::fg_secondary()
                                }),
                        ),
                    )
                    .into_element()
            }))
            .child(
                Button::new()
                    .ghost()
                    .icon()
                    .enabled(false)
                    .tooltip("Edit")
                    .child(
                        Icon::new(IconType::Pencil01)
                            .size(18.)
                            .color(colors::fg_secondary()),
                    ),
            )
            .child(
                Button::new()
                    .ghost()
                    .icon()
                    .tooltip("Remove account")
                    .on_press(move |e: Event<PressEventData>| {
                        e.stop_propagation();
                        remove.mutate(RemoveAccountKeys { id });
                    })
                    .child(
                        Icon::new(IconType::Trash01)
                            .size(18.)
                            .color(colors::fg_secondary()),
                    ),
            )
            .into_element()
    }
}

fn kind_label(kind: AccountKind) -> &'static str {
    match kind {
        AccountKind::Microsoft => "Microsoft",
        AccountKind::Offline => "Cracked",
        AccountKind::Session => "Session",
    }
}

fn default_badge() -> impl IntoElement {
    badge(
        Icon::new(IconType::CheckCircle)
            .size(12.)
            .color(colors::brand())
            .into_element(),
        "Default".to_string(),
        colors::brand(),
        colors::brand(),
    )
}

fn expired_badge() -> impl IntoElement {
    badge(
        Icon::new(IconType::AlertTriangle)
            .size(12.)
            .color(colors::danger())
            .into_element(),
        "Expired".to_string(),
        colors::danger(),
        colors::danger(),
    )
}

fn badge(icon: impl IntoElement, text: String, border: Color, fg: Color) -> impl IntoElement {
    rect()
        .horizontal()
        .cross_align(Alignment::Center)
        .spacing(4.)
        .padding(Gaps::new_symmetric(2., 8.))
        .corner_radius(CornerRadius::new_all(999.))
        .themed_border(border_all_color(1., border))
        .background(colors::component_bg())
        .child(icon)
        .child(
            label()
                .text(text)
                .font_size(10.)
                .font_weight(FontWeight::MEDIUM)
                .color(crate::theme::accent::themed_fill(fg)),
        )
        .into_element()
}

fn empty_state() -> Element {
    rect()
        .vertical()
        .width(Size::fill())
        .center()
        .padding(Gaps::new_all(32.))
        .spacing(8.)
        .corner_radius(CornerRadius::new_all(12.))
        .background(colors::page_elevated())
        .child(
            Icon::new(IconType::Users01)
                .size(32.)
                .color(colors::fg_secondary()),
        )
        .child(
            label()
                .text("No accounts yet. Add one above.")
                .font_size(14.)
                .color(colors::fg_secondary()),
        )
        .into_element()
}

#[cfg(test)]
mod session_ui_tests {
    use super::*;
    use freya_testing::TestingRunner;
    use std::collections::HashMap;

    fn click_label(test: &mut TestingRunner, text: &str) {
        let position = test
            .find(|node, element| {
                Label::try_downcast(element)
                    .filter(|label| label.text == text)
                    .map(|_| {
                        let area = node.layout().area;
                        (
                            area.min_x() as f64 + area.size.width as f64 / 2.,
                            area.min_y() as f64 + area.size.height as f64 / 2.,
                        )
                    })
            })
            .unwrap_or_else(|| panic!("Missing account action: {text}"));
        test.click_cursor(position);
        test.sync_and_update();
    }

    fn paragraphs(test: &TestingRunner) -> Vec<String> {
        test.find_many(|_, element| {
            Paragraph::try_downcast(element).map(|paragraph| {
                paragraph
                    .spans
                    .iter()
                    .map(|span| span.text.to_string())
                    .collect()
            })
        })
    }

    fn set_fonts(test: &mut TestingRunner) {
        test.set_fonts(HashMap::from([(
            "Inter",
            include_bytes!("../../../../assets/fonts/Inter-Regular.ttf").as_slice(),
        )]));
        test.sync_and_update();
    }

    #[test]
    fn session_entry_from_account_actions_masks_and_clears_the_token_on_cancel() {
        let app = || {
            let mut visible = use_state(|| false);
            rect()
                .width(Size::fill())
                .height(Size::fill())
                .background(colors::page())
                .child(hero(
                    None,
                    false,
                    None,
                    |_| {},
                    move |_| visible.set(true),
                    |_| {},
                ))
                .maybe_child(visible.read().then(|| SessionAccountDialog { visible }))
        };
        let (mut test, _) = TestingRunner::new(app, Size2D::new(800., 600.), |_| (), 1.);
        set_fonts(&mut test);
        let directory = std::path::Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("../../build/accounts-session-ui");
        std::fs::create_dir_all(&directory).unwrap();
        test.render_to_file(directory.join("session-accounts-actions.png"));
        click_label(&mut test, "Add Session");
        assert!(
            test.find(|_, element| Label::try_downcast(element)
                .filter(|label| label.text == "Auto-detect token type")
                .map(|_| ()))
                .is_some()
        );
        let position = test
            .find(|node, element| {
                Paragraph::try_downcast(element)
                    .filter(|paragraph| {
                        paragraph
                            .spans
                            .iter()
                            .any(|span| span.text == "Paste a token")
                    })
                    .map(|_| {
                        let area = node.layout().area;
                        (
                            area.min_x() as f64 + 8.,
                            area.min_y() as f64 + area.size.height as f64 / 2.,
                        )
                    })
            })
            .expect("Session token input");
        test.click_cursor(position);
        test.write_text("fixture-session-token");
        test.sync_and_update();
        let rendered = paragraphs(&test);
        assert!(
            rendered
                .iter()
                .any(|value| value == "*********************"),
            "{rendered:?}"
        );
        assert!(
            rendered
                .iter()
                .all(|value| !value.contains("fixture-session-token"))
        );
        test.render_to_file(directory.join("session-masked-form.png"));
        click_label(&mut test, "Cancel");
        click_label(&mut test, "Add Session");
        assert!(
            paragraphs(&test)
                .iter()
                .any(|value| value == "Paste a token")
        );
        assert!(paragraphs(&test).iter().all(|value| !value.contains('*')));
        click_label(&mut test, "Auto-detect token type");
        click_label(&mut test, "Microsoft refresh token");
        assert!(
            test.find(|_, element| Label::try_downcast(element)
                .filter(|label| label.text == "OAuth client ID (optional)")
                .map(|_| ()))
                .is_some()
        );
        test.render_to_file(directory.join("session-refresh-form.png"));
    }

    #[test]
    fn pending_session_import_keeps_cancel_available_in_a_small_window() {
        let app = || {
            let mut cancelled = use_consume::<State<bool>>();
            let token = use_state(String::new);
            let refresh = use_state(|| true);
            let client_id = use_state(String::new);
            rect()
                .width(Size::fill())
                .height(Size::fill())
                .background(colors::page())
                .child(session_dialog(
                    token,
                    refresh,
                    client_id,
                    true,
                    None,
                    EventHandler::new(|()| panic!("A pending import must not submit twice")),
                    EventHandler::new(move |()| cancelled.set(true)),
                ))
        };
        let (mut test, cancelled) = TestingRunner::new(
            app,
            Size2D::new(600., 430.),
            |runner| runner.provide_root_context(|| State::create(false)),
            1.,
        );
        set_fonts(&mut test);
        click_label(&mut test, "Verifying...");
        let directory = std::path::Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("../../build/accounts-session-ui");
        std::fs::create_dir_all(&directory).unwrap();
        test.render_to_file(directory.join("session-pending-small.png"));
        click_label(&mut test, "Cancel");
        assert!(*cancelled.peek());
    }
}
