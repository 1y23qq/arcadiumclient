use std::collections::HashSet;
use std::path::{Path, PathBuf};
use std::time::Instant;

use arcadium_content::ContentResult;
use arcadium_content::packages::optimization_mods;

async fn original_validation(roots: &[PathBuf]) -> ContentResult<()> {
    let mut directories = HashSet::new();
    let mut files = HashSet::new();
    for root in roots {
        for directory in [root.clone(), root.join("1.8.9"), root.join("1.7.10")] {
            let mut entries = match tokio::fs::read_dir(&directory).await {
                Ok(entries) => entries,
                Err(error) if error.kind() == std::io::ErrorKind::NotFound => continue,
                Err(error) => return Err(error.into()),
            };
            if !directories.insert(tokio::fs::canonicalize(&directory).await?) {
                continue;
            }
            while let Some(entry) = entries.next_entry().await? {
                let path = entry.path();
                if !path
                    .extension()
                    .is_some_and(|extension| extension.eq_ignore_ascii_case("jar"))
                    || !tokio::fs::metadata(&path).await?.is_file()
                {
                    continue;
                }
                files.insert(tokio::fs::canonicalize(path).await?);
                if files.len() > 4096 {
                    return Err(std::io::Error::other("Too many benchmark archives").into());
                }
            }
        }
    }
    let mut files: Vec<_> = files.into_iter().collect();
    files.sort();
    for file in files {
        optimization_mods::ensure_mod_allowed(&file).await?;
    }
    Ok(())
}

fn median(values: &[f64]) -> f64 {
    let mut sorted = values.to_vec();
    sorted.sort_by(f64::total_cmp);
    sorted[sorted.len() / 2]
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let mut arguments = std::env::args_os().skip(1);
    let root = arguments.next().map(PathBuf::from).ok_or("Usage: benchmark_mod_validation <mods-directory> [iterations]")?;
    let iterations = arguments.next().map(|value| value.to_string_lossy().parse::<usize>()).transpose()?.unwrap_or(20);
    if iterations == 0 || !Path::new(&root).is_dir() {
        return Err("Use an existing mod directory and a positive iteration count".into());
    }
    let roots = [root];
    let mut before = Vec::new();
    let mut after = Vec::new();
    for index in 0..iterations {
        for use_original in if index % 2 == 0 { [true, false] } else { [false, true] } {
            let started = Instant::now();
            if use_original {
                original_validation(&roots).await?;
                before.push(started.elapsed().as_secs_f64() * 1000.0);
            } else {
                optimization_mods::check_mod_directories(&roots).await?;
                after.push(started.elapsed().as_secs_f64() * 1000.0);
            }
        }
    }
    println!("{}", serde_json::to_string_pretty(&serde_json::json!({
        "root": roots[0], "iterations": iterations,
        "originalMillis": before, "batchedMillis": after,
        "originalMedianMillis": median(&before), "batchedMedianMillis": median(&after),
        "fullArchiveValidation": true, "managedHashesChecked": true
    }))?);
    Ok(())
}
