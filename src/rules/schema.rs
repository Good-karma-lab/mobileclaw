//! Rule schema definitions

use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

/// A single automation rule
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Rule {
    /// Unique identifier
    pub id: String,
    /// Human-readable name
    pub name: String,
    /// Whether the rule is active
    pub enabled: bool,
    /// Trigger conditions
    pub trigger: RuleTrigger,
    /// Action to execute
    pub action: Action,
    /// Creation timestamp
    pub created_at: DateTime<Utc>,
    /// Last modification timestamp
    pub updated_at: DateTime<Utc>,
}

/// Trigger definition
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RuleTrigger {
    /// Type of trigger event
    pub trigger_type: TriggerType,
    /// Conditions that must match
    pub conditions: Vec<Condition>,
}

/// Types of trigger events
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum TriggerType {
    /// Incoming phone call
    IncomingCall,
    /// Incoming SMS message
    IncomingSms,
    /// Geofence entry/exit
    Geofence,
    /// Scheduled time (cron-like)
    Schedule,
    /// Webhook received
    Webhook,
}

/// A condition to match
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Condition {
    /// Type of condition
    pub condition_type: ConditionType,
    /// Value to match (exact or pattern)
    pub value: String,
    /// Whether to negate the match
    #[serde(default)]
    pub negate: bool,
}

/// Types of conditions
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum ConditionType {
    /// Phone number (exact or pattern with wildcards)
    PhoneNumber,
    /// SMS message content contains
    SmsContent,
    /// Location name or coordinates
    Location,
    /// Time of day (HH:MM format)
    TimeOfDay,
    /// Day of week (monday, tuesday, etc.)
    DayOfWeek,
    /// Custom field match
    CustomField,
}

/// Action to execute when trigger matches
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Action {
    /// Type of action
    pub action_type: ActionType,
    /// Parameters for the action
    pub params: serde_json::Value,
}

/// Types of actions
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "snake_case")]
pub enum ActionType {
    /// Send Telegram notification
    TelegramNotify,
    /// Send SMS
    SendSms,
    /// Make a phone call
    PlaceCall,
    /// Post notification
    PostNotification,
    /// Execute agent prompt
    AgentPrompt,
    /// HTTP request
    HttpRequest,
    /// Store in memory
    MemoryStore,
}

/// Event data passed to the rules engine for evaluation
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Event {
    /// Type of event
    pub event_type: TriggerType,
    /// Event timestamp
    pub timestamp: DateTime<Utc>,
    /// Event data fields
    pub data: serde_json::Value,
}

impl Event {
    /// Create a new incoming call event
    pub fn incoming_call(phone_number: &str) -> Self {
        Self {
            event_type: TriggerType::IncomingCall,
            timestamp: Utc::now(),
            data: serde_json::json!({
                "phone_number": phone_number,
            }),
        }
    }

    /// Create a new incoming SMS event
    pub fn incoming_sms(phone_number: &str, content: &str) -> Self {
        Self {
            event_type: TriggerType::IncomingSms,
            timestamp: Utc::now(),
            data: serde_json::json!({
                "phone_number": phone_number,
                "content": content,
            }),
        }
    }

    /// Get a field from the event data
    pub fn get(&self, field: &str) -> Option<&str> {
        self.data.get(field).and_then(|v| v.as_str())
    }
}
