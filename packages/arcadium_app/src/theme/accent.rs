use std::{
    path::Path,
    sync::Arc,
    time::{Duration, Instant},
};

use freya::prelude::*;
use freya_core::current_context::CurrentContext;
use tokio::io::AsyncReadExt;

const MAX_THEME_BYTES: u64 = 8192;
const POLL_INTERVAL: Duration = Duration::from_millis(500);
const FRAME_INTERVAL: Duration = Duration::from_millis(33);
const CYCLE_SECONDS: f32 = 4.;

#[derive(Clone, Debug, PartialEq)]
pub struct SharedAccent {
    pub gradient: bool,
    pub preset: String,
    pub custom: bool,
    pub start: Color,
    pub end: Color,
    pub solid: Color,
    pub animate: bool,
    pub speed: f32,
    pub smoothness: f32,
}

impl Default for SharedAccent {
    fn default() -> Self {
        Self {
            gradient: true,
            preset: "Arcadium".to_string(),
            custom: false,
            start: Color::from_rgb(58, 160, 255),
            end: Color::from_rgb(54, 241, 176),
            solid: Color::from_rgb(43, 75, 255),
            animate: true,
            speed: 1.,
            smoothness: 100.,
        }
    }
}

impl SharedAccent {
    fn parse(bytes: &[u8]) -> Option<Self> {
        if bytes.len() as u64 > MAX_THEME_BYTES {
            return None;
        }
        let document: serde_json::Value = serde_json::from_slice(bytes).ok()?;
        let object = document.as_object()?;
        if object.get("version")?.as_u64()? != 1 {
            return None;
        }
        let preset = object.get("preset")?.as_str()?;
        if preset.is_empty() || preset.len() > 128 || preset.chars().any(char::is_control) {
            return None;
        }
        let color = |name: &str| {
            let hex = object.get(name)?.as_str()?;
            if hex.len() != 7
                || !hex.starts_with('#')
                || !hex[1..].bytes().all(|b| b.is_ascii_hexdigit())
            {
                return None;
            }
            Color::from_hex(hex)
        };
        let number = |name: &str, default: f32, maximum: f32| {
            let value = match object.get(name) {
                None => default,
                Some(value) => value.as_f64()? as f32,
            };
            value.is_finite().then(|| value.clamp(0., maximum))
        };
        Some(Self {
            gradient: object.get("gradient")?.as_bool()?,
            preset: preset.to_string(),
            custom: object.get("custom")?.as_bool()?,
            start: color("start")?,
            end: color("end")?,
            solid: color("solid")?,
            animate: object.get("animate")?.as_bool()?,
            speed: number("speed", 1., 5.)?,
            smoothness: number("smoothness", 100., 100.)?,
        })
    }

    pub fn representative(&self) -> Color {
        if self.gradient {
            self.start
        } else {
            self.solid
        }
    }

    fn fill_at(&self, phase: f32, brightness: f32) -> Fill {
        self.fill_at_opacity(phase, brightness, 255)
    }

    fn fill_at_opacity(&self, phase: f32, brightness: f32, opacity: u8) -> Fill {
        if !self.gradient {
            return Fill::Color((self.solid * brightness).with_a(opacity));
        }
        let phase = if self.animate && phase.is_finite() {
            phase.rem_euclid(1.) * 2.
        } else {
            0.
        };
        let mut gradient = LinearGradient::new().angle(270.);
        let tint = |color: Color| (color * brightness).with_a(opacity);
        gradient = gradient.stop((tint(self.sample(phase)), 0.));
        let width = (self.smoothness / 100.).max(0.02);
        let mut previous = phase;
        for section in phase.floor() as i32..=(phase + 1.).floor() as i32 {
            for edge in [0.5 - width / 2., 0.5 + width / 2.] {
                let boundary = section as f32 + edge;
                if boundary > previous && boundary < phase + 1. {
                    gradient =
                        gradient.stop((tint(self.sample(boundary)), (boundary - phase) * 100.));
                    previous = boundary;
                }
            }
        }
        gradient.stop((tint(self.sample(phase + 1.)), 100.)).into()
    }

    fn sample(&self, position: f32) -> Color {
        let triangle = 1. - (position.rem_euclid(2.) - 1.).abs();
        let blend = ((triangle - 0.5) / (self.smoothness / 100.).max(0.02) + 0.5).clamp(0., 1.);
        mix(self.start, self.end, blend)
    }

