//! Minimal JNI adapter around Brave's MPL-2.0 adblock engine.
//! No lists or resource/scriptlet bundles are shipped with the APK.

use adblock::{
    lists::{FilterFormat, FilterSet, ParseOptions, RuleTypes},
    request::Request,
    Engine,
};
use jni::{
    objects::{JObject, JObjectArray, JString},
    sys::{jboolean, jstring, JNI_FALSE, JNI_TRUE},
    JNIEnv,
};
use std::sync::{OnceLock, RwLock};

mod validation;

static ENGINE: OnceLock<RwLock<Option<Engine>>> = OnceLock::new();

fn engine_slot() -> &'static RwLock<Option<Engine>> {
    ENGINE.get_or_init(|| RwLock::new(None))
}

fn build_engine(lists: Vec<String>) -> Engine {
    let mut set = FilterSet::new(false);
    for list in lists {
        let mut standard = String::new();
        let mut hosts = String::new();
        validation::for_each_rule(&list, |_, rule| {
            if let validation::Rule::Filter(text, format) = rule {
                let target = match format {
                    FilterFormat::Standard => &mut standard,
                    FilterFormat::Hosts => &mut hosts,
                };
                target.push_str(text);
                target.push('\n');
            }
        });
        for (text, format) in [
            (standard, FilterFormat::Standard),
            (hosts, FilterFormat::Hosts),
        ] {
            if !text.is_empty() {
                set.add_filter_list(
                    text,
                    ParseOptions {
                        format,
                        rule_types: RuleTypes::NetworkOnly,
                        ..ParseOptions::default()
                    },
                );
            }
        }
    }
    Engine::new_with_filter_set(set)
}

/// Return bounded, line-numbered syntax errors without touching the active engine.
/// A JNI failure throws instead of returning the null used for successful validation.
#[no_mangle]
pub extern "system" fn Java_uk_nktnet_webviewkiosk_blocking_NativeAdblockEngine_nativeValidationError(
    mut env: JNIEnv,
    _self: JObject,
    text: JString,
) -> jstring {
    let text: String = match env.get_string(&text) {
        Ok(value) => value.into(),
        Err(_) => {
            let _ = env.throw_new(
                "java/lang/IllegalStateException",
                "Cannot read filter text for validation",
            );
            return std::ptr::null_mut();
        }
    };
    match validation::validate(&text).error_message() {
        None => std::ptr::null_mut(),
        Some(message) => match env.new_string(message) {
            Ok(message) => message.into_raw(),
            Err(_) => {
                let _ = env.throw_new(
                    "java/lang/IllegalStateException",
                    "Cannot return filter validation errors",
                );
                std::ptr::null_mut()
            }
        },
    }
}

fn matches(engine: &Engine, url: &str, source_url: &str, kind: &str, method: &str) -> bool {
    let Ok(request) = Request::new(url, source_url, kind, method) else {
        return false;
    };
    engine.check_network_request(&request).should_block()
}

/// Compile all lists *outside* the write lock, then atomically replace the engine.
/// Invalid JNI arguments leave the previous engine unchanged.
#[no_mangle]
pub extern "system" fn Java_uk_nktnet_webviewkiosk_blocking_NativeAdblockEngine_nativeReplace(
    mut env: JNIEnv,
    _self: JObject,
    texts: JObjectArray,
) -> jboolean {
    let Ok(length) = env.get_array_length(&texts) else {
        return JNI_FALSE;
    };
    let mut lists = Vec::with_capacity(length as usize);
    for i in 0..length {
        let Ok(element) = env.get_object_array_element(&texts, i) else {
            return JNI_FALSE;
        };
        let jstring = JString::from(element);
        let Ok(text) = env.get_string(&jstring) else {
            return JNI_FALSE;
        };
        lists.push(String::from(text));
    }
    let replacement = build_engine(lists);
    match engine_slot().write() {
        Ok(mut slot) => {
            *slot = Some(replacement);
            JNI_TRUE
        }
        Err(_) => JNI_FALSE,
    }
}

#[no_mangle]
pub extern "system" fn Java_uk_nktnet_webviewkiosk_blocking_NativeAdblockEngine_nativeClear(
    _env: JNIEnv,
    _self: JObject,
) {
    if let Ok(mut slot) = engine_slot().write() {
        *slot = None;
    }
}

