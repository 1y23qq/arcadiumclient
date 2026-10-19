use arcadium_app::application::ArcadiumApp;
use freya::prelude::*;
use freya_testing::TestingRunner;
use std::borrow::Cow;
use std::cell::Cell;
use std::collections::HashMap;

fn main() {
    let rt = tokio::runtime::Runtime::new().unwrap();
    let _runtime = rt.enter();
    let home = std::env::args().any(|arg| arg == "--home");
    arcadium_common::paths::set_launcher_dir(
        std::env::current_dir()
            .unwrap()
            .join("build/visual-check/data"),
    );
    let app = ArcadiumApp {
        needs_location: !home,
        start_maximized: false,
        boot_launch: Cell::new(None),
    };
    let (mut testing, _) = TestingRunner::new(
        move || app.render().into_element(),
        (1200., 800.).into(),
        |_| {},
        1.0,
    );
    testing.set_fonts(HashMap::from([(
        "Inter",
        include_bytes!("../assets/fonts/Inter-Regular.ttf").as_slice(),
    )]));
    testing.set_default_fonts(&[Cow::Borrowed("Inter")]);
    testing.poll_n(
        std::time::Duration::from_millis(15),
        if home { 600 } else { 120 },
    );
    std::fs::create_dir_all("build/visual-check").unwrap();
    if home {
        testing.render_to_file("build/visual-check/home.png");
        println!("Arcadium launcher home render check completed.");
        return;
    }
    testing.render_to_file("build/visual-check/launcher.png");
    testing.click_cursor((1100., 760.));
    testing.poll_n(std::time::Duration::from_millis(15), 100);
    testing.render_to_file("build/visual-check/location.png");
    println!("Arcadium launcher render and navigation check completed.");
}
