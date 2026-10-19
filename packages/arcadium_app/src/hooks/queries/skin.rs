use bytes::Bytes;
use freya::prelude::*;

pub fn use_player_skin(uuid: String) -> (Bytes, bool) {
    let profile = super::use_player_profile(uuid.clone(), None::<String>);

    let (skin_url, is_slim) = crate::hooks::settled_or_loading(&profile)
        .map_or((None, false), |profile| (profile.skin_url, profile.is_slim));

    let skin_query = super::use_cached_image(skin_url.clone(), 256);

    match crate::hooks::loaded_image(skin_url.as_deref(), &skin_query) {
        Some((_, bytes)) => (bytes, is_slim),
        None => (Bytes::new(), false),
    }
}
