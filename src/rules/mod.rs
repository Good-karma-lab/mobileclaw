//! Trigger-Action Rules Engine
//!
//! Enables automation rules like "If incoming call from +1234567890, send Telegram notification"
//!
//! Rule structure:
//! - Trigger: Event type + conditions (e.g., incoming_call with phone_number match)
//! - Action: Tool to execute with parameters (e.g., telegram_notify with message)

mod engine;
mod schema;
pub mod storage;

pub use engine::RulesEngine;
pub use schema::{
    Action, ActionType, Condition, ConditionType, Event, Rule, RuleTrigger, TriggerType,
};