    pub fn foreground(&self) -> Color {
        let dark = Color::from_rgb(10, 19, 27);
        let backgrounds = if self.gradient {
            [self.start, mix(self.start, self.end, 0.5), self.end]
        } else {
            [self.solid; 3]
        };
        let score = |foreground| {
            backgrounds
                .iter()
                .map(|&color| contrast(foreground, color))
                .fold(f32::INFINITY, f32::min)
        };
        if score(dark) >= score(Color::WHITE) {
            dark
        } else {
            Color::WHITE
        }
    }
}

#[derive(Clone, Copy)]
struct AccentContext {
    theme: State<Arc<SharedAccent>>,
    phase: State<f32>,
}

fn context() -> Option<AccentContext> {
    CurrentContext::try_with(|_| try_consume_root_context::<AccentContext>()).flatten()
}

pub fn current() -> Arc<SharedAccent> {
    context()
        .map(|state| state.theme.read().cloned())
        .unwrap_or_else(|| Arc::new(SharedAccent::default()))
}

pub fn fill(brightness: f32) -> Fill {
    let theme = current();
    let phase = if theme.gradient && theme.animate {
        context().map(|state| *state.phase.read()).unwrap_or(0.)
    } else {
        0.
    };
    theme.fill_at(phase, brightness)
}

pub fn themed_fill(value: impl Into<Fill>) -> Fill {
    let value = value.into();
    let Fill::Color(color) = value else {
        return value;
    };
    let theme = current();
    if !theme.gradient || color.a() == 0 {
        return value;
    }
    for brightness in [1., 0.92, 0.84, 0.52] {
        let expected = theme.representative() * brightness;
        if color.r() == expected.r() && color.g() == expected.g() && color.b() == expected.b() {
            let phase = if theme.animate {
                context().map(|state| *state.phase.read()).unwrap_or(0.)
            } else {
                0.
            };
            return theme.fill_at_opacity(phase, brightness, color.a());
        }
    }
    value
}

pub trait AccentBorderExt: StyleExt + ChildrenExt + LayoutExt {
    fn themed_border(mut self, border: impl Into<Option<Border>>) -> Self {
        let Some(border) = border.into() else {
            return self;
        };
        let color = border.fill;
        let theme = current();
        let matching = [1., 0.92, 0.84, 0.52].into_iter().any(|brightness| {
            let expected = theme.representative() * brightness;
            color.r() == expected.r() && color.g() == expected.g() && color.b() == expected.b()
        });
        if theme.gradient && matching && color.a() > 0 {
            let corners = self.get_style().corner_radius;
            let padding = self.get_layout().layout.padding;
            self.child(GradientBorder {
                border,
                corners,
                padding,
            })
        } else {
            StyleExt::border(self, border)
        }
    }
}

impl<T: StyleExt + ChildrenExt + LayoutExt> AccentBorderExt for T {}

#[derive(PartialEq)]
struct GradientBorder {
    border: Border,
    corners: CornerRadius,
    padding: Gaps,
}

impl Component for GradientBorder {
    fn render(&self) -> impl IntoElement {
        use freya::elements::rect::RectElement;
        use skia_safe::{Paint, Rect as SkRect};
        let mut fill = use_state(|| themed_fill(self.border.fill));
        fill.set_if_modified(themed_fill(self.border.fill));
        let border = self.border.clone();
        let corners = self.corners;
        let scale = *Platform::get().scale_factor.read() as f32;
        let horizontal = self.padding.horizontal() * scale;
        let vertical = self.padding.vertical() * scale;
        canvas(RenderCallback::new(move |context| {
            let bounds = SkRect::from_wh(context.size.width, context.size.height);
            let mut paint = Paint::default();
            paint.set_anti_alias(true);
            fill.peek()
                .apply_to_paint(&mut paint, Area::new(Default::default(), context.size));
            match RectElement::border_shape(bounds, &corners, &border) {
                BorderShape::DRRect(outer, inner) => {
                    context.canvas.draw_drrect(&outer, &inner, &paint);
                }
                BorderShape::Path(path) => {
                    context.canvas.draw_path(&path, &paint);
                }
            }
        }))
        .position(
            Position::new_absolute()
                .top(-self.padding.top())
                .left(-self.padding.left()),
        )
        .width(Size::func_data(
            move |context| Some(context.parent + horizontal),
            &horizontal.to_bits(),
        ))
        .height(Size::func_data(
            move |context| Some(context.parent + vertical),
            &vertical.to_bits(),
        ))
    }
}

