use interfrost::api::minecraft::{Os, OsRule, Rule, RuleAction};

use arcadium_common::os_ext::OsExt;

#[must_use]
pub fn validate_rules(rules: &[Rule], java_arch: &str, updated: bool) -> bool {
    let mut rule = rules
        .iter()
        .map(|r| validate_rule(r, java_arch, updated))
        .collect::<Vec<Option<bool>>>();

    if rules
        .iter()
        .all(|r| matches!(r.action, RuleAction::Disallow))
    {
        rule.push(Some(true));
    }

    !(rule.iter().any(|r| r == &Some(false)) || rule.iter().all(Option::is_none))
}

#[must_use]
pub fn validate_rule(rule: &Rule, java_arch: &str, updated: bool) -> Option<bool> {
    let result = match rule {
        Rule { os: Some(os), .. } => validate_os_rule(os, java_arch, updated),
        Rule {
            features: Some(features),
            ..
        } => {
            !features.is_demo_user.unwrap_or(true)
                || features.has_custom_resolution.unwrap_or(false)
                || !features.has_quick_plays_support.unwrap_or(true)
                || !features.is_quick_play_multiplayer.unwrap_or(true)
                || !features.is_quick_play_realms.unwrap_or(true)
                || !features.is_quick_play_singleplayer.unwrap_or(true)
        }
        _ => return Some(true),
    };

    match rule.action {
        RuleAction::Allow => Some(result),
        RuleAction::Disallow => {
            if result {
                Some(false)
            } else {
                None
            }
        }
    }
}

#[must_use]
pub fn validate_os_rule(rule: &OsRule, java_arch: &str, updated: bool) -> bool {
    let mut rule_match = true;

    if let Some(ref arch) = rule.arch {
        let canonical = match java_arch {
            "amd64" | "x64" => "x86_64",
            "arm64" => "aarch64",
            value => value,
        };
        rule_match &= regex::Regex::new(&format!("^(?:{arch})$"))
            .is_ok_and(|pattern| pattern.is_match(java_arch) || pattern.is_match(canonical));
    }

    if let Some(name) = &rule.name {
        if (updated || rule.arch.is_some())
            && !matches!(
                name,
                Os::LinuxArm64 | Os::LinuxArm32 | Os::OsxArm64 | Os::WindowsArm64
            )
        {
            rule_match &= Os::native() == name.get_os() || &Os::native_arch(java_arch) == name;
        } else {
            rule_match &= &Os::native_arch(java_arch) == name;
        }
    }

    rule_match
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn lwjgl_native_architectures_are_selected_independently() {
        for (required, java, expected) in [
            ("aarch64", "amd64", false),
            ("aarch64", "aarch64", true),
            ("x86_64", "amd64", true),
            ("x86", "amd64", false),
            ("aarch64", "arm64", true),
        ] {
            let rule: OsRule =
                serde_json::from_value(serde_json::json!({"arch":required})).unwrap();
            assert_eq!(
                validate_os_rule(&rule, java, true),
                expected,
                "{required} vs {java}"
            );
        }
    }
}
