use std::time::Duration;

use anyhow::{Context, Result};
use freya::winit::{
    platform::macos::{WindowAttributesExtMacOS, WindowExtMacOS},
    window::{Window, WindowAttributes},
};
use objc2::{AnyThread, MainThreadMarker};
use objc2_app_kit::{NSApplication, NSApplicationActivationPolicy, NSImage};
use objc2_foundation::NSData;

pub fn window_attributes(attributes: WindowAttributes) -> WindowAttributes {
    attributes
        .with_title_hidden(true)
        .with_titlebar_transparent(true)
        .with_titlebar_buttons_hidden(true)
        .with_fullsize_content_view(true)
        .with_has_shadow(true)
}

pub fn initialize_window(window: &Window, start_maximized: bool) {
    if let Err(error) = set_application_icon() {
        tracing::warn!("could not set the Dock icon: {error:#}");
    }
    if start_maximized {
        super::toggle_window_maximized(window);
    }
}

pub fn set_application_icon() -> Result<()> {
    let mtm = MainThreadMarker::new().context("Dock icon requires the main thread")?;
    let data = NSData::with_bytes(include_bytes!("../../icons/1024x1024.png"));
    let image =
        NSImage::initWithData(NSImage::alloc(), &data).context("invalid application icon")?;
    anyhow::ensure!(image.isValid(), "application icon could not be decoded");
    unsafe { NSApplication::sharedApplication(mtm).setApplicationIconImage(Some(&image)) };
    Ok(())
}

pub fn toggle_fullscreen(window: &Window) {
    if !window.set_simple_fullscreen(!window.simple_fullscreen()) {
        tracing::warn!("could not switch the window fullscreen state");
    }
}

pub fn set_menu_bar_only(menu_bar_only: bool) {
    if let Some(mtm) = MainThreadMarker::new() {
        NSApplication::sharedApplication(mtm).setActivationPolicy(if menu_bar_only {
            NSApplicationActivationPolicy::Accessory
        } else {
            NSApplicationActivationPolicy::Regular
        });
    }
}

pub fn loop_memory_collector() {
    tokio::spawn(async {
        tokio::time::sleep(Duration::from_secs(8)).await;
        loop {
            unsafe {
                unsafe extern "C" {
                    fn malloc_zone_pressure_relief(
                        zone: *mut core::ffi::c_void,
                        goal: usize,
                    ) -> usize;
                }
                malloc_zone_pressure_relief(core::ptr::null_mut(), 0);
            }
            tokio::time::sleep(Duration::from_secs(60)).await;
        }
    });
}
