use freya::prelude::*;

use crate::components::IconType;
use crate::routes::Route;
use crate::theme::colors;
use crate::view::onboarding::{
    onboarding_illustration, onboarding_nav, onboarding_page, step_heading,
};

#[derive(PartialEq)]
pub struct OnboardingWelcome;

impl Component for OnboardingWelcome {
    fn render(&self) -> impl IntoElement {
        let next = Route::OnboardingLocation {};

        let content = rect()
            .vertical()
            .width(Size::fill())
            .spacing(16.)
            .child(step_heading("Arcadium", ""))
            .child(
                label()
                    .text("Choose a folder, sign in, and set up Minecraft 1.8.9 or 1.7.10.")
                    .font_size(15.)
                    .color(colors::fg_secondary()),
            )
            .into_element();

        onboarding_page(
            onboarding_illustration(IconType::OnboardingWelcome),
            content,
            onboarding_nav(None, next, true),
        )
    }
}
