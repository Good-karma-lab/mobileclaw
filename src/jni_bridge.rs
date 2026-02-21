//! JNI bridge for Android integration
//!
//! This module exposes the ZeroClaw agent runtime as JNI functions
//! that can be called from Kotlin/Java code on Android.
//!
//! Architecture:
//! - Agent runs in-process (not subprocess) to bypass SELinux
//! - Each agent handle maintains a tokio runtime
//! - Thread-safe handle management with Arc/Mutex

use crate::agent;
use crate::config::Config;
use jni::objects::{JClass, JObject, JString};
use jni::sys::{jboolean, jlong, jstring};
use jni::JNIEnv;
use std::collections::HashMap;
use std::sync::Mutex;
use tokio::runtime::Runtime;

/// Global registry of agent handles
/// Maps handle ID (jlong) to runtime + config
static AGENT_HANDLES: Mutex<Option<HashMap<i64, AgentHandle>>> = Mutex::new(None);

struct AgentHandle {
    runtime: Runtime,
    config: Config,
}


/// Initialize the agent handle registry
fn init_handles() {
    let mut handles = AGENT_HANDLES.lock().unwrap();
    if handles.is_none() {
        *handles = Some(HashMap::new());
    }
}

/// Generate unique handle ID
fn next_handle_id() -> i64 {
    use std::sync::atomic::{AtomicI64, Ordering};
    static NEXT_ID: AtomicI64 = AtomicI64::new(1);
    NEXT_ID.fetch_add(1, Ordering::SeqCst)
}

/// Start the ZeroClaw agent runtime
///
/// Returns a handle (jlong) that must be passed to subsequent calls
#[no_mangle]
pub extern "C" fn Java_com_mobileclaw_app_ZeroClawBackend_startAgent(
    mut env: JNIEnv,
    _class: JClass,
    config_path: JString,
    api_key: JString,
    model: JString,
    telegram_token: JString,
) -> jlong {
    init_handles();

    // Convert config path from Java
    let config_path_str: String = match env.get_string(&config_path) {
        Ok(s) => s.into(),
        Err(e) => {
            let _ = env.throw_new("java/lang/RuntimeException", format!("Invalid config path: {}", e));
            return 0;
        }
    };

    let api_key_str: String = env.get_string(&api_key).map(Into::into).unwrap_or_default();
    let model_str: String = env.get_string(&model).map(Into::into).unwrap_or_default();
    let telegram_token_str: String = env.get_string(&telegram_token).map(Into::into).unwrap_or_default();

    // Set ZEROCLAW_WORKSPACE environment variable for Android
    // This points to the app's files directory where we can write
    std::env::set_var("ZEROCLAW_WORKSPACE", &config_path_str);

    // Load configuration from workspace
    let mut config = match Config::load_or_init() {
        Ok(c) => c,
        Err(e) => {
            let _ = env.throw_new(
                "java/lang/RuntimeException",
                format!("Failed to load config: {}", e),
            );
            return 0;
        }
    };

    // Apply Android overrides from caller
    config.api_key = if api_key_str.is_empty() { config.api_key } else { Some(api_key_str) };
    config.default_model = if model_str.is_empty() { config.default_model } else { Some(model_str) };
    if config.default_provider.is_none() {
        config.default_provider = Some("openrouter".into());
    }
    config.gateway.port = 8000;
    config.gateway.require_pairing = false;
    config.android.enabled = true;
    config.android.bridge.mode = "http".into();
    if !telegram_token_str.is_empty() {
        config.channels_config.telegram = Some(crate::config::schema::TelegramConfig {
            bot_token: telegram_token_str,
            allowed_users: vec![],
        });
    }

    // Create runtime and spawn the full daemon (gateway + channels + scheduler)
    let runtime = match tokio::runtime::Runtime::new() {
        Ok(r) => r,
        Err(e) => {
            let _ = env.throw_new(
                "java/lang/RuntimeException",
                format!("Failed to create tokio runtime: {}", e),
            );
            return 0;
        }
    };

    let daemon_config = config.clone();
    runtime.spawn(async move {
        if let Err(e) = crate::daemon::run(daemon_config, "127.0.0.1".into(), 8000).await {
            eprintln!("[ZeroClaw] Daemon exited: {}", e);
        }
    });

    let handle = AgentHandle { runtime, config };

    // Store handle and return ID
    let handle_id = next_handle_id();
    let mut handles = AGENT_HANDLES.lock().unwrap();
    if let Some(map) = handles.as_mut() {
        map.insert(handle_id, handle);
    }

    handle_id
}

