use crate::providers::{create_provider_with_url, Provider};
use serde::{Deserialize, Serialize};
use std::ffi::{c_char, CStr, CString};

#[derive(Debug, Deserialize)]
struct MobileBridgeRequest {
    prompt: String,
    #[serde(default)]
    system_prompt: Option<String>,
    #[serde(default = "default_provider")]
    provider: String,
    #[serde(default = "default_model")]
    model: String,
    #[serde(default)]
    api_url: Option<String>,
    #[serde(default)]
    api_key: Option<String>,
    #[serde(default = "default_temperature")]
    temperature: f64,
}

#[derive(Debug, Serialize)]
struct MobileBridgeResponse {
    ok: bool,
    reply: Option<String>,
    error: Option<String>,
}

fn default_provider() -> String {
    "ollama".to_string()
}

fn default_model() -> String {
    "gpt-oss:20b".to_string()
}

fn default_temperature() -> f64 {
    0.2
}

fn to_c_string(value: &str) -> *mut c_char {
    match CString::new(value) {
        Ok(s) => s.into_raw(),
        Err(_) => CString::new(r#"{"ok":false,"reply":null,"error":"invalid utf8 response"}"#)
            .map(CString::into_raw)
            .unwrap_or(std::ptr::null_mut()),
    }
}

fn make_response(ok: bool, reply: Option<String>, error: Option<String>) -> String {
    serde_json::to_string(&MobileBridgeResponse { ok, reply, error }).unwrap_or_else(|_| {
        r#"{"ok":false,"reply":null,"error":"serialization failure"}"#.to_string()
    })
}

fn handle_request_json(request_json: &str) -> String {
    let request: MobileBridgeRequest = match serde_json::from_str(request_json) {
        Ok(req) => req,
        Err(error) => {
            return make_response(false, None, Some(format!("invalid request JSON: {error}")));
        }
    };

    if request.prompt.trim().is_empty() {
        return make_response(false, None, Some("prompt must not be empty".to_string()));
    }

    match run_chat(request) {
        Ok(reply) => make_response(true, Some(reply), None),
        Err(error) => make_response(false, None, Some(error.to_string())),
    }
}

fn run_chat(request: MobileBridgeRequest) -> anyhow::Result<String> {
    let provider: Box<dyn Provider> = create_provider_with_url(
        request.provider.trim(),
        request.api_key.as_deref().map(str::trim),
        request.api_url.as_deref().map(str::trim),
    )?;

    let runtime = tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()?;

    runtime.block_on(async move {
        provider
            .chat_with_system(
                request.system_prompt.as_deref(),
                request.prompt.trim(),
                request.model.trim(),
                request.temperature,
            )
            .await
    })
}

#[no_mangle]
pub extern "C" fn mobileclaw_chat_json(request_json_ptr: *const c_char) -> *mut c_char {
    if request_json_ptr.is_null() {
        return to_c_string(&make_response(
            false,
            None,
            Some("null request pointer".to_string()),
        ));
    }

    let request_raw = unsafe { CStr::from_ptr(request_json_ptr) };
    let request_json = match request_raw.to_str() {
        Ok(value) => value,
        Err(_) => {
            return to_c_string(&make_response(
                false,
                None,
                Some("request is not valid UTF-8".to_string()),
            ))
        }
    };

    to_c_string(&handle_request_json(request_json))
}

#[no_mangle]
pub extern "C" fn mobileclaw_free_cstring(ptr: *mut c_char) {
    if ptr.is_null() {
        return;
    }
    unsafe {
        let _ = CString::from_raw(ptr);
    }
}

#[cfg(target_os = "android")]
#[no_mangle]
pub extern "system" fn Java_com_zeroclaw_app_NativeZeroClawBridge_mobileclawChatJson(
    mut env: jni::JNIEnv,
    _class: jni::objects::JClass,
    request_json: jni::objects::JString,
) -> jni::sys::jstring {
    let request = match env.get_string(&request_json) {
        Ok(value) => value.to_string_lossy().into_owned(),
        Err(error) => {
            let message =
                make_response(false, None, Some(format!("jni get_string failed: {error}")));
            return env
                .new_string(message)
                .map(|s| s.into_raw())
                .unwrap_or(std::ptr::null_mut());
        }
    };

    let response = handle_request_json(&request);
    env.new_string(response)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}
