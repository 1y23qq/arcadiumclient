// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: MIT

pub const BUNDLE_POLICY: &[u8] = include_bytes!("../../../distribution/bundle-policy.json");

pub const MINECRAFT_VERSION: &str = "1.8.9";
pub const MINECRAFT_VERSIONS: &[&str] = &["1.8.9", "1.7.10"];
pub const CLIENT_COMPONENTS: &[u8] =
    include_bytes!("../assets/distribution/client-components.json");
pub const VERSIONS: &[u8] = include_bytes!("../assets/distribution/versions.json");
pub const ARTS: &[u8] = include_bytes!("../assets/distribution/arts.json");
pub const BUNDLES: &[u8] = include_bytes!("../assets/distribution/bundles.json");
pub const TERMS: &[u8] = include_bytes!("../assets/distribution/terms.json");
pub const CHANGELOG: &[u8] = include_bytes!("../assets/distribution/changelog.json");
pub const FORGE_1710_PROFILE: &[u8] =
    include_bytes!("../assets/distribution/forge-1.7.10.profile.json");
pub const FORGE_1710_DEPENDENCIES: &[u8] =
    include_bytes!("../../../distribution/forge-1.7.10.dependencies.json");
pub const FORGE_1710_VERSION: &str = "10.13.4.1614";
pub const FORGE_1710_RETIRED: &[u8] =
    include_bytes!("../../../distribution/forge-1.7.10.retired.json");

#[derive(Debug, serde::Deserialize)]
pub struct ClientComponent {
    pub file: String,
    pub sha1: String,
    pub sha256: String,
    pub bytes: u64,
    #[serde(default)]
    pub bootstrap: bool,
}

#[must_use]
pub fn supports_version(version: &str) -> bool {
    MINECRAFT_VERSIONS.contains(&version)
}

#[must_use]
pub fn requires_separate_game_dir(version: &str) -> bool {
    version == "1.7.10"
}

#[must_use]
pub fn supports_forge_compat(version: &str) -> bool {
    supports_version(version)
}

#[must_use]
pub fn uses_native_forge(version: &str) -> bool {
    version == "1.7.10"
}

#[must_use]
pub fn default_ornithe_loader(_version: &str) -> Option<&'static str> {
    None
}

pub fn components(version: &str) -> Option<&'static [ClientComponent]> {
    static CATALOG: std::sync::OnceLock<std::collections::HashMap<String, Vec<ClientComponent>>> =
        std::sync::OnceLock::new();
    let catalog = CATALOG.get_or_init(|| {
        serde_json::from_slice(CLIENT_COMPONENTS).expect("bundled component catalog must be valid")
    });
    let components = catalog.get(version)?.as_slice();
    (!components.is_empty()).then_some(components)
}

pub fn bundle(path: &str) -> Option<&'static [u8]> {
    match path.rsplit('/').next()? {
        "core-1.7.10-forge.mrpack" => Some(include_bytes!(
            "../assets/distribution/core-1.7.10-forge.mrpack"
        )),
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
pub const BRANDING_SHA1: &str = "4a8113bc58eb3c9630425ea019eb097b61344855";

pub const COMPONENTS: &[(&str, &str)] = &[
    ("ArcadiumCore-1.8.9.jar", "5d80fd0b4321284872e945d3b8bfd96bba56c890"),
    ("ArcadiumFeatures-1.8.9.jar", "b4711fdc4d9e296d1694d3a246a728cab8d4e239"),
    ("ArcadiumRenderer-1.8.9.jar", "a93ef441f12e02add2c756c3b1dedad0454bc616"),
    ("ArcadiumForgeCompat-1.8.9.jar", "1038a9b8276b204ca945e2ab22c2e9cd5a514797"),
];

#[cfg(test)]
mod tests {
    #[test]
    fn ships_version_specific_loader_bundles() {
        let value: serde_json::Value = serde_json::from_slice(super::BUNDLES).unwrap();
        let versions = value["versions"].as_object().unwrap();
        assert_eq!(versions.len(), super::MINECRAFT_VERSIONS.len());
        assert_eq!(versions["1.8.9"]["ornithe"].as_array().unwrap().len(), 4);
        for &version in super::MINECRAFT_VERSIONS {
            let loader = if super::uses_native_forge(version) {
                "forge"
            } else {
                "ornithe"
            };
            for bundle in versions[version][loader].as_array().unwrap() {
                let path = bundle["path"].as_str().unwrap();
                assert!(path.contains(version));
                assert!(super::bundle(path).is_some());
            }
        }
    }

    #[test]
    fn legacy_worlds_and_bootstraps_are_separate() {
        assert!(super::supports_version("1.8.9"));
        assert!(super::supports_version("1.7.10"));
        assert!(super::requires_separate_game_dir("1.7.10"));
        assert!(!super::requires_separate_game_dir("1.8.9"));
        assert!(super::supports_forge_compat("1.7.10"));
        assert!(super::supports_forge_compat("1.8.9"));
        assert_eq!(super::default_ornithe_loader("1.7.10"), None);
        assert!(super::uses_native_forge("1.7.10"));
        assert!(!super::uses_native_forge("1.8.9"));
        for unsupported in ["1.7.9", "1.8.8", "1.12.2", "1.7.10-forge", ""] {
            assert!(!super::supports_version(unsupported));
            assert!(super::components(unsupported).is_none());
        }
    }
    #[test]
    fn game_branding_has_zip_signature() {
        assert_eq!(&super::BRANDING_MOD[..4], b"PK\x03\x04");
    }
}