/// Process a message through the agent runtime
///
/// This is the main entry point for agent conversations.
/// Returns the agent's response as a String.
#[no_mangle]
pub extern "C" fn Java_com_mobileclaw_app_ZeroClawBackend_processMessage(
    mut env: JNIEnv,
    _class: JClass,
    handle_id: jlong,
    message: JString,
) -> jstring {
    // Get handle
    let handles = AGENT_HANDLES.lock().unwrap();
    let handle = match handles.as_ref().and_then(|m| m.get(&handle_id)) {
        Some(h) => h,
        None => {
            let _ = env.throw_new("java/lang/RuntimeException", "Invalid handle ID");
            return JObject::null().into_raw();
        }
    };

    // Convert message
    let message_str: String = match env.get_string(&message) {
        Ok(s) => s.into(),
        Err(e) => {
            let _ = env.throw_new("java/lang/RuntimeException", format!("Invalid message: {}", e));
            return JObject::null().into_raw();
        }
    };

    // Process message through agent runtime
    let config = handle.config.clone();
    let response = handle.runtime.block_on(async move {
        match agent::loop_::process_message(config, &message_str).await {
            Ok(r) => r,
            Err(e) => format!("Error processing message: {}", e),
        }
    });

    // Return response
    match env.new_string(&response) {
        Ok(s) => s.into_raw(),
        Err(e) => {
            let _ = env.throw_new("java/lang/RuntimeException", format!("Failed to create response string: {}", e));
            JObject::null().into_raw()
        }
    }
}

/// Check if the agent is healthy
#[no_mangle]
pub extern "C" fn Java_com_mobileclaw_app_ZeroClawBackend_isHealthy(
    _env: JNIEnv,
    _class: JClass,
    handle_id: jlong,
) -> jboolean {
    let handles = AGENT_HANDLES.lock().unwrap();
    match handles.as_ref().and_then(|m| m.get(&handle_id)) {
        Some(_) => 1, // true
        None => 0,     // false
    }
}

/// Stop the agent and release resources
#[no_mangle]
pub extern "C" fn Java_com_mobileclaw_app_ZeroClawBackend_stopAgent(
    mut env: JNIEnv,
    _class: JClass,
    handle_id: jlong,
) {
    let mut handles = AGENT_HANDLES.lock().unwrap();
    if let Some(map) = handles.as_mut() {
        if map.remove(&handle_id).is_none() {
            let _ = env.throw_new("java/lang/RuntimeException", "Invalid handle ID");
        }
    }
}

/// Get the gateway URL for this agent instance
#[no_mangle]
pub extern "C" fn Java_com_mobileclaw_app_ZeroClawBackend_getGatewayUrl(
    mut env: JNIEnv,
    _class: JClass,
    handle_id: jlong,
) -> jstring {
    let handles = AGENT_HANDLES.lock().unwrap();
    let handle = match handles.as_ref().and_then(|m| m.get(&handle_id)) {
        Some(h) => h,
        None => {
            let _ = env.throw_new("java/lang/RuntimeException", "Invalid handle ID");
            return JObject::null().into_raw();
        }
    };

    let url = format!(
        "http://{}:{}",
        &handle.config.gateway.host,
        handle.config.gateway.port
    );

    match env.new_string(&url) {
        Ok(s) => s.into_raw(),
        Err(e) => {
            let _ = env.throw_new("java/lang/RuntimeException", format!("Failed to create URL string: {}", e));
            JObject::null().into_raw()
        }
    }
}

/// Execute a tool action
///
/// This allows direct tool execution through the agent runtime.
/// Note: Tool execution is already handled by process_message,
/// so this is a simplified version that just calls the agent.
#[no_mangle]
pub extern "C" fn Java_com_mobileclaw_app_ZeroClawBackend_executeTool(
    mut env: JNIEnv,
    _class: JClass,
    handle_id: jlong,
    tool_name: JString,
    params_json: JString,
) -> jstring {
    // Get handle
    let handles = AGENT_HANDLES.lock().unwrap();
    let handle = match handles.as_ref().and_then(|m| m.get(&handle_id)) {
        Some(h) => h,
        None => {
            let _ = env.throw_new("java/lang/RuntimeException", "Invalid handle ID");
            return JObject::null().into_raw();
        }
    };

    // Convert parameters
    let tool_name_str: String = match env.get_string(&tool_name) {
        Ok(s) => s.into(),
        Err(e) => {
            let _ = env.throw_new("java/lang/RuntimeException", format!("Invalid tool name: {}", e));
            return JObject::null().into_raw();
        }
    };

    let params_str: String = match env.get_string(&params_json) {
        Ok(s) => s.into(),
        Err(e) => {
            let _ = env.throw_new("java/lang/RuntimeException", format!("Invalid params: {}", e));
            return JObject::null().into_raw();
        }
    };

    // For now, we'll construct a message to the agent asking it to execute the tool
    // In future, could add direct tool execution API
    let message = format!("Execute tool: {} with params: {}", tool_name_str, params_str);

    let config = handle.config.clone();
    let result = handle.runtime.block_on(async move {
        match agent::loop_::process_message(config, &message).await {
            Ok(response) => serde_json::json!({
                "success": true,
                "result": response
            }),
            Err(e) => serde_json::json!({
                "success": false,
                "error": format!("{}", e)
            }),
        }
    });

    // Return result as JSON string
    let result_str = result.to_string();
    match env.new_string(&result_str) {
        Ok(s) => s.into_raw(),
        Err(e) => {
            let _ = env.throw_new("java/lang/RuntimeException", format!("Failed to create result string: {}", e));
            JObject::null().into_raw()
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_handle_generation() {
        let id1 = next_handle_id();
        let id2 = next_handle_id();
        assert!(id2 > id1, "IDs should be monotonically increasing");
    }
}
