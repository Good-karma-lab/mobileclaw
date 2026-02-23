//! Rules Engine - Evaluates rules against events

use super::schema::{Condition, ConditionType, Event, Rule, RuleTrigger};
use super::storage;
use anyhow::Result;
use std::path::PathBuf;

/// Rules engine that evaluates triggers and executes actions
pub struct RulesEngine {
    db_path: PathBuf,
}

impl RulesEngine {
    pub fn new(db_path: PathBuf) -> Self {
        Self { db_path }
    }

    /// Process an event and return matching rules
    pub fn evaluate(&self, event: &Event) -> Result<Vec<Rule>> {
        let rules = storage::list_enabled_rules(&self.db_path)?;
        
        let matching: Vec<Rule> = rules
            .into_iter()
            .filter(|rule| rule.trigger.trigger_type == event.event_type)
            .filter(|rule| self.matches_conditions(&rule.trigger, event))
            .collect();
        
        Ok(matching)
    }

    /// Check if all conditions match for a rule trigger
    fn matches_conditions(&self, trigger: &RuleTrigger, event: &Event) -> bool {
        if trigger.conditions.is_empty() {
            return true;
        }

        trigger.conditions.iter().all(|condition| self.matches_condition(condition, event))
    }

    /// Check if a single condition matches
    fn matches_condition(&self, condition: &Condition, event: &Event) -> bool {
        let value: Option<String> = match condition.condition_type {
            ConditionType::PhoneNumber => event.get("phone_number").map(|s| s.to_string()),
            ConditionType::SmsContent => event.get("content").map(|s| s.to_string()),
            ConditionType::Location => event.get("location").map(|s| s.to_string()),
            ConditionType::TimeOfDay => {
                let now = chrono::Utc::now();
                Some(now.format("%H:%M").to_string())
            }
            ConditionType::DayOfWeek => {
                let now = chrono::Utc::now();
                Some(now.format("%A").to_string().to_lowercase())
            }
            ConditionType::CustomField => {
                if let Some(field) = condition.value.strip_prefix("field:") {
                    event.get(field).map(|s| s.to_string())
                } else {
                    None
                }
            }
        };

        let Some(value) = value else {
            return false;
        };

        // Check for wildcard match
        if condition.value == "*" {
            return !condition.negate;
        }

        // Check for exact match
        if value == condition.value {
            return !condition.negate;
        }

        // Check for prefix match (ends with *)
        if condition.value.ends_with('*') {
            let prefix = &condition.value[..condition.value.len() - 1];
            let matches = value.starts_with(prefix);
            return condition.negate ^ matches;
        }

        // Check for suffix match (starts with *)
        if condition.value.starts_with('*') {
            let suffix = &condition.value[1..];
            let matches = value.ends_with(suffix);
            return condition.negate ^ matches;
        }

        // Check for contains match
        if value.to_lowercase().contains(&condition.value.to_lowercase()) {
            return !condition.negate;
        }

        condition.negate
    }

    /// Add a new rule
    pub fn add_rule(&self, rule: &Rule) -> Result<()> {
        storage::add_rule(&self.db_path, rule)
    }

    /// Get a rule by ID
    pub fn get_rule(&self, id: &str) -> Result<Option<Rule>> {
        storage::get_rule(&self.db_path, id)
    }

    /// List all rules
    pub fn list_rules(&self) -> Result<Vec<Rule>> {
        storage::list_rules(&self.db_path)
    }

    /// Delete a rule
    pub fn delete_rule(&self, id: &str) -> Result<bool> {
        storage::delete_rule(&self.db_path, id)
    }

    /// Toggle rule enabled state
    pub fn toggle_rule(&self, id: &str, enabled: bool) -> Result<bool> {
        storage::toggle_rule(&self.db_path, id, enabled)
    }
}
