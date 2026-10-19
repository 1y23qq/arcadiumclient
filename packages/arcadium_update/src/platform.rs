#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum UpdatePlatform {
    WindowsX64,
    MacArm64,
    MacX64,
}

impl UpdatePlatform {
    pub fn current() -> Option<Self> {
        match (std::env::consts::OS, std::env::consts::ARCH) {
            ("windows", "x86_64") => Some(Self::WindowsX64),
            ("macos", "aarch64") => Some(Self::MacArm64),
            ("macos", "x86_64") => Some(Self::MacX64),
            _ => None,
        }
    }

    pub fn id(self) -> &'static str {
        match self {
            Self::WindowsX64 => "windows-x64",
            Self::MacArm64 => "macos-arm64",
            Self::MacX64 => "macos-x64",
        }
    }

    pub fn installer_name(self, version: &str) -> String {
        match self {
            Self::WindowsX64 => format!("ArcadiumClientSetup-{version}-x64.exe"),
            _ => format!("ArcadiumClient-{version}-{}.app.tar.gz", self.id()),
        }
    }

    pub fn manifest_name(self) -> &'static str {
        match self {
            Self::WindowsX64 => "package-manifest.json",
            Self::MacArm64 => "package-manifest-macos-arm64.json",
            Self::MacX64 => "package-manifest-macos-x64.json",
        }
    }

    pub fn magic(self) -> [u8; 2] {
        match self {
            Self::WindowsX64 => *b"MZ",
            _ => [0x1f, 0x8b],
        }
    }
}
