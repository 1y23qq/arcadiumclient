use crate::bundles::BundleError;
use crate::packages::PackageError;

pub type ContentResult<T> = Result<T, ContentError>;

#[derive(Debug, thiserror::Error)]
pub enum ContentError {
    #[error(transparent)]
    Package(#[from] PackageError),

    #[error(transparent)]
    Bundle(#[from] BundleError),

    #[error(transparent)]
    Request(#[from] arcadium_net::RequestError),

    #[error(transparent)]
    Db(#[from] arcadium_db::DbError),

    #[error("Database execution failed: {0}")]
    Sql(#[from] sqlx::Error),

    #[error(transparent)]
    Io(#[from] arcadium_io::IOError),

    #[error(transparent)]
    StdIo(#[from] std::io::Error),

    #[error(transparent)]
    Paths(#[from] arcadium_common::PathsError),

    #[error(transparent)]
    Events(#[from] arcadium_events::EventError),

    #[error("Unable to parse JSON: {0}")]
    Json(#[from] serde_json::Error),

    #[error("Invalid URL: {0}")]
    Url(#[from] url::ParseError),

    #[error("Arcadium could not retire an old bundled mod: {reason}")]
    RetiredBundleConflict { reason: String },

    #[error("invalid content data: {reason}")]
    InvalidData { reason: String },
}

impl ContentError {
    #[must_use]
    pub fn is_transient(&self) -> bool {
        match self {
            Self::Request(source) => source.is_transient(),
            Self::Io(arcadium_io::IOError::IOError(source))
            | Self::Io(arcadium_io::IOError::PathIOError { source, .. })
            | Self::StdIo(source) => {
                use std::io::ErrorKind;
                matches!(
                    source.kind(),
                    ErrorKind::ConnectionRefused
                        | ErrorKind::ConnectionReset
                        | ErrorKind::ConnectionAborted
                        | ErrorKind::NotConnected
                        | ErrorKind::TimedOut
                )
            }
            _ => false,
        }
    }
}
