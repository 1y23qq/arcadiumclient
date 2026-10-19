#[cfg(target_os = "macos")]
mod test {
    use std::time::{Duration, Instant};

    use arcadium_app::platform;
    use freya::winit::{
        application::ApplicationHandler,
        dpi::LogicalSize,
        event::WindowEvent,
        event_loop::{ActiveEventLoop, ControlFlow, EventLoop},
        platform::macos::WindowExtMacOS,
        raw_window_handle::{HasWindowHandle, RawWindowHandle},
        window::{Window, WindowId},
    };
    use objc2::{MainThreadMarker, rc::Retained};
    use objc2_app_kit::{NSApplication, NSImage, NSView, NSWindow};
    use objc2_foundation::{NSRect, NSSize};

    fn verify_icon(icon: &NSImage) {
        assert!(icon.isValid());
        let directory = std::env::temp_dir().join(format!(
            "arcadium-dock-icon-{}-{}",
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        std::fs::create_dir(&directory).unwrap();
        let tiff = directory.join("icon.tiff");
        let png = directory.join("icon.png");
        std::fs::write(&tiff, icon.TIFFRepresentation().unwrap().to_vec()).unwrap();
        assert!(
            std::process::Command::new("/usr/bin/sips")
                .args(["--setProperty", "format", "png"])
                .arg(&tiff)
                .arg("--out")
                .arg(&png)
                .stdout(std::process::Stdio::null())
                .status()
                .unwrap()
                .success()
        );
        let actual = image::open(&png).unwrap().into_rgba8();
        let expected = image::load_from_memory(include_bytes!("../icons/1024x1024.png"))
            .unwrap()
            .into_rgba8();
        std::fs::remove_dir_all(directory).unwrap();
        assert_eq!(actual.dimensions(), expected.dimensions());
        for (index, (actual, expected)) in actual.pixels().zip(expected.pixels()).enumerate() {
            assert!(
                actual[3].abs_diff(expected[3]) <= 1,
                "Dock icon alpha differs at pixel {index}"
            );
            if expected[3] == 255 {
                assert!(
                    (0..3).all(|channel| actual[channel].abs_diff(expected[channel]) <= 2),
                    "Dock icon color differs at pixel {index}"
                );
            }
        }
    }

    fn native(window: &Window) -> Retained<NSWindow> {
        let handle = window.window_handle().unwrap();
        let RawWindowHandle::AppKit(handle) = handle.as_raw() else {
            panic!("expected AppKit");
        };
        unsafe { &*handle.ns_view.as_ptr().cast::<NSView>() }
            .window()
            .unwrap()
    }

    fn frames_match(actual: NSRect, expected: NSRect) -> bool {
        [
            (actual.origin.x, expected.origin.x),
            (actual.origin.y, expected.origin.y),
            (actual.size.width, expected.size.width),
            (actual.size.height, expected.size.height),
        ]
        .iter()
        .all(|(actual, expected)| (actual - expected).abs() <= 1.0)
    }

    struct Probe {
        window: Option<Window>,
        original: Option<NSRect>,
        resized: Option<NSRect>,
        step: u8,
        next: Instant,
        deadline: Instant,
    }

    impl ApplicationHandler for Probe {
        fn resumed(&mut self, event_loop: &ActiveEventLoop) {
            if self.window.is_some() {
                return;
            }
            let attributes = platform::macos::window_attributes(
                Window::default_attributes()
                    .with_title("Arcadium window controls test")
                    .with_inner_size(LogicalSize::new(900.0, 650.0))
                    .with_min_inner_size(LogicalSize::new(800.0, 600.0)),
            );
            let window = event_loop.create_window(attributes).unwrap();
            self.original = Some(native(&window).frame());
            platform::macos::initialize_window(&window, true);
            let mtm = MainThreadMarker::new().unwrap();
            let icon = NSApplication::sharedApplication(mtm)
                .applicationIconImage()
                .unwrap();
            verify_icon(&icon);
            self.window = Some(window);
            self.next = Instant::now() + Duration::from_millis(100);
            self.deadline = Instant::now() + Duration::from_secs(20);
        }

        fn window_event(&mut self, _: &ActiveEventLoop, _: WindowId, _: WindowEvent) {}

        fn about_to_wait(&mut self, event_loop: &ActiveEventLoop) {
            if Instant::now() < self.next || self.window.is_none() {
                event_loop.set_control_flow(ControlFlow::WaitUntil(self.next));
                return;
            }
            let window = self.window.as_ref().unwrap();
            let native = native(window);
            let fullscreen = window.simple_fullscreen();
            let ready = match self.step {
                0 | 2 | 5 | 9 => {
                    fullscreen && frames_match(native.frame(), native.screen().unwrap().frame())
                }
                1 | 3 => !fullscreen && frames_match(native.frame(), self.original.unwrap()),
                4 | 6 | 8 | 11 => {
                    !fullscreen && frames_match(native.frame(), self.resized.unwrap())
                }
                7 | 10 => !fullscreen && native.isMiniaturized(),
                _ => unreachable!(),
            };
            if !ready
                || (!matches!(self.step, 7 | 10)
                    && (native.isMiniaturized() || !native.isVisible()))
            {
                assert!(
                    Instant::now() < self.deadline,
                    "window transition timed out at step {}: fullscreen={fullscreen}, minimized={}, visible={}, frame={:?}",
                    self.step,
                    native.isMiniaturized(),
                    native.isVisible(),
                    native.frame()
                );
                self.next = Instant::now() + Duration::from_millis(100);
                event_loop.set_control_flow(ControlFlow::WaitUntil(self.next));
                return;
            }
            match self.step {
                0 | 2 | 5 => {
                    platform::toggle_window_maximized(window);
                }
                1 => {
                    platform::toggle_window_maximized(window);
                }
                3 => {
                    let original = self.original.unwrap();
                    let resized = NSRect::new(
                        original.origin,
                        NSSize::new(original.size.width - 50.0, original.size.height - 30.0),
                    );
                    self.resized = Some(resized);
                    native.setFrame_display(resized, true);
                }
                4 => {
                    platform::toggle_window_maximized(window);
                }
                6 => {
                    platform::minimize_window(window);
                }
                7 | 10 => {
                    window.set_minimized(false);
                }
                8 => {
                    platform::toggle_window_maximized(window);
                }
                9 => {
                    platform::minimize_window(window);
                }
                11 => {
                    println!(
                        "Verified Arcadium Dock icon, startup fullscreen, fullscreen/restore, resize/restore and minimize from both window states."
                    );
                    event_loop.exit();
                    return;
                }
                _ => unreachable!(),
            }
            self.step += 1;
            self.next = Instant::now() + Duration::from_millis(100);
            self.deadline = Instant::now() + Duration::from_secs(20);
            event_loop.set_control_flow(ControlFlow::WaitUntil(self.next));
        }
    }

    pub fn run() {
        EventLoop::new()
            .unwrap()
            .run_app(&mut Probe {
                window: None,
                original: None,
                resized: None,
                step: 0,
                next: Instant::now(),
                deadline: Instant::now() + Duration::from_secs(20),
            })
            .unwrap();
    }
}

fn main() {
    #[cfg(target_os = "macos")]
    test::run();

    #[cfg(not(target_os = "macos"))]
    panic!("Run this native window test on macOS.");
}
