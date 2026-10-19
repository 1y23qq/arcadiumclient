pub const ICON_URL: &str = "arcadium:icon";

pub fn display_file_name(raw: &str) -> String {
    let stem = raw
        .rsplit_once('.')
        .filter(|(head, ext)| !head.is_empty() && ext.len() <= 8)
        .map_or(raw, |(head, _)| head);
    let bytes = stem.as_bytes();
    let cut = bytes.iter().enumerate().position(|(index, byte)| {
        matches!(byte, b'-' | b'_' | b'+') && bytes.get(index + 1).is_some_and(u8::is_ascii_digit)
    });
    display_name(match cut {
        Some(0) | None => stem,
        Some(cut) => &stem[..cut],
    })
}

pub fn display_name(name: &str) -> String {
    match name.to_ascii_lowercase().replace([' ', '-'], "").as_str() {
        "oneconfig" => "Arcadium Core",
        "polyplus" | "poly+" | "polyplus+" => "Arcadium Features",
        "oneclient" => "Arcadium Client",
        "polyhitbox" => "Hitboxes",
        "polysprint" => "Toggle Sprint",
        "polynametag" => "Nametags",
        "polytime" => "Time",
        "polyweather" => "Weather",
        "polyzoom" => "Zoom",
        "polyblur" => "Motion Blur",
        "customblockhighlight" | "blockhighlight" => "Block Overlay",
        _ => return rebrand_text(name),
    }
    .to_string()
}

pub fn rebrand_text(text: &str) -> String {
    text.replace("OneClient", "Arcadium Client")
        .replace("OneConfig", "Arcadium Core")
        .replace("PolyPlus", "Arcadium Features")
        .replace("PolyGlass", "Glass")
        .replace("PolyHitbox", "Hitboxes")
        .replace("PolySprint", "Toggle Sprint")
        .replace("PolyNametag", "Nametags")
        .replace("PolyTime", "Time")
        .replace("PolyWeather", "Weather")
        .replace("PolyZoom", "Zoom")
        .replace("PolyBlur", "Motion Blur")
        .replace("Block Highlight", "Block Overlay")
}

pub fn icon(name: &str, original: Option<String>) -> Option<String> {
    if matches!(
        name.to_ascii_lowercase().as_str(),
        "oneconfig" | "polyplus" | "polyplus+" | "poly+"
    ) {
        Some(ICON_URL.to_string())
    } else {
        original
    }
}

#[cfg(test)]
mod tests {
    #[test]
    fn external_metadata_keeps_generic_feature_labels() {
        for (input, expected) in [
            ("OneConfig", "Arcadium Core"),
            ("PolySprint", "Toggle Sprint"),
            ("PolyBlur", "Motion Blur"),
            ("Custom Block Highlight", "Block Overlay"),
        ] {
            assert_eq!(super::display_name(input), expected);
        }
        assert_eq!(super::display_name("Ordinary Mod"), "Ordinary Mod");
    }
}
