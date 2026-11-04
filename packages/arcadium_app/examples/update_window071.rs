use arcadium_app::{release_qa::UpdateWindowPreview, updater::UpdatePhase};
use freya::prelude::*;

fn main() {
    let runtime = tokio::runtime::Runtime::new().unwrap();
    let _guard = runtime.enter();
    let phase = match std::env::args().nth(1).as_deref() {
        Some("confirmation") => UpdatePhase::AwaitingConfirmation,
        Some("installing") => UpdatePhase::Installing,
        Some("failed") => UpdatePhase::Failed,
        _ => UpdatePhase::Downloading,
    };
    launch(
        LaunchConfig::new()
            .with_window(
                WindowConfig::new_app(UpdateWindowPreview { phase })
                    .with_title("Arcadium Update QA")
                    .with_size(900., 650.)
                    .with_decorations(false),
            )
            .with_plugin(freya::borderless::BorderlessPlugin::new())
            .with_default_font("Inter")
            .with_font(
                "Inter",
                include_bytes!("../assets/fonts/Inter-Regular.ttf").as_slice(),
            ),
    );
}
