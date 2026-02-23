//! Rules management tool for the agent
//!
//! Allows the agent to create, list, and manage automation rules

use async_trait::async_trait;
use serde::Deserialize;

use crate::rules::{Action, ActionType, Condition, ConditionType, Rule, RuleTrigger, TriggerType, RulesEngine};
use crate::tools::traits::{Tool, ToolResult};

use std::path::PathBuf;
use std::sync::Arc;

pub struct RulesTool {
    engine: Arc<RulesEngine>,
}

impl RulesTool {
    pub fn new(engine: Arc<RulesEngine>) -> Self {
        Self { engine }
    }
    
    /// Create with database path directly
    pub fn with_db_path(db_path: PathBuf) -> Self {
        Self {
            engine: Arc::new(RulesEngine::new(db_path)),
        }
    }
}

#[derive(Debug, Deserialize)]
#[serde(tag = "action", rename_all = "snake_case")]
enum RulesRequest {
    Create {
        name: String,
        trigger_type: String,
        conditions: Vec<ConditionSpec>,
        action_type: String,
        action_params: serde_json::Value,
    },
    List,
    Get { id: String },
    Delete { id: String },
    Toggle { id: String, enabled: bool },
}

#[derive(Debug, Deserialize)]
struct ConditionSpec {
    condition_type: String,
    value: String,
    #[serde(default)]
    negate: bool,
}

#[async_trait]
impl Tool for RulesTool {
    fn name(&self) -> &str {
        "rules"
    }

    fn description(&self) -> &str {
        "Manage automation rules. Create rules like 'When I get a call from +123456, send Telegram'. \
         Actions: create, list, get, delete, toggle. \
         Trigger types: incoming_call, incoming_sms, geofence, schedule. \
         Action types: telegram_notify, send_sms, post_notification, agent_prompt, http_request, memory_store."
    }

    fn parameters_schema(&self) -> serde_json::Value {
        serde_json::json!({
            "type": "object",
            "properties": {
                "action": {
                    "type": "string",
                    "enum": ["create", "list", "get", "delete", "toggle"],
                    "description": "The action to perform"
                },
                "name": {
                    "type": "string",
                    "description": "Rule name (for create)"
                },
                "trigger_type": {
                    "type": "string",
                    "enum": ["incoming_call", "incoming_sms", "geofence", "schedule", "webhook"],
                    "description": "Type of trigger event"
                },
                "conditions": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "condition_type": {
                                "type": "string",
                                "enum": ["phone_number", "sms_content", "location", "time_of_day", "day_of_week", "custom_field"]
                            },
                            "value": { "type": "string" },
                            "negate": { "type": "boolean", "default": false }
                        },
                        "required": ["condition_type", "value"]
                    },
                    "description": "Conditions that must match for the rule to trigger"
                },
                "action_type": {
                    "type": "string",
                    "enum": ["telegram_notify", "send_sms", "place_call", "post_notification", "agent_prompt", "http_request", "memory_store"],
                    "description": "Action to execute when triggered"
                },
                "action_params": {
                    "type": "object",
                    "description": "Parameters for the action (e.g., {\"message\": \"Call received\"})"
                },
                "id": {
                    "type": "string",
                    "description": "Rule ID (for get, delete, toggle)"
                },
                "enabled": {
                    "type": "boolean",
                    "description": "Enable or disable the rule (for toggle)"
                }
            },
            "required": ["action"]
        })
    }

    async fn execute(&self, args: serde_json::Value) -> anyhow::Result<ToolResult> {
        let request: RulesRequest = serde_json::from_value(args)?;

        match request {
            RulesRequest::Create { name, trigger_type, conditions, action_type, action_params } => {
                let trigger = parse_trigger_type(&trigger_type)?;
                let conds = parse_conditions(conditions)?;
                let action = parse_action_type(&action_type)?;

                let rule = Rule {
                    id: uuid::Uuid::new_v4().to_string(),
                    name,
                    enabled: true,
                    trigger: RuleTrigger {
                        trigger_type: trigger,
                        conditions: conds,
                    },
                    action: Action {
                        action_type: action,
                        params: action_params,
                    },
                    created_at: chrono::Utc::now(),
                    updated_at: chrono::Utc::now(),
                };

                let rule_id = rule.id.clone();
                let rule_json = serde_json::to_string_pretty(&rule)?;

                self.engine.add_rule(&rule)?;

                Ok(ToolResult {
                    success: true,
                    output: format!("Created rule {}:\n{}", rule_id, rule_json),
                    error: None,
                })
            }

            RulesRequest::List => {
                let rules = self.engine.list_rules()?;
                let output = if rules.is_empty() {
                    "No rules configured.".to_string()
                } else {
                    rules
                        .iter()
                        .map(|r| format!("- {} [{}]: {} ({:?} -> {:?})", 
                            r.id, 
                            if r.enabled { "enabled" } else { "disabled" },
                            r.name,
                            r.trigger.trigger_type,
                            r.action.action_type
                        ))
                        .collect::<Vec<_>>()
                        .join("\n")
                };

                Ok(ToolResult {
                    success: true,
                    output,
                    error: None,
                })
            }

            RulesRequest::Get { id } => {
                match self.engine.get_rule(&id)? {
                    Some(rule) => {
                        let json = serde_json::to_string_pretty(&rule)?;
                        Ok(ToolResult {
                            success: true,
                            output: json,
                            error: None,
                        })
                    }
                    None => Ok(ToolResult {
                        success: false,
                        output: String::new(),
                        error: Some(format!("Rule {} not found", id)),
                    }),
                }
            }

            RulesRequest::Delete { id } => {
                match self.engine.delete_rule(&id)? {
                    true => Ok(ToolResult {
                        success: true,
                        output: format!("Deleted rule {}", id),
                        error: None,
                    }),
                    false => Ok(ToolResult {
                        success: false,
                        output: String::new(),
                        error: Some(format!("Rule {} not found", id)),
                    }),
                }
            }

            RulesRequest::Toggle { id, enabled } => {
                match self.engine.toggle_rule(&id, enabled)? {
                    true => Ok(ToolResult {
                        success: true,
                        output: format!("Rule {} is now {}", id, if enabled { "enabled" } else { "disabled" }),
                        error: None,
                    }),
                    false => Ok(ToolResult {
                        success: false,
                        output: String::new(),
                        error: Some(format!("Rule {} not found", id)),
                    }),
                }
            }
        }
    }
}

