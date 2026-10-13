//! Minimal JNI adapter around Brave's MPL-2.0 adblock engine.
//! No lists or resource/scriptlet bundles are shipped with the APK.

use adblock::{
    lists::{FilterFormat, FilterSet, ParseOptions, RuleTypes},
    request::Request,
    Engine,
};
use jni::{
    objects::{JObject, JObjectArray, JString},
    sys::{jboolean, JNI_FALSE, JNI_TRUE},
    JNIEnv,
};
use std::sync::{OnceLock, RwLock};

static ENGINE: OnceLock<RwLock<Option<Engine>>> = OnceLock::new();

fn engine_slot() -> &'static RwLock<Option<Engine>> {
    ENGINE.get_or_init(|| RwLock::new(None))
}

fn build_engine(lists: Vec<String>) -> Engine {
    let mut set = FilterSet::new(false);
    for list in lists {
        // Parsing network rules only avoids allocating cosmetic resources that this
        // integration cannot yet apply. Hosts-format lists are supported explicitly.
        let format = if list
            .lines()
            .map(str::trim)
            .find(|line| {
                !line.is_empty()
                    && !line.starts_with('!')
                    && !line.starts_with('#')
                    && !line.starts_with('[')
            })
            .is_some_and(|line| {
                let first = line.split_whitespace().next().unwrap_or("");
                first == "0.0.0.0" || first == "127.0.0.1"
            })
        {
            FilterFormat::Hosts
        } else {
            FilterFormat::Standard
        };
        set.add_filter_list(
            list,
            ParseOptions {
                format,
                rule_types: RuleTypes::NetworkOnly,
                ..ParseOptions::default()
            },
        );
    }
    Engine::new_with_filter_set(set)
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
    fn malformed_request_fails_open() {
        let engine = build_engine(vec!["||ads.example^".to_owned()]);
        assert!(!matches(&engine, "not a URL", "https://publisher.example", "other", "GET"));
    }
}
