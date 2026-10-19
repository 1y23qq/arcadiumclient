// SPDX-License-Identifier: GPL-3.0-only

use std::{env, path::PathBuf, process::Command};

fn main() {
    println!("cargo:rerun-if-changed=assets/java/JavaInfo.java");
    println!("cargo:rerun-if-env-changed=JAVA_HOME");
    let compiler = env::var_os("JAVA_HOME")
        .map(|directory| {
            PathBuf::from(directory).join("bin").join(if cfg!(windows) {
                "javac.exe"
            } else {
                "javac"
            })
        })
        .unwrap_or_else(|| PathBuf::from("javac"));
    let output = Command::new(compiler)
        .args(["--release", "8", "-encoding", "UTF-8", "-d"])
        .arg(env::var_os("OUT_DIR").expect("Cargo did not provide OUT_DIR"))
        .arg("assets/java/JavaInfo.java")
        .output()
        .expect("Install JDK 25 and set JAVA_HOME to build the Java runtime probe");
    assert!(
        output.status.success(),
        "Java runtime probe compilation failed: {}",
        String::from_utf8_lossy(&output.stderr)
    );
}
