use crate::LauncherResult;
use arcadium_net::RequestClient;

#[derive(Debug, Clone, PartialEq, Eq, serde::Deserialize)]
pub struct ChangelogEntry {
    pub version: String,
    pub body: String,
}

#[tracing::instrument(level = "debug", skip(_net))]
pub async fn fetch_changelog(_net: &RequestClient) -> LauncherResult<Vec<ChangelogEntry>> {
    Ok(serde_json::from_slice(
        arcadium_common::distribution::CHANGELOG,
    )?)
}
