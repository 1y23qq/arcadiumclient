// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: MIT

pub const BUNDLE_POLICY: &[u8] = include_bytes!("../../../distribution/bundle-policy.json");

pub const MINECRAFT_VERSION: &str = "1.8.9";
pub const VERSIONS: &[u8] = include_bytes!("../assets/distribution/versions.json");
pub const ARTS: &[u8] = include_bytes!("../assets/distribution/arts.json");
pub const BUNDLES: &[u8] = include_bytes!("../assets/distribution/bundles.json");
pub const TERMS: &[u8] = include_bytes!("../assets/distribution/terms.json");
pub const CHANGELOG: &[u8] = include_bytes!("../assets/distribution/changelog.json");

pub fn bundle(path: &str) -> Option<&'static [u8]> {
    match path.rsplit('/').next()? {
        "hud-1.8.9-ornithe.mrpack" => Some(include_bytes!(
            "../assets/distribution/hud-1.8.9-ornithe.mrpack"
        )),
        "performance-1.8.9-ornithe.mrpack" => Some(include_bytes!(
            "../assets/distribution/performance-1.8.9-ornithe.mrpack"
        )),
        "qol-1.8.9-ornithe.mrpack" => Some(include_bytes!(
            "../assets/distribution/qol-1.8.9-ornithe.mrpack"
        )),
        "utility-1.8.9-ornithe.mrpack" => Some(include_bytes!(
            "../assets/distribution/utility-1.8.9-ornithe.mrpack"
        )),
        _ => None,
    }
}

pub const BRANDING_MOD: &[u8] = include_bytes!("../assets/distribution/ArcadiumBrand-1.8.9.jar");
pub const BRANDING_SHA1: &str = "e30be1301dc6ee0ba66d1ff68b77b03c29bd32f5";

pub const COMPONENTS: &[(&str, &str)] = &[
    ("ArcadiumCore-1.8.9.jar", "f4917cabdcc50a3dd599518e280b430bf632f8f9"),
    ("ArcadiumFeatures-1.8.9.jar", "733eeacab85c8715e243ba4d6357843d0f7fbc4d"),
    ("ArcadiumRenderer-1.8.9.jar", "a93ef441f12e02add2c756c3b1dedad0454bc616"),
    ("ArcadiumForgeCompat-1.8.9.jar", "0aa3eaeb1dc74e9a33a8cc48d3228d2a55e78b8d"),
];

#[cfg(test)]
mod tests {
    #[test]
    fn ships_only_1_8_9_ornithe() {
        let value: serde_json::Value = serde_json::from_slice(super::BUNDLES).unwrap();
        let versions = value["versions"].as_object().unwrap();
        assert_eq!(versions.len(), 1);
        assert!(versions.contains_key("1.8.9"));
        let bundles = versions["1.8.9"]["ornithe"].as_array().unwrap();
        assert_eq!(bundles.len(), 4);
        for bundle in bundles {
            assert!(super::bundle(bundle["path"].as_str().unwrap()).is_some());
        }
    }
    #[test]
    fn game_branding_has_zip_signature() {
        assert_eq!(&super::BRANDING_MOD[..4], b"PK\x03\x04");
    }
}
