use freya::prelude::*;

/// The app's one progress meter: a pill-shaped track with a filled portion.
///
/// `pct` is 0-100 and is clamped here, so callers can hand over a raw ratio
/// without guarding against a denominator that grew mid-flight.
pub fn progress_track(pct: f32, height: f32, fill: Color, bg: Color) -> Rect {
    let foreground = if fill == crate::theme::colors::brand() {
        AccentProgressFill { pct, height }.into_element()
    } else {
        progress_fill(pct, height).background(fill).into_element()
    };
    rect()
        .width(Size::fill())
        .height(Size::px(height))
        .corner_radius(CornerRadius::new_all(height / 2.))
        .background(bg)
        .child(foreground)
}

fn progress_fill(pct: f32, height: f32) -> Rect {
    rect()
        .width(Size::percent(pct.clamp(0.0, 100.0)))
        .height(Size::fill())
        .corner_radius(CornerRadius::new_all(height / 2.))
}

#[derive(PartialEq)]
struct AccentProgressFill {
    pct: f32,
    height: f32,
}

impl Component for AccentProgressFill {
    fn render(&self) -> impl IntoElement {
        progress_fill(self.pct, self.height).background(crate::theme::accent::fill(1.))
    }
}
