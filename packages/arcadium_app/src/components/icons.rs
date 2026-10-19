use arcadium_content::packages::ProviderId;
use freya::prelude::*;
use skia_safe as skia;
use std::{cell::RefCell, rc::Rc};

use crate::{AppAssets, theme::colors};

#[derive(Clone, Copy, PartialEq)]
pub struct IconTint(pub State<Color>);

#[derive(PartialEq)]
pub struct Icon {
    pub icon: IconType,
    pub size_px: f32,
    pub color: Option<Color>,
    pub rotation: Option<f32>,
}

impl Icon {
    pub fn new(icon: impl Into<IconType>) -> Self {
        let icon = icon.into();
        let color = match icon {
            IconType::Modrinth => Some(colors::MODRINTH_COLOR),
            IconType::Curseforge => Some(colors::CURSEFORGE_COLOR),
            _ => None,
        };

        Self {
            icon,
            size_px: 24.,
            color,
            rotation: None,
        }
    }

    pub fn size(mut self, size_px: f32) -> Self {
        self.size_px = size_px;
        self
    }

    pub fn color(mut self, color: Color) -> Self {
        self.color = Some(color);
        self
    }

    /// Degrees clockwise
    pub fn rotate(mut self, degrees: f32) -> Self {
        self.rotation = Some(degrees);
        self
    }
}

impl Component for Icon {
    fn render(&self) -> impl IntoElement {
        let path = self.icon.path();
        let load = move || (path, AppAssets::get_bytes(path).unwrap_or_default());
        let mut bytes = use_state(load);
        if bytes.peek().0 != path {
            bytes.set(load());
        }
        let tint = use_hook(try_consume_context::<IconTint>);

        let color = self
            .color
            .or_else(|| tint.map(|tint| *tint.0.read()))
            .unwrap_or_else(colors::fg_primary);

        let fill = crate::theme::accent::themed_fill(color);
        if !matches!(fill, Fill::Color(_)) {
            return GradientIcon {
                path,
                size: self.size_px,
                fill,
                rotation: self.rotation,
            }
            .into_element();
        }

        SvgViewer::new((path, bytes.peek().1.clone()))
            .show_loader(false)
            .width(Size::px(self.size_px))
            .height(Size::px(self.size_px))
            .color(color)
            .map(self.rotation, |svg, degrees| svg.rotation(degrees))
            .into_element()
    }
}

#[derive(PartialEq)]
struct GradientIcon {
    path: &'static str,
    size: f32,
    fill: Fill,
    rotation: Option<f32>,
}

impl Component for GradientIcon {
    fn render(&self) -> impl IntoElement {
        let pixels = (self.size * *Platform::get().scale_factor.read() as f32)
            .ceil()
            .max(1.) as i32;
        let cache = use_hook(|| Rc::new(RefCell::new(None::<(&'static str, i32, skia::Image)>)));
        {
            let mut cache = cache.borrow_mut();
            if cache
                .as_ref()
                .is_none_or(|(path, size, _)| *path != self.path || *size != pixels)
            {
                let image = AppAssets::get_bytes(self.path).and_then(|bytes| {
                    let mut dom =
                        skia::svg::Dom::from_bytes(&bytes, skia::FontMgr::empty()).ok()?;
                    dom.set_container_size((pixels, pixels));
                    let mut root = dom.root();
                    root.set_width(skia::svg::Length::new(
                        pixels as f32,
                        skia::svg::LengthUnit::PX,
                    ));
                    root.set_height(skia::svg::Length::new(
                        pixels as f32,
                        skia::svg::LengthUnit::PX,
                    ));
                    root.set_color(skia::Color::WHITE.into());
                    let mut surface = skia::surfaces::raster_n32_premul((pixels, pixels))?;
                    dom.render(surface.canvas());
                    Some(surface.image_snapshot())
                });
                *cache = image.map(|image| (self.path, pixels, image));
            }
        }
        let mut fill = use_state(|| self.fill.clone());
        fill.set_if_modified(self.fill.clone());
        let mut rotation = use_state(|| self.rotation);
        rotation.set_if_modified(self.rotation);
        canvas(RenderCallback::new(move |context| {
            let cache = cache.borrow();
            let Some((_, _, mask)) = cache.as_ref() else {
                return;
            };
            let bounds = skia::Rect::from_wh(context.size.width, context.size.height);
            let saved = context
                .canvas
                .save_layer(&skia::canvas::SaveLayerRec::default().bounds(&bounds));
            if let Some(degrees) = *rotation.peek() {
                context.canvas.rotate(degrees, Some(bounds.center()));
            }
            context
                .canvas
                .draw_image_rect(mask, None, bounds, &skia::Paint::default());
            let mut paint = skia::Paint::default();
            fill.peek()
                .apply_to_paint(&mut paint, Area::new(Default::default(), context.size));
            paint.set_blend_mode(skia::BlendMode::SrcIn);
            context.canvas.draw_rect(bounds, &paint);
            context.canvas.restore_to_count(saved);
        }))
        .width(Size::px(self.size))
        .height(Size::px(self.size))
    }
}

impl From<ProviderId> for IconType {
    fn from(val: ProviderId) -> Self {
        match val {
            ProviderId::Modrinth => IconType::Modrinth,
            ProviderId::CurseForge => IconType::Curseforge,
            ProviderId::Local => IconType::Folder,
        }
    }
}

#[derive(arcadium_macro::IconNamed, Debug, PartialEq, Clone, Copy)]
#[allow(dead_code)]
pub enum IconType {
    AlertCircle,
    AlertTriangle,
    Announcement01,
    ArrowLeft,
    ArrowRight,
    Brush01,
    BarChartSquare02,
    Bell01,
    Calendar,
    CheckCircle,
    Check,
    ChevronDown,
    ChevronRight,
    ChevronsLeft,
    ChevronsRight,
    ClipboardCheck,
    ClockRewind,
    CodeSnippet02,
    Colors,
    Copy01,
    Curseforge,
    Database01,
    Discord,
    DotsGrid,
    DotsVertical,
    Download01,
    DownloadCloud02,
    Eye,
    File02,
    FilePlus02,
    FileX02,
    Folder,
    FolderCheck,
    FolderDownload,
    Github,
    Globe01,
    HelpCircle,
    IconLogo,
    InfoCircle,
    Key01,
    LayoutTop,
    LineChartUp01,
    Link03,
    LinkExternal01,
    Loading02,
    LogOut01,
    Maximize01,
    MessageTextSquare01,
    Minus,
    Modrinth,
    Moon01,
    OnboardingAccount,
    OnboardingComplete,
    OnboardingLanguage,
    OnboardingPreferences,
    OnboardingWelcome,
    PaintPour,
    ParagraphWrap,
    Pencil01,
    Play,
    Plus,
    RefreshCcw02,
    RefreshCw01,
    Rocket02,
    SearchMd,
    Settings01,
    Settings02,
    Settings04,
    Sliders04,
    Square,
    Terminal,
    Trash01,
    Users01,
    XClose,
    X,
}
