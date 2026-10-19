use bytes::Bytes;

use crate::AppAssets;

pub mod accent;
pub mod colors;

pub const NAVBAR_HEIGHT_PX: f32 = 80.;
pub const HOME_PADDING_PX: f32 = 48.;
pub const DEFAULT_FONT: &str = "Inter";
pub const MONO_FONT: &str = "JetBrains Mono";

pub fn load_fonts() -> Vec<(&'static str, Bytes)> {
    const FONTS: [(&str, &str); 2] = [
        (DEFAULT_FONT, "fonts/Inter-Regular.ttf"),
        (MONO_FONT, "fonts/JetBrainsMono/JetBrainsMonoVariable.ttf"),
    ];

    FONTS
        .iter()
        .filter_map(|&(weight, file)| {
            let bytes = AppAssets::get_bytes(file).or_else(|| {
                tracing::error!("Failed to load embedded font '{file}'");
                None
            })?;

            Some((weight, bytes))
        })
        .collect()
}
