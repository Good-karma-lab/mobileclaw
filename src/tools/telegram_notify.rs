use super::traits::{Tool, ToolResult};
use crate::security::SecurityPolicy;
use async_trait::async_trait;
use reqwest::Client;
use serde_json::json;
use std::sync::Arc;
use std::time::Duration;

const TELEGRAM_API_TIMEOUT_SECS: u64 = 15;

pub struct TelegramNotifyTool {
    client: Client,
    security: Arc<SecurityPolicy>,
    bot_token: String,
    chat_id: String,
}

impl TelegramNotifyTool {
    pub fn new(security: Arc<SecurityPolicy>, bot_token: String, chat_id: String) -> Self {
        let client = Client::builder()
            .timeout(Duration::from_secs(TELEGRAM_API_TIMEOUT_SECS))
            .build()
            .unwrap_or_else(|_| Client::new());

        Self {
            client,
            security,
            bot_token,
            chat_id,
        }
    }

    fn send_message_url(&self) -> String {
        format!(
            "https://api.telegram.org/bot{}/sendMessage",
            self.bot_token
        )
    }
}

#[async_trait]
impl Tool for TelegramNotifyTool {
    fn name(&self) -> &str {
        "telegram_notify"
    }

    fn description(&self) -> &str {
        "Send a Telegram message to the configured notify chat. Use this to proactively notify the user via Telegram."
    }

    fn parameters_schema(&self) -> serde_json::Value {
        json!({
            "type": "object",
            "properties": {
                "message": {
                    "type": "string",
                    "description": "The message text to send"
                }
            },
            "required": ["message"]
        })
    }

    async fn execute(&self, args: serde_json::Value) -> anyhow::Result<ToolResult> {
        if !self.security.can_act() {
            return Ok(ToolResult {
                success: false,
                output: String::new(),
                error: Some("Action blocked: autonomy is read-only".into()),
            });
        }

        if !self.security.record_action() {
            return Ok(ToolResult {
                success: false,
                output: String::new(),
                error: Some("Action blocked: rate limit exceeded".into()),
            });
        }

        let message = args
            .get("message")
            .and_then(|v| v.as_str())
            .map(str::trim)
            .filter(|v| !v.is_empty())
            .ok_or_else(|| anyhow::anyhow!("Missing 'message' parameter"))?
            .to_string();

        let body = json!({
            "chat_id": self.chat_id,
            "text": message,
            "parse_mode": "Markdown"
        });

        let response = self
            .client
            .post(&self.send_message_url())
            .header("Content-Type", "application/json")
            .body(body.to_string())
            .send()
            .await?;

        let status = response.status();
        let response_text = response.text().await.unwrap_or_default();

        if !status.is_success() {
            return Ok(ToolResult {
                success: false,
                output: response_text.clone(),
                error: Some(format!("Telegram API returned status {}", status)),
            });
        }

        let ok = serde_json::from_str::<serde_json::Value>(&response_text)
            .ok()
            .and_then(|json| json.get("ok").and_then(|v| v.as_bool()))
            .unwrap_or(false);

        if ok {
            Ok(ToolResult {
                success: true,
                output: format!("Telegram message sent to chat {}.", self.chat_id),
                error: None,
            })
        } else {
            Ok(ToolResult {
                success: false,
                output: response_text.clone(),
                error: Some("Telegram API returned ok=false".into()),
            })
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::security::AutonomyLevel;

    fn test_security(level: AutonomyLevel, max_actions_per_hour: u32) -> Arc<SecurityPolicy> {
        Arc::new(SecurityPolicy {
            autonomy: level,
            max_actions_per_hour,
            workspace_dir: std::env::temp_dir(),
            ..SecurityPolicy::default()
        })
    }

    #[test]
    fn tool_name() {
        let tool = TelegramNotifyTool::new(
            test_security(AutonomyLevel::Full, 100),
            "123:ABC".into(),
            "987654321".into(),
        );
        assert_eq!(tool.name(), "telegram_notify");
    }

    #[test]
    fn tool_description_non_empty() {
        let tool = TelegramNotifyTool::new(
            test_security(AutonomyLevel::Full, 100),
            "123:ABC".into(),
            "987654321".into(),
        );
        assert!(!tool.description().is_empty());
    }

    #[test]
    fn tool_requires_message_param() {
        let tool = TelegramNotifyTool::new(
            test_security(AutonomyLevel::Full, 100),
            "123:ABC".into(),
            "987654321".into(),
        );
        let schema = tool.parameters_schema();
        let required = schema["required"].as_array().unwrap();
        assert!(required.contains(&serde_json::Value::String("message".to_string())));
    }

    #[tokio::test]
    async fn execute_blocks_readonly_mode() {
        let tool = TelegramNotifyTool::new(
            test_security(AutonomyLevel::ReadOnly, 100),
            "123:ABC".into(),
            "987654321".into(),
        );
        let result = tool.execute(json!({"message": "hello"})).await.unwrap();
        assert!(!result.success);
        assert!(result.error.unwrap().contains("read-only"));
    }

    #[tokio::test]
    async fn execute_blocks_rate_limit() {
        let tool = TelegramNotifyTool::new(
            test_security(AutonomyLevel::Full, 0),
            "123:ABC".into(),
            "987654321".into(),
        );
        let result = tool.execute(json!({"message": "hello"})).await.unwrap();
        assert!(!result.success);
        assert!(result.error.unwrap().contains("rate limit"));
    }
}