#[no_mangle]
pub extern "system" fn Java_uk_nktnet_webviewkiosk_blocking_NativeAdblockEngine_nativeShouldBlock(
    mut env: JNIEnv,
    _self: JObject,
    url: JString,
    source_url: JString,
    kind: JString,
    method: JString,
) -> jboolean {
    let url: String = match env.get_string(&url) {
        Ok(value) => value.into(),
        Err(_) => return JNI_FALSE,
    };
    let source: String = match env.get_string(&source_url) {
        Ok(value) => value.into(),
        Err(_) => return JNI_FALSE,
    };
    let kind: String = match env.get_string(&kind) {
        Ok(value) => value.into(),
        Err(_) => return JNI_FALSE,
    };
    let method: String = match env.get_string(&method) {
        Ok(value) => value.into(),
        Err(_) => return JNI_FALSE,
    };
    let Ok(slot) = engine_slot().read() else {
        return JNI_FALSE;
    };
    match slot.as_ref() {
        Some(engine) if matches(engine, &url, &source, &kind, &method) => JNI_TRUE,
        _ => JNI_FALSE,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn network_rules_and_exceptions() {
        let engine = build_engine(vec![
            "||ads.example^\n@@||ads.example/allowed.js^".to_owned(),
        ]);
        assert!(matches(
            &engine,
            "https://ads.example/track.js",
            "https://publisher.example/",
            "script",
            "GET",
        ));
        assert!(!matches(
            &engine,
            "https://ads.example/allowed.js",
            "https://publisher.example/",
            "script",
            "GET",
        ));
    }

    #[test]
    fn hosts_import_is_supported() {
        let engine = build_engine(vec!["0.0.0.0 telemetry.example".to_owned()]);
        assert!(matches(
            &engine,
            "https://telemetry.example/pixel",
            "https://publisher.example/",
            "other",
            "GET",
        ));
    }

    #[test]
    fn mixed_formats_compile_the_same_rules_that_validation_accepts() {
        for text in [
            "\u{feff}! header\n||ads.example^\n0.0.0.0 telemetry.example alias.example # hosts\n:: ipv6.example\n@@||ads.example/allowed.js^",
            "0.0.0.0 telemetry.example alias.example\n||ads.example^\n:: ipv6.example\n@@||ads.example/allowed.js^",
        ] {
            assert!(validation::validate(text).error_message().is_none());
            let engine = build_engine(vec![text.to_owned()]);
            for host in ["ads.example", "telemetry.example", "alias.example", "ipv6.example"] {
                assert!(matches(
                    &engine,
                    &format!("https://{host}/pixel"),
                    "https://publisher.example/",
                    "other",
                    "GET",
                ));
            }
            assert!(!matches(
                &engine,
                "https://ads.example/allowed.js",
                "https://publisher.example/",
                "script",
                "GET",
            ));
        }
    }

    #[test]
    fn bare_domains_in_existing_hosts_lists_keep_hostname_semantics() {
        let text = "0.0.0.0 telemetry.example\nads.example\n@@||ads.example/allowed.js^";
        assert!(validation::validate(text).error_message().is_none());
        let engine = build_engine(vec![text.to_owned()]);
        assert!(matches(
            &engine,
            "https://ads.example/pixel",
            "https://publisher.example/",
            "other",
            "GET",
        ));
        assert!(!matches(
            &engine,
            "https://publisher.example/ads.example.js",
            "https://publisher.example/",
            "script",
            "GET",
        ));
        assert!(!matches(
            &engine,
            "https://ads.example/allowed.js",
            "https://publisher.example/",
            "script",
            "GET",
        ));
    }

    #[test]
    fn validation_does_not_replace_active_engine() {
        *engine_slot().write().unwrap() = Some(build_engine(vec!["||original.example^".to_owned()]));
        assert!(validation::validate("||bad.example^$redirect=").error_message().is_some());
        assert!(validation::validate("||new.example^").error_message().is_none());
        {
            let slot = engine_slot().read().unwrap();
            let engine = slot.as_ref().unwrap();
            assert!(matches(
                engine,
                "https://original.example/pixel",
                "https://publisher.example/",
                "other",
                "GET",
            ));
            assert!(!matches(
                engine,
                "https://new.example/pixel",
                "https://publisher.example/",
                "other",
                "GET",
            ));
        }
        *engine_slot().write().unwrap() = None;
    }

    #[test]
    fn malformed_request_fails_open() {
        let engine = build_engine(vec!["||ads.example^".to_owned()]);
        assert!(!matches(&engine, "not a URL", "https://publisher.example", "other", "GET"));
    }
}
