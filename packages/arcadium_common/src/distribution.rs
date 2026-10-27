// Copyright (c) 2026 0aurorq3.
// SPDX-License-Identifier: MIT

pub const BUNDLE_POLICY: &[u8] = include_bytes!("../../../distribution/bundle-policy.json");

pub const MINECRAFT_VERSION: &str = "1.8.9";
pub const MINECRAFT_VERSIONS: &[&str] = &["1.8.9", "1.7.10", "1.21.11", "26.2"];
pub const MODERN_12111_LOCK: &[u8] = include_bytes!("../../../distribution/oneclient-1.21.11.lock.json");
pub const MODERN_262_LOCK: &[u8] = include_bytes!("../../../distribution/oneclient-26.2.lock.json");
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
pub fn default_loader(version: &str) -> Option<crate::GameLoader> {
    match version {
        "1.7.10" => Some(crate::GameLoader::Forge),
        "1.8.9" => Some(crate::GameLoader::Ornithe),
        "1.21.11" | "26.2" => Some(crate::GameLoader::Fabric),
        _ => None,
    }
}

#[must_use]
pub fn requires_separate_game_dir(version: &str) -> bool {
    matches!(version, "1.7.10" | "1.21.11" | "26.2")
}

#[must_use]
pub fn supports_forge_compat(version: &str) -> bool {
    matches!(version, "1.8.9" | "1.7.10")
}

#[must_use]
pub fn uses_modern_fabric(version: &str) -> bool {
    matches!(version, "1.21.11" | "26.2")
}

#[must_use]
pub fn uses_native_forge(version: &str) -> bool {
    version == "1.7.10"
}

#[must_use]
pub fn default_ornithe_loader(_version: &str) -> Option<&'static str> {
    None
}

#[must_use]
pub fn default_fabric_loader(version: &str) -> Option<&'static str> {
    (version == "26.2").then_some("0.20.1")
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
        "hud-1.21.11-fabric.mrpack" => Some(include_bytes!(
            "../assets/distribution/hud-1.21.11-fabric.mrpack"
        )),
        "performance-1.21.11-fabric.mrpack" => Some(include_bytes!(
            "../assets/distribution/performance-1.21.11-fabric.mrpack"
        )),
        "qol-1.21.11-fabric.mrpack" => Some(include_bytes!(
            "../assets/distribution/qol-1.21.11-fabric.mrpack"
        )),
        "utility-1.21.11-fabric.mrpack" => Some(include_bytes!(
            "../assets/distribution/utility-1.21.11-fabric.mrpack"
        )),
        "hud-26.2-fabric.mrpack" => Some(include_bytes!(
            "../assets/distribution/hud-26.2-fabric.mrpack"
        )),
        "performance-26.2-fabric.mrpack" => Some(include_bytes!(
            "../assets/distribution/performance-26.2-fabric.mrpack"
        )),
        "qol-26.2-fabric.mrpack" => Some(include_bytes!(
            "../assets/distribution/qol-26.2-fabric.mrpack"
        )),
        "skyblock-26.2-fabric.mrpack" => Some(include_bytes!(
            "../assets/distribution/skyblock-26.2-fabric.mrpack"
        )),
        "utility-26.2-fabric.mrpack" => Some(include_bytes!(
            "../assets/distribution/utility-26.2-fabric.mrpack"
        )),
        _ => None,
    }
}

pub const BRANDING_MOD: &[u8] = include_bytes!("../assets/distribution/ArcadiumBrand-1.8.9.jar");
pub const BRANDING_SHA1: &str = "7071e2f11eaf00e248e9e2ea0ea9b93d8d83399c";

pub const COMPONENTS: &[(&str, &str)] = &[
    ("ArcadiumCore-1.8.9.jar", "501e9ade6bee278335da088c1cde74d2568db064"),
    ("ArcadiumFeatures-1.8.9.jar", "e94762ea3438fd51d76a029f3447069861bc9050"),
    ("ArcadiumRenderer-1.8.9.jar", "a93ef441f12e02add2c756c3b1dedad0454bc616"),
    ("ArcadiumForgeCompat-1.8.9.jar", "c303376f010a84253df466fa78fed4e53c1233fe"),
    ("ArcadiumPylon-1.8.9.jar", "94a1ac70643e13cba024b13bd5236247291142a9"),
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
            } else if super::uses_modern_fabric(version) {
                "fabric"
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
        assert!(super::supports_version("1.21.11"));
        assert!(super::supports_version("26.2"));
        assert!(super::requires_separate_game_dir("26.2"));
        assert!(super::uses_modern_fabric("26.2"));
        assert!(!super::supports_forge_compat("26.2"));
        assert_eq!(
            super::default_loader("26.2"),
            Some(crate::GameLoader::Fabric)
        );
        assert!(super::requires_separate_game_dir("1.7.10"));
        assert!(!super::requires_separate_game_dir("1.8.9"));
        assert!(super::requires_separate_game_dir("1.21.11"));
        assert!(super::supports_forge_compat("1.7.10"));
        assert!(super::supports_forge_compat("1.8.9"));
        assert!(!super::supports_forge_compat("1.21.11"));
        assert!(super::uses_modern_fabric("1.21.11"));
        assert!(!super::uses_modern_fabric("1.8.9"));
        assert_eq!(
            super::default_loader("1.21.11"),
            Some(crate::GameLoader::Fabric)
        );
        assert_eq!(
            super::default_loader("1.8.9"),
            Some(crate::GameLoader::Ornithe)
        );
        assert_eq!(
            super::default_loader("1.7.10"),
            Some(crate::GameLoader::Forge)
        );
        assert_eq!(super::default_loader("1.21.10"), None);
        assert_eq!(super::default_ornithe_loader("1.7.10"), None);
        assert_eq!(super::default_fabric_loader("26.2"), Some("0.20.1"));
        assert_eq!(super::default_fabric_loader("1.21.11"), None);
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