fn parse_trigger_type(s: &str) -> anyhow::Result<TriggerType> {
    match s.to_lowercase().as_str() {
        "incoming_call" => Ok(TriggerType::IncomingCall),
        "incoming_sms" => Ok(TriggerType::IncomingSms),
        "geofence" => Ok(TriggerType::Geofence),
        "schedule" => Ok(TriggerType::Schedule),
        "webhook" => Ok(TriggerType::Webhook),
        _ => anyhow::bail!("Unknown trigger type: {}", s),
    }
}

fn parse_action_type(s: &str) -> anyhow::Result<ActionType> {
    match s.to_lowercase().as_str() {
        "telegram_notify" => Ok(ActionType::TelegramNotify),
        "send_sms" => Ok(ActionType::SendSms),
        "place_call" => Ok(ActionType::PlaceCall),
        "post_notification" => Ok(ActionType::PostNotification),
        "agent_prompt" => Ok(ActionType::AgentPrompt),
        "http_request" => Ok(ActionType::HttpRequest),
        "memory_store" => Ok(ActionType::MemoryStore),
        _ => anyhow::bail!("Unknown action type: {}", s),
    }
}

fn parse_conditions(specs: Vec<ConditionSpec>) -> anyhow::Result<Vec<Condition>> {
    specs
        .into_iter()
        .map(|spec| {
            let cond_type = match spec.condition_type.to_lowercase().as_str() {
                "phone_number" => ConditionType::PhoneNumber,
                "sms_content" => ConditionType::SmsContent,
                "location" => ConditionType::Location,
                "time_of_day" => ConditionType::TimeOfDay,
                "day_of_week" => ConditionType::DayOfWeek,
                "custom_field" => ConditionType::CustomField,
                _ => anyhow::bail!("Unknown condition type: {}", spec.condition_type),
            };

            Ok(Condition {
                condition_type: cond_type,
                value: spec.value,
                negate: spec.negate,
            })
        })
        .collect()
}
