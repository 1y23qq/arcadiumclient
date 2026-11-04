use crate::updater::{UpdatePhase, UpdateStatus};
use freya::prelude::*;

pub struct UpdateWindowPreview {
    pub phase: UpdatePhase,
}

impl App for UpdateWindowPreview {
    fn render(&self) -> impl IntoElement {
        let phase = self.phase.clone();
        use_hook(move || {
            crate::updater::preview_status(UpdateStatus {
                phase,
                version: "0.0.0-qa".into(),
                downloaded: 32_000_000,
                total: 100_000_000,
            });
        });
        rect()
            .width(Size::fill())
            .height(Size::fill())
            .background(Color::from_rgb(12, 16, 23))
            .child(crate::components::UpdatePromptOverlay)
    }
}