pub fn use_shared_accent() {
    let mut theme = use_state(|| Arc::new(SharedAccent::default()));
    let phase = use_state(|| 0.);
    use_hook(|| provide_root_context(AccentContext { theme, phase }));
    use_hook(move || {
        spawn(async move {
            let Ok(directory) = arcadium_common::paths::config_dir() else {
                return;
            };
            let path = directory.join("theme-accent.json");
            loop {
                if let Ok(next) = load(&path).await {
                    theme.set_if_modified(Arc::new(next));
                }
                tokio::time::sleep(POLL_INTERVAL).await;
            }
        })
    });
}

async fn load(path: &Path) -> std::io::Result<SharedAccent> {
    let file = match tokio::fs::File::open(path).await {
        Ok(file) => file,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            return Ok(SharedAccent::default());
        }
        Err(error) => return Err(error),
    };
    let mut bytes = Vec::new();
    file.take(MAX_THEME_BYTES + 1)
        .read_to_end(&mut bytes)
        .await?;
    SharedAccent::parse(&bytes).ok_or_else(|| {
        std::io::Error::new(
            std::io::ErrorKind::InvalidData,
            "Invalid shared theme accent",
        )
    })
}

#[derive(PartialEq)]
pub struct AccentAnimationDriver;

impl Component for AccentAnimationDriver {
    fn render(&self) -> impl IntoElement {
        let Some(shared) = context() else {
            return rect().into_element();
        };
        let theme = shared.theme.read();
        let reset = !crate::motion::use_animations_enabled() || !theme.gradient || !theme.animate;
        let motion = !reset && theme.speed > 0.;
        let focused = *Platform::get().is_app_focused.read();
        let mut running = use_state(|| false);
        running.set_if_modified(motion && focused);
        let mut phase = shared.phase;
        use_side_effect_with_deps(&reset, move |&reset| {
            if reset {
                phase.set_if_modified(0.);
            }
        });
        use_hook(move || {
            spawn(async move {
                let mut last = Instant::now();
                let mut was_running = false;
                loop {
                    let now = Instant::now();
                    let active = *running.peek();
                    if active && was_running {
                        let next = advance(
                            *phase.peek(),
                            now.duration_since(last).as_secs_f32(),
                            shared.theme.peek().speed,
                        );
                        phase.set(next);
                    }
                    last = now;
                    was_running = active;
                    if active {
                        tokio::time::sleep(FRAME_INTERVAL).await;
                    } else {
                        tokio::time::sleep(Duration::from_millis(250)).await;
                    }
                }
            })
        });
        rect().into_element()
    }
}

fn advance(phase: f32, elapsed: f32, speed: f32) -> f32 {
    (phase + elapsed / CYCLE_SECONDS * speed).rem_euclid(1.)
}

fn mix(start: Color, end: Color, amount: f32) -> Color {
    let channel =
        |a: u8, b: u8| (f32::from(a) + (f32::from(b) - f32::from(a)) * amount).round() as u8;
    Color::from_rgb(
        channel(start.r(), end.r()),
        channel(start.g(), end.g()),
        channel(start.b(), end.b()),
    )
}

