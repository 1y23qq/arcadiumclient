use arcadium_common::GameLoader;
use interfrost::api::{minecraft::VersionInfo, modded::LoaderVersion};
use serde_json::{Value, json};

use crate::{McError, McResult};

pub(crate) fn applies(version: &str, loader: GameLoader) -> bool {
    arcadium_common::distribution::uses_native_forge(version) && loader == GameLoader::Forge
}

pub(crate) fn loader(requested: Option<&str>) -> McResult<LoaderVersion> {
    let pinned = arcadium_common::distribution::FORGE_1710_VERSION;
    if let Some(requested) = requested
        && requested != pinned
    {
        return Err(McError::RequestedLoaderVersionNotFound {
            requested: requested.to_owned(),
        });
    }
    let dependencies: Value =
        serde_json::from_slice(arcadium_common::distribution::FORGE_1710_DEPENDENCIES)?;
    Ok(LoaderVersion {
        id: pinned.to_owned(),
        url: dependencies["profile"]["url"]
            .as_str()
            .expect("pinned Forge profile URL")
            .to_owned(),
        stable: true,
    })
}

pub(crate) fn profile(id: &str) -> McResult<VersionInfo> {
    let mut profile: Value =
        serde_json::from_slice(arcadium_common::distribution::FORGE_1710_PROFILE)?;
    let dependencies: Value =
        serde_json::from_slice(arcadium_common::distribution::FORGE_1710_DEPENDENCIES)?;
    profile["id"] = json!(id);
    profile["javaVersion"]["majorVersion"] = json!(25);
    let libraries = profile["libraries"]
        .as_array_mut()
        .expect("pinned Forge libraries");
    libraries.retain(|library| {
        !library["rules"].as_array().is_some_and(|rules| {
            rules.iter().any(|rule| {
                matches!(
                    rule["os"]["name"].as_str(),
                    Some("freebsd" | "linux-ppc64le")
                )
            })
        })
    });
    for library in libraries.iter_mut() {
        let artifact = library["name"]
            .as_str()
            .unwrap_or("")
            .split(':')
            .nth(1)
            .unwrap_or("");
        let Some((_, platform)) = artifact.split_once("-natives-") else {
            continue;
        };
        let (os, arch) = match platform {
            "windows" => ("windows", "x86_64"),
            "windows-x86" => ("windows", "x86"),
            "windows-arm64" => ("windows", "aarch64"),
            "macos" => ("osx", "x86_64"),
            "macos-arm64" => ("osx", "aarch64"),
            "linux" => ("linux", "x86_64"),
            "linux-arm64" => ("linux", "aarch64"),
            "linux-arm32" => ("linux", "arm"),
            _ => continue,
        };
        library["rules"] = json!([{ "action":"allow", "os":{ "name":os, "arch":arch } }]);
    }
    let mut prefix: Vec<Value> = dependencies["libraries"].as_array().expect("pinned Forge prefix libraries").iter().map(|library| json!({
        "name": library["name"],
        "downloads": { "artifact": { "path": library["path"], "url": library["url"], "sha1": library["sha1"], "size": library["bytes"] } }
    })).collect();
    prefix.append(libraries);
    *libraries = prefix;
    for argument in profile["arguments"]["game"]
        .as_array_mut()
        .expect("pinned Forge game arguments")
    {
        if argument.as_str() == Some("cpw.mods.fml.common.launcher.FMLTweaker") {
            *argument = json!("io.github.arcadiumclient.forge1710.bootstrap.ArcadiumForgeTweaker");
        }
    }
    Ok(serde_json::from_value(profile)?)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn actual_forge_profile_is_pinned_and_retains_modern_platform_libraries() {
        assert!(applies("1.7.10", GameLoader::Forge));
        assert!(!applies("1.8.9", GameLoader::Ornithe));
        assert!(loader(Some("10.13.4.1558")).is_err());
        let loader = loader(None).unwrap();
        let profile = profile(&format!("1.7.10-{}", loader.id)).unwrap();
        assert_eq!(profile.java_version.unwrap().major_version, 25);
        assert_eq!(
            profile.main_class,
            "com.gtnewhorizons.retrofuturabootstrap.MainStartOnFirstThread"
        );
        assert!(profile.libraries.iter().any(|library| {
            library
                .name
                .contains("net.minecraftforge:forge:1.7.10-10.13.4.1614")
        }));
        assert_eq!(profile.libraries[0].name, "org.ow2.asm:asm:9.10.1");
        for platform in ["natives-windows", "natives-macos-arm64", "natives-macos"] {
            assert!(
                profile
                    .libraries
                    .iter()
                    .any(|library| library.name.contains(platform)),
                "Missing {platform}"
            );
        }
        assert!(
            !profile
                .libraries
                .iter()
                .any(|library| library.name.contains("fabric-loader"))
        );
    }
}
