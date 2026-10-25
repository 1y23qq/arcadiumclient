use super::Gpu;

pub async fn inventory() -> Result<Vec<Gpu>, String> {
    #[cfg(windows)]
    {
        use std::process::Stdio;
        use std::time::Duration;
        use tokio::process::Command;

        let mut command = Command::new("powershell.exe");
        command
            .args([
                "-NoLogo", "-NoProfile", "-NonInteractive", "-Command",
                "[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false); @(Get-CimInstance Win32_VideoController -ErrorAction Stop | Select-Object Name,AdapterCompatibility,PNPDeviceID) | ConvertTo-Json -Compress",
            ])
            .creation_flags(0x08000000)
            .stdin(Stdio::null())
            .stderr(Stdio::null())
            .kill_on_drop(true);
        let output = tokio::time::timeout(Duration::from_secs(8), command.output())
            .await
            .map_err(|_| "GPU inventory timed out".to_owned())?
            .map_err(|error| error.to_string())?;
        if !output.status.success() {
            return Err("Windows GPU inventory was unavailable".to_owned());
        }
        parse_windows(&output.stdout)
    }
    #[cfg(target_os = "linux")]
    {
        tokio::task::spawn_blocking(super::detect)
            .await
            .map_err(|error| error.to_string())
    }
    #[cfg(not(any(windows, target_os = "linux")))]
    {
        Ok(Vec::new())
    }
}

#[cfg(any(windows, test))]
fn parse_windows(raw: &[u8]) -> Result<Vec<Gpu>, String> {
    #[derive(serde::Deserialize)]
    #[serde(rename_all = "PascalCase")]
    struct Adapter {
        name: Option<String>,
        adapter_compatibility: Option<String>,
        #[serde(rename = "PNPDeviceID")]
        id: Option<String>,
    }
    let raw = raw.strip_prefix(&[0xef, 0xbb, 0xbf]).unwrap_or(raw);
    let value: serde_json::Value =
        serde_json::from_slice(raw).map_err(|error| error.to_string())?;
    let values = match value {
        serde_json::Value::Array(values) => values,
        serde_json::Value::Object(_) => vec![value],
        serde_json::Value::Null => Vec::new(),
        _ => return Err("Invalid Windows GPU inventory".into()),
    };
    values
        .into_iter()
        .map(|value| {
            let adapter: Adapter =
                serde_json::from_value(value).map_err(|error| error.to_string())?;
            let mut name = adapter.name.unwrap_or_default();
            if adapter
                .adapter_compatibility
                .as_deref()
                .is_some_and(|vendor| vendor.to_ascii_lowercase().contains("nvidia"))
                && !name.to_ascii_lowercase().contains("nvidia")
            {
                name = format!("NVIDIA {name}");
            }
            Ok(Gpu {
                id: adapter.id.unwrap_or_default(),
                name,
            })
        })
        .collect()
}

pub fn supports_nvidium(gpu: &Gpu) -> bool {
    let name = gpu.name.to_ascii_uppercase();
    let words: Vec<_> = name
        .split(|ch: char| !ch.is_ascii_alphanumeric())
        .filter(|word| !word.is_empty())
        .collect();
    if !words.contains(&"NVIDIA") {
        return false;
    }
    if words.iter().any(|word| {
        *word == "RTX"
            || word
                .strip_prefix("RTX")
                .is_some_and(|tail| tail.starts_with(|ch: char| ch.is_ascii_digit()))
    }) {
        return true;
    }
    words
        .windows(2)
        .any(|pair| pair[0] == "GTX" && matches!(pair[1], "1630" | "1650" | "1660"))
        || words
            .iter()
            .any(|word| matches!(*word, "GTX1630" | "GTX1650" | "GTX1660"))
        || words.iter().any(|word| {
            matches!(
                *word,
                "T400"
                    | "T4"
                    | "T500"
                    | "T550"
                    | "T600"
                    | "T1000"
                    | "T1200"
                    | "T2000"
                    | "MX450"
                    | "MX550"
                    | "MX570"
                    | "A2"
                    | "A10"
                    | "A16"
                    | "A40"
                    | "L4"
                    | "L40"
                    | "L40S"
            )
        })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn scans_every_adapter_and_keeps_hybrid_nvidia_inventory() {
        let cards = parse_windows(br#"[{"Name":"Intel(R) Iris Xe Graphics","AdapterCompatibility":"Intel","PNPDeviceID":"PCI\\VEN_8086"},{"Name":"GeForce RTX 4060 Laptop GPU","AdapterCompatibility":"NVIDIA","PNPDeviceID":"PCI\\VEN_10DE"}]"#).unwrap();
        assert_eq!(cards.len(), 2);
        assert!(!supports_nvidium(&cards[0]));
        assert!(supports_nvidium(&cards[1]));
        assert_eq!(cards[1].id, "PCI\\VEN_10DE");
    }

    #[test]
    fn supports_turing_and_later_graphics_without_accepting_older_nvidia() {
        for name in [
            "NVIDIA GeForce GTX 1650 SUPER",
            "NVIDIA RTX 5070 Ti",
            "NVIDIA Quadro RTX 4000",
            "NVIDIA RTX A2000",
            "NVIDIA RTX PRO 6000",
            "NVIDIA T1000",
            "NVIDIA GeForce MX450",
            "NVIDIA GeForce GTX1650",
            "NVIDIA Tesla T4",
            "NVIDIA A40",
            "NVIDIA L40S",
        ] {
            assert!(
                supports_nvidium(&Gpu {
                    id: String::new(),
                    name: name.into()
                }),
                "{name}"
            );
        }
        for name in [
            "NVIDIA GeForce GTX 1080 Ti",
            "NVIDIA TITAN V",
            "NVIDIA Quadro P4000",
            "NVIDIA GeForce MX350",
            "NVIDIA GeForce GT 1030",
            "NVIDIA A100",
            "NVIDIA A30",
            "NVIDIA H100",
            "Intel Arc A770",
            "AMD Radeon RX 7900",
            "RTX 4060",
            "NVIDIA",
        ] {
            assert!(
                !supports_nvidium(&Gpu {
                    id: String::new(),
                    name: name.into()
                }),
                "{name}"
            );
        }
    }

    #[test]
    fn distinguishes_inventory_failure_from_empty_and_handles_one_adapter() {
        assert!(parse_windows(b"not json").is_err());
        assert!(parse_windows(b"[]").unwrap().is_empty());
        assert_eq!(
            parse_windows(
                br#"{"Name":"NVIDIA RTX 3060","AdapterCompatibility":"NVIDIA","PNPDeviceID":"id"}"#
            )
            .unwrap()
            .len(),
            1
        );
    }
}
