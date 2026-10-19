pub const DUMMY_REPLACE_NEWLINE: &str = "\n";

pub const MICROSOFT_CLIENT_ID: &str = match option_env!("ARCADIUM_MICROSOFT_CLIENT_ID") {
    Some(value) => value,
    None => "8112e52c-0e69-4db2-8988-a49b528d4e4b",
};
pub const MINECRAFT_SCOPES: &str = "XboxLive.signin offline_access";
pub const CURSEFORGE_API_KEY: &str = match option_env!("ARCADIUM_CURSEFORGE_API_KEY") {
    Some(value) => value,
    None => "",
};
pub const DISCORD_CLIENT_ID: &str = match option_env!("ARCADIUM_DISCORD_CLIENT_ID") {
    Some(value) => value,
    None => "1558477538937016564",
};

pub const MODRINTH_API_URL: &str = "https://api.modrinth.com";
pub const MODRINTH_CDN_PREFIX: &str = "https://cdn.modrinth.com/data/";
pub const CURSEFORGE_API_URL: &str = "https://api.curseforge.com/v1";
pub const CURSEFORGE_GAME_ID: u32 = 432;
pub const METADATA_API_URL: &str = "https://meta.polyfrost.org";
pub const MCLOGS_API_URL: &str = "https://api.mclo.gs/1";
pub const SKYCLIENT_BASE_URL: &str =
    "https://raw.githubusercontent.com/SkyblockClient/SkyblockClient-REPO/refs/heads/main/v1";
pub const META_URL_BASE: &str = "https://data-v2.polyfrost.org";
pub const TOS_URL: &str = "https://arcadiumclient.com/terms/";
pub const PRIVACY_URL: &str = "https://arcadiumclient.com/privacy/";

pub const SENTRY_DSN: &str = match option_env!("ARCADIUM_SENTRY_DSN") {
    Some(value) => value,
    None => "",
};

pub const TARGET_OS: &str = cfg_select! {
    target_os = "windows" => "windows",
    target_os = "macos" => "osx",
    target_os = "linux" => "linux"
};

pub const NATIVE_ARCH: &str = cfg_select! {
    target_arch = "x86" => "32",
    target_arch = "x86_64" => "64",
    _ => "64"
};

pub const JAVA_BIN: &str = if cfg!(windows) { "javaw.exe" } else { "java" };

pub const ARCH_WIDTH: &str = if cfg!(target_pointer_width = "64") {
    "64"
} else {
    "32"
};

pub const LINE_ENDING: &str = if cfg!(windows) { "\r\n" } else { "\n" };
pub const CLASSPATH_SEPARATOR: &str = if cfg!(windows) { ";" } else { ":" };
