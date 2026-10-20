use std::path::PathBuf;

#[tokio::main(flavor = "multi_thread", worker_threads = 2)]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let arguments: Vec<_> = std::env::args_os().skip(1).map(PathBuf::from).collect();
    if arguments.len() != 2 || arguments.iter().any(|path| !path.is_absolute()) {
        return Err("Usage: verify_shared_settings <absolute 1.7.10 game directory> <absolute 1.8.9 game directory>".into());
    }
    let backups = arcadium_core::game::share_client_settings(&arguments[0], &arguments[1]).await?;
    println!(
        "{}",
        serde_json::to_string_pretty(&serde_json::json!({
            "passed": true,
            "nativeGameDirectory": arguments[0],
            "primaryGameDirectory": arguments[1],
            "preservedSettings": backups,
        }))?
    );
    Ok(())
}
