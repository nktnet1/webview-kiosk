//! Use the same upstream parser and line routing for validation and compilation.
//! Unsupported syntax is not a malformed rule: subscriptions often mix engines' features.

use adblock::{
    filters::network::NetworkFilterError,
    lists::{parse_filter, FilterFormat, FilterParseError, ParseOptions, RuleTypes},
};
use std::net::IpAddr;

pub(crate) enum Rule<'a> {
    Filter(&'a str, FilterFormat),
    Unsupported,
    Invalid(&'static str),
}

/// Hosts records may coexist with ABP rules, use IPv4/IPv6, and contain aliases/comments.
/// Only IP-prefixed records are routed as hosts; bare ABP patterns retain their meaning.
pub(crate) fn for_each_rule<'a>(text: &'a str, mut visit: impl FnMut(usize, Rule<'a>)) {
    let mut first_rule = true;
    let mut prefer_hosts = false;
    for (index, line) in text.trim_start_matches('\u{feff}').lines().enumerate() {
        let line = line.trim();
        // The second '#' test mirrors upstream cosmetic detection, so generic
        // cosmetic filters such as ##.ad and #@#.ad are never mistaken for comments.
        let hash_comment = line.starts_with('#')
            && !line.as_bytes()[1..line.len().min(5)].contains(&b'#');
        if line.is_empty() || line.starts_with('!') || line.starts_with("[Adblock") || hash_comment {
            continue;
        }
        let mut fields = line.split_whitespace();
        let first = fields.next().unwrap_or("");
        let has_fields = fields.next().is_some();
        let ip = first.parse::<IpAddr>();
        if first_rule {
            // Preserve bare-domain semantics in existing hosts lists, which were
            // selected by the first meaningful IP-prefixed line before this change.
            prefer_hosts = has_fields && ip.is_ok();
            first_rule = false;
        }
        let resembles_ip = (first.contains('.')
            && first.chars().all(|c| c.is_ascii_digit() || c == '.'))
            || (first.matches(':').count() >= 2
                && first.chars().all(|c| c.is_ascii_hexdigit() || c == '.' || c == ':'));
        if has_fields && (ip.is_ok() || resembles_ip) {
            if ip.is_err() {
                visit(index + 1, Rule::Invalid("invalid hosts IP address"));
                continue;
            }
            let record = line.split('#').next().unwrap_or("");
            let mut hosts = record.split_whitespace().skip(1).peekable();
            if hosts.peek().is_none() {
                visit(index + 1, Rule::Invalid("hosts record has no hostname"));
            }
            for hostname in hosts {
                if !hostname.contains('.') {
                    // Local machine aliases (localhost, ip6-loopback, etc.) have
                    // no request-blocking equivalent. Do not reject a valid hosts file.
                    visit(index + 1, Rule::Unsupported);
                } else {
                    visit(index + 1, Rule::Filter(hostname, FilterFormat::Hosts));
                }
            }
        } else {
            let format = if prefer_hosts && parse_filter(
                line,
                false,
                ParseOptions {
                    format: FilterFormat::Hosts,
                    rule_types: RuleTypes::NetworkOnly,
                    ..ParseOptions::default()
                },
            ).is_ok() {
                FilterFormat::Hosts
            } else {
                FilterFormat::Standard
            };
            visit(index + 1, Rule::Filter(line, format));
        }
    }
}

#[derive(Default, Debug)]
pub(crate) struct ValidationReport {
    pub(crate) supported: usize,
    pub(crate) unsupported: usize,
    pub(crate) invalid: usize,
    // Bound diagnostics even for an 8 MiB file containing millions of bad rules.
    errors: Vec<String>,
}

impl ValidationReport {
    fn invalid_rule(&mut self, line: usize, message: impl std::fmt::Display) {
        self.invalid += 1;
        if self.errors.len() < 5 {
            self.errors.push(format!("Line {line}: {message}"));
        }
    }

    pub(crate) fn error_message(&self) -> Option<String> {
        if self.invalid > 0 {
            let mut message = format!("Invalid filter syntax:\n{}", self.errors.join("\n"));
            if self.invalid > self.errors.len() {
                message.push_str(&format!("\n… and {} more errors", self.invalid - self.errors.len()));
            }
            Some(message)
        } else if self.supported == 0 {
            if self.unsupported == 0 {
                Some("Filter list has no rules (only comments or blank lines)".to_owned())
            } else {
                Some(format!(
                    "Filter list has no supported network rules. Unsupported rules: {}. No changes were saved.",
                    self.unsupported,
                ))
            }
        } else {
            None
        }
    }
}

/// Reject only explicit syntax errors. Other engine restrictions, unknown options,
/// disabled full-regex support, and future error variants are compatibility warnings.
fn malformed(error: &NetworkFilterError) -> bool {
    matches!(
        error,
        NetworkFilterError::FilterParseError
            | NetworkFilterError::PunycodeError
            | NetworkFilterError::RegexParsingError(_)
            | NetworkFilterError::EmptyRedirection
            | NetworkFilterError::NegatedBadFilter
            | NetworkFilterError::NegatedImportant
            | NetworkFilterError::NegatedOptionMatchCase
            | NetworkFilterError::NegatedExplicitCancel
            | NetworkFilterError::NegatedRedirection
            | NetworkFilterError::NegatedTag
            | NetworkFilterError::NegatedGenericHide
    )
}

pub(crate) fn validate(text: &str) -> ValidationReport {
    let mut report = ValidationReport::default();
    for_each_rule(text, |line, rule| match rule {
        Rule::Invalid(message) => report.invalid_rule(line, message),
        Rule::Unsupported => report.unsupported += 1,
        Rule::Filter(text, format) => {
            let result = parse_filter(
                text,
                false,
                ParseOptions {
                    format,
                    rule_types: RuleTypes::NetworkOnly,
                    ..ParseOptions::default()
                },
            );
            match result {
                Ok(_) => report.supported += 1,
                Err(FilterParseError::Network(error)) if malformed(&error) => {
                    report.invalid_rule(line, error);
                }
                Err(FilterParseError::Empty) => (),
                // Cosmetics/scriptlets are outside this network-only integration.
                // Metadata warnings and unsupported extensions must not reject a list.
                Err(_) => report.unsupported += 1,
            }
        }
    });
    report
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn accepts_network_rules_exceptions_and_unicode() {
        let report = validate("||ads.example^\n@@||ads.example/ok.js$script\n||例子.测试^");
        assert!(report.error_message().is_none());
        assert_eq!(report.supported, 3);
    }

    #[test]
    fn accepts_bom_crlf_headers_and_comments() {
        let report = validate("\u{feff}[Adblock Plus 2.0]\r\n! Title: Test\r\n#comment\r\n\r\n||ads.example^\r\n");
        assert!(report.error_message().is_none());
        assert_eq!(report.supported, 1);
        assert_eq!(report.unsupported, 0);
    }

    #[test]
    fn accepts_hosts_ipv6_aliases_and_inline_comments() {
        let report = validate("127.0.0.1 localhost\n::1 localhost ip6-loopback\n0.0.0.0 ads.example alias.example # comment\n:: telemetry.example");
        assert!(report.error_message().is_none());
        assert_eq!(report.supported, 3);
    }

    #[test]
    fn accepts_mixed_formats_in_either_order() {
        for text in [
            "||ads.example^\n0.0.0.0 telemetry.example\n@@||ads.example/ok.js",
            "0.0.0.0 telemetry.example\n||ads.example^\n@@||ads.example/ok.js",
        ] {
            let report = validate(text);
            assert!(report.error_message().is_none());
            assert_eq!(report.supported, 3);
        }
    }

    #[test]
    fn unsupported_features_are_not_syntax_errors() {
        let report = validate("||ads.example^\n||other.example^$unknown-option\n||other.example^$removeparam=/test/\n/[a-z]+/\nexample.com##.ad\n##.generic-ad\nexample.com##+js(set, foo, bar)");
        assert!(report.error_message().is_none());
        assert_eq!(report.supported, 1);
        assert_eq!(report.unsupported, 6);
    }

    #[test]
    fn unsupported_only_list_cannot_be_saved_as_a_usable_network_list() {
        let report = validate("example.com##.ad\n||ads.example^$future-option");
        assert!(report.error_message().unwrap().contains("no supported network rules"));
        assert_eq!(report.unsupported, 2);
        assert_eq!(report.invalid, 0);
    }

    #[test]
    fn unsupported_rules_are_warnings_when_supported_network_rules_remain() {
        let report = validate("example.com##.ad\n||ads.example^$future-option\n||supported.example^");
        assert!(report.error_message().is_none());
        assert_eq!(report.supported, 1);
        assert_eq!(report.unsupported, 2);
        assert_eq!(report.invalid, 0);
    }

    #[test]
    fn valid_literal_patterns_are_network_rules_without_special_markers() {
        let report = validate("advertisement\n@@allowed\n/ads/banner*\n||ads.example^");
        assert!(report.error_message().is_none());
        assert_eq!(report.supported, 4);
    }

    #[test]
    fn exception_only_lists_are_supported_network_rules() {
        let report = validate("@@||allowed.example^");
        assert!(report.error_message().is_none());
        assert_eq!(report.supported, 1);
    }

    #[test]
    fn empty_or_comment_only_lists_have_a_distinct_error() {
        for text in ["", " \n\t", "\u{feff}\n! comment\n#comment\n[Adblock Plus 2.0]"] {
            let report = validate(text);
            assert_eq!(report.invalid, 0);
            assert!(report.error_message().unwrap().contains("no rules"));
        }
    }

    #[test]
    fn malformed_network_rule_reports_original_line_number() {
        let report = validate("\u{feff}! header\r\n\r\n||ads.example^\r\n||bad.example^$redirect=\r\n");
        assert_eq!(report.invalid, 1);
        assert!(report.error_message().unwrap().contains("Line 4: empty redirection"));
    }

    #[test]
    fn malformed_hosts_records_report_their_lines() {
        let report = validate("0.0.0.0 good.example\n0.0.0.0 bad/host.example\n999.0.0.0 ads.example\n:: # missing host");
        assert_eq!(report.invalid, 3);
        let error = report.error_message().unwrap();
        for line in [2, 3, 4] {
            assert!(error.contains(&format!("Line {line}:")));
        }
    }

    #[test]
    fn large_lists_have_bounded_diagnostics() {
        let report = validate(&"||bad.example^$redirect=\n".repeat(100_000));
        assert_eq!(report.invalid, 100_000);
        assert_eq!(report.errors.len(), 5);
        assert!(report.error_message().unwrap().contains("99995 more errors"));
    }
}