fn contrast(foreground: Color, background: Color) -> f32 {
    let luminance = |color: Color| {
        let channel = |value: u8| {
            let value = f32::from(value) / 255.;
            if value <= 0.04045 {
                value / 12.92
            } else {
                ((value + 0.055) / 1.055).powf(2.4)
            }
        };
        0.2126 * channel(color.r()) + 0.7152 * channel(color.g()) + 0.0722 * channel(color.b())
    };
    let a = luminance(foreground);
    let b = luminance(background);
    (a.max(b) + 0.05) / (a.min(b) + 0.05)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn document() -> serde_json::Value {
        serde_json::json!({"version":1,"gradient":true,"preset":"Arcadium","custom":false,"start":"#3AA0FF","end":"#36F1B0","solid":"#2B4BFF","animate":true})
    }

    #[test]
    fn default_matches_the_client_arcadium_preset() {
        assert_eq!(
            SharedAccent::parse(&serde_json::to_vec(&document()).unwrap()),
            Some(SharedAccent::default())
        );
        assert_eq!(
            SharedAccent::default().sample(0.),
            Color::from_rgb(58, 160, 255)
        );
        assert_eq!(
            SharedAccent::default().sample(1.),
            Color::from_rgb(54, 241, 176)
        );
        assert_eq!(
            SharedAccent::default().sample(2.),
            Color::from_rgb(58, 160, 255)
        );
    }

    #[test]
    fn malformed_or_unsupported_documents_are_rejected() {
        for (name, value) in [
            ("version", serde_json::json!(2)),
            ("gradient", serde_json::json!("true")),
            ("start", serde_json::json!("#12")),
            ("start", serde_json::json!("#abcdef00")),
            ("end", serde_json::json!("#GGGGGG")),
            ("solid", serde_json::json!("3AA0FF")),
            ("preset", serde_json::json!("\nArcadium")),
            ("speed", serde_json::json!("fast")),
            ("smoothness", serde_json::json!(null)),
        ] {
            let mut json = document();
            json[name] = value;
            assert!(
                SharedAccent::parse(&serde_json::to_vec(&json).unwrap()).is_none(),
                "{name}"
            );
        }
        assert!(SharedAccent::parse(&vec![b' '; MAX_THEME_BYTES as usize + 1]).is_none());
        assert!(SharedAccent::parse(b"{not json}").is_none());
    }

    #[test]
    fn speed_and_smoothness_follow_shared_settings_with_legacy_defaults() {
        let mut json = document();
        json["speed"] = serde_json::json!(2.5);
        json["smoothness"] = serde_json::json!(25);
        let theme = SharedAccent::parse(&serde_json::to_vec(&json).unwrap()).unwrap();
        assert_eq!(theme.speed, 2.5);
        assert_eq!(theme.smoothness, 25.);
        assert_eq!(advance(0., 1., 1.), 0.25);
        assert_eq!(advance(0., 1., 2.), 0.5);
        assert_eq!(advance(0.25, 100., 0.), 0.25);
        assert_eq!(advance(0., 4., 0.5), 0.5);
        assert_eq!(theme.sample(0.1), theme.start);
        assert_ne!(SharedAccent::default().sample(0.1), theme.start);
        assert_eq!(theme.sample(0.9), theme.end);
        assert_eq!(theme.sample(0.5), SharedAccent::default().sample(0.5));
        assert_ne!(
            theme.fill_at(0.1, 1.),
            SharedAccent::default().fill_at(0.1, 1.)
        );
        json["speed"] = serde_json::json!(-1);
        json["smoothness"] = serde_json::json!(150);
        let clamped = SharedAccent::parse(&serde_json::to_vec(&json).unwrap()).unwrap();
        assert_eq!(clamped.speed, 0.);
        assert_eq!(clamped.smoothness, 100.);
    }

    #[test]
    fn gradient_is_spatial_and_continuous_across_the_loop() {
        let theme = SharedAccent::default();
        assert_ne!(theme.fill_at(0., 1.), theme.fill_at(0.25, 1.));
        assert_eq!(theme.fill_at(0., 1.), theme.fill_at(1., 1.));
        let mut still = theme.clone();
        still.animate = false;
        assert_eq!(still.fill_at(0., 1.), still.fill_at(0.75, 1.));
        assert!(matches!(still.fill_at(0., 1.), Fill::LinearGradient(_)));
    }

    #[test]
    fn solid_mode_and_foregrounds_remain_readable() {
        let mut theme = SharedAccent::default();
        assert!(contrast(theme.foreground(), theme.start) >= 4.5);
        assert!(contrast(theme.foreground(), theme.end) >= 4.5);
        theme.gradient = false;
        assert_eq!(theme.fill_at(0.5, 1.), Fill::Color(theme.solid));
        theme.solid = Color::from_rgb(12, 17, 35);
        assert_eq!(theme.foreground(), Color::WHITE);
    }

    #[tokio::test]
    async fn file_changes_and_missing_file_are_read_without_restarting() {
        let directory =
            std::env::temp_dir().join(format!("arcadium-accent-{}", uuid::Uuid::new_v4()));
        tokio::fs::create_dir(&directory).await.unwrap();
        let path = directory.join("theme-accent.json");
        assert_eq!(load(&path).await.unwrap(), SharedAccent::default());
        let mut json = document();
        tokio::fs::write(&path, serde_json::to_vec(&json).unwrap())
            .await
            .unwrap();
        let original = load(&path).await.unwrap();
        json["start"] = serde_json::json!("#FF3344");
        json["custom"] = serde_json::json!(true);
        tokio::fs::write(&path, serde_json::to_vec(&json).unwrap())
            .await
            .unwrap();
        let changed = load(&path).await.unwrap();
        assert_ne!(changed, original);
        assert!(changed.custom);
        assert_eq!(changed.start, Color::from_rgb(255, 51, 68));
        tokio::fs::write(&path, b"{interrupted").await.unwrap();
        assert!(load(&path).await.is_err());
        tokio::fs::remove_file(&path).await.unwrap();
        tokio::fs::remove_dir(&directory).await.unwrap();
    }

    #[test]
    fn primary_controls_follow_theme_and_phase_changes_in_place() {
        use crate::components::{Button, checkbox_labeled, progress_track, toggle};
        use freya::elements::rect::RectElement;
        use freya_testing::TestingRunner;
        use std::{any::Any, cell::Cell, collections::HashMap, rc::Rc};

        let renders = Rc::new(Cell::new(0));
        let rendering = renders.clone();
        let app = move || {
            rendering.set(rendering.get() + 1);
            let checked = use_state(|| true);
            rect()
                .vertical()
                .spacing(12.)
                .padding(24.)
                .width(Size::fill())
                .height(Size::fill())
                .background(super::super::colors::page())
                .font_family("Inter")
                .child(Button::new().primary().text("Launch"))
                .child(toggle(checked))
                .child(checkbox_labeled(checked, "Selected"))
                .child(progress_track(
                    65.,
                    8.,
                    super::super::colors::brand(),
                    Color::BLACK,
                ))
        };
        let (mut test, mut shared) = TestingRunner::new(
            app,
            Size2D::new(440., 240.),
            |runner| {
                runner.provide_root_context(|| AccentContext {
                    theme: State::create(Arc::new(SharedAccent::default())),
                    phase: State::create(0.),
                })
            },
            1.,
        );
        test.set_fonts(HashMap::from([(
            "Inter",
            include_bytes!("../../assets/fonts/Inter-Regular.ttf").as_slice(),
        )]));
        let fills = |test: &TestingRunner| {
            test.find_many(|_, element| {
                (element as &dyn Any)
                    .downcast_ref::<RectElement>()
                    .and_then(|rect| {
                        matches!(rect.style.background, Fill::LinearGradient(_))
                            .then(|| rect.style.background.clone())
                    })
            })
        };
        test.sync_and_update();
        let initial = fills(&test);
        assert_eq!(initial.len(), 4);
        assert!(
            initial
                .iter()
                .all(|fill| *fill == SharedAccent::default().fill_at(0., 1.))
        );
        let directory =
            Path::new(env!("CARGO_MANIFEST_DIR")).join("../../build/theme-accent-evidence");
        std::fs::create_dir_all(&directory).unwrap();
        test.render_to_file(directory.join("arcadium-default.png"));

        let original_render_count = renders.get();
        test.run_in(|| shared.phase.set(0.25));
        test.sync_and_update();
        let moved = fills(&test);
        assert_eq!(moved.len(), 4);
        assert_ne!(moved, initial);
        assert_eq!(
            renders.get(),
            original_render_count,
            "Animation must not rebuild the parent page"
        );
        test.render_to_file(directory.join("arcadium-flow.png"));

        test.run_in(|| {
            let mut theme = SharedAccent::default();
            theme.gradient = false;
            theme.solid = Color::from_rgb(182, 62, 28);
            shared.theme.set(Arc::new(theme));
        });
        test.sync_and_update();
        assert!(fills(&test).is_empty());
        let solid_fills = test.find_many(|_, element| {
            (element as &dyn Any)
                .downcast_ref::<RectElement>()
                .map(|rect| rect.style.background.clone())
        });
        assert!(
            solid_fills
                .iter()
                .filter(|fill| **fill == Fill::Color(Color::from_rgb(182, 62, 28)))
                .count()
                >= 4
        );
        test.render_to_file(directory.join("shared-solid.png"));
    }

    #[test]
    fn selected_surfaces_and_borders_keep_geometry_and_animate() {
        check_selected_surfaces(1.);
        check_selected_surfaces(2.);
    }

    fn check_selected_surfaces(scale: f64) {
        use crate::components::{Icon, IconType};
        use freya_testing::TestingRunner;
        use std::{cell::Cell, collections::HashMap, rc::Rc};

        #[derive(PartialEq)]
        struct Detail;
        impl Component for Detail {
            fn render(&self) -> impl IntoElement {
                let color = super::super::colors::brand();
                rect()
                    .horizontal()
                    .spacing(12.)
                    .cross_align(Alignment::Center)
                    .width(Size::px(320.))
                    .height(Size::px(140.))
                    .padding(12.)
                    .corner_radius(CornerRadius::new_all(12.))
                    .background(themed_fill(color.with_a(38)))
                    .themed_border(Border::new().fill(color).width(2.))
                    .child(Icon::new(IconType::CheckCircle).size(24.).color(color))
                    .child(label().text("Selected account").color(themed_fill(color)))
            }
        }
        let renders = Rc::new(Cell::new(0));
        let rendering = renders.clone();
        let app = move || {
            rendering.set(rendering.get() + 1);
            rect()
                .vertical()
                .padding(20.)
                .spacing(12.)
                .width(Size::fill())
                .height(Size::fill())
                .background(Color::BLACK)
                .font_family("Inter")
                .child(Detail)
                .child(
                    rect()
                        .width(Size::px(320.))
                        .height(Size::px(20.))
                        .background(themed_fill(Color::RED)),
                )
        };
        let (mut test, mut shared) = TestingRunner::new(
            app,
            Size2D::new(360. * scale as f32, 220. * scale as f32),
            |runner| {
                runner.provide_root_context(|| AccentContext {
                    theme: State::create(Arc::new(SharedAccent::default())),
                    phase: State::create(0.),
                })
            },
            scale,
        );
        test.set_fonts(HashMap::from([(
            "Inter",
            include_bytes!("../../assets/fonts/Inter-Regular.ttf").as_slice(),
        )]));
        let directory =
            Path::new(env!("CARGO_MANIFEST_DIR")).join("../../build/theme-accent-evidence");
        std::fs::create_dir_all(&directory).unwrap();
        test.sync_and_update();
        test.render_to_file(directory.join("details-gradient-a.png"));
        let first = image::open(directory.join("details-gradient-a.png"))
            .unwrap()
            .to_rgba8();
        let edge = first
            .get_pixel((100. * scale) as u32, (21. * scale) as u32)
            .0;
        assert!(
            edge[1] > 100 && edge[2] > 100,
            "The gradient border moved inside the card padding"
        );
        let count = renders.get();
        test.run_in(|| shared.phase.set(0.25));
        test.sync_and_update();
        test.render_to_file(directory.join("details-gradient-b.png"));
        let second = image::open(directory.join("details-gradient-b.png"))
            .unwrap()
            .to_rgba8();
        assert_eq!(
            count,
            renders.get(),
            "Accent animation rebuilt the containing page"
        );
        let changed = |x0: u32, y0: u32, x1: u32, y1: u32| {
            let (x0, y0, x1, y1) = (
                (x0 as f64 * scale) as u32,
                (y0 as f64 * scale) as u32,
                (x1 as f64 * scale) as u32,
                (y1 as f64 * scale) as u32,
            );
            (y0..y1)
                .flat_map(|y| (x0..x1).map(move |x| (x, y)))
                .filter(|&(x, y)| first.get_pixel(x, y) != second.get_pixel(x, y))
                .count()
        };
        assert!(
            changed(70, 20, 290, 23) > 100,
            "The padded card border did not flow at its outer edge"
        );
        assert!(
            changed(32, 35, 60, 150) > 20,
            "The selected icon did not use a spatial gradient"
        );
        assert!(
            changed(70, 35, 260, 150) > 50,
            "The accent text or tinted surface did not flow"
        );
        assert_eq!(
            changed(20, 172, 340, 192),
            0,
            "The theme recolored a non-accent surface"
        );
        test.run_in(|| {
            let mut theme = SharedAccent::default();
            theme.gradient = false;
            shared.theme.set(Arc::new(theme));
        });
        test.sync_and_update();
        test.render_to_file(directory.join("details-solid.png"));
    }
}
