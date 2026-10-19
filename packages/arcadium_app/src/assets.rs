#[derive(rust_embed::RustEmbed)]
#[folder = "./assets/"]
#[exclude = "steve.png"]
#[exclude = "alex.png"]
pub struct AppAssets;

impl AppAssets {
    pub fn get_bytes(path: &str) -> Option<bytes::Bytes> {
        AppAssets::get(path).map(|file| match file.data {
            std::borrow::Cow::Borrowed(slice) => bytes::Bytes::copy_from_slice(slice),
            std::borrow::Cow::Owned(vec) => {
                tracing::debug!("Cloning asset data for '{}'", path);
                bytes::Bytes::from(vec)
            }
        })
    }
}

#[cfg(test)]
mod tests {
    use super::AppAssets;

    #[test]
    fn excludes_minecraft_default_skin_textures() {
        assert!(AppAssets::get("steve.png").is_none());
        assert!(AppAssets::get("alex.png").is_none());
        assert!(!AppAssets::iter().any(|path| matches!(path.as_ref(), "steve.png" | "alex.png")));
    }
}
