//! Rule storage using SQLite
//!
//! Uses a functional pattern similar to cron store - opens connection per operation
//! to avoid thread-safety issues with rusqlite::Connection.

use super::schema::{Action, ActionType, Condition, Rule, RuleTrigger, TriggerType};
use anyhow::Result;
use chrono::{DateTime, Utc};
use rusqlite::{params, Connection, OptionalExtension};
use std::path::Path;

/// Initialize the rules database and return a connection
fn with_connection<T, P: AsRef<Path>>(
    path: P,
    f: impl FnOnce(&Connection) -> Result<T>,
) -> Result<T> {
    let path = path.as_ref();

    // Ensure parent directory exists
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)?;
    }

    let conn = Connection::open(path)?;

    conn.execute_batch(
        r#"
        CREATE TABLE IF NOT EXISTS rules (
            id TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            enabled INTEGER NOT NULL DEFAULT 1,
            trigger_type TEXT NOT NULL,
            trigger_conditions TEXT NOT NULL,
            action_type TEXT NOT NULL,
            action_params TEXT NOT NULL,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_rules_enabled ON rules(enabled);
        "#,
    )?;

    f(&conn)
}

/// Add a new rule
pub fn add_rule<P: AsRef<Path>>(path: P, rule: &Rule) -> Result<()> {
    with_connection(path, |conn| {
        conn.execute(
            r#"
            INSERT INTO rules (id, name, enabled, trigger_type, trigger_conditions, action_type, action_params, created_at, updated_at)
            VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)
            "#,
            params![
                rule.id,
                rule.name,
                rule.enabled as i32,
                serde_json::to_string(&rule.trigger.trigger_type)?,
                serde_json::to_string(&rule.trigger.conditions)?,
                serde_json::to_string(&rule.action.action_type)?,
                serde_json::to_string(&rule.action.params)?,
                rule.created_at.to_rfc3339(),
                rule.updated_at.to_rfc3339(),
            ],
        )?;
        Ok(())
    })
}

/// Get a rule by ID
pub fn get_rule<P: AsRef<Path>>(path: P, id: &str) -> Result<Option<Rule>> {
    with_connection(path, |conn| {
        let mut stmt = conn.prepare(
            r#"
            SELECT id, name, enabled, trigger_type, trigger_conditions, action_type, action_params, created_at, updated_at
            FROM rules WHERE id = ?1
            "#,
        )?;

        let rule = stmt
            .query_row(params![id], |row| row_to_rule(row))
            .optional()?;
        Ok(rule)
    })
}

/// List all rules
pub fn list_rules<P: AsRef<Path>>(path: P) -> Result<Vec<Rule>> {
    with_connection(path, |conn| {
        let mut stmt = conn.prepare(
            r#"
            SELECT id, name, enabled, trigger_type, trigger_conditions, action_type, action_params, created_at, updated_at
            FROM rules ORDER BY created_at DESC
            "#,
        )?;

        let rules = stmt
            .query_map([], |row| row_to_rule(row))?
            .collect::<Result<Vec<_>, _>>()?;
        Ok(rules)
    })
}

/// List enabled rules
pub fn list_enabled_rules<P: AsRef<Path>>(path: P) -> Result<Vec<Rule>> {
    with_connection(path, |conn| {
        let mut stmt = conn.prepare(
            r#"
            SELECT id, name, enabled, trigger_type, trigger_conditions, action_type, action_params, created_at, updated_at
            FROM rules WHERE enabled = 1 ORDER BY created_at DESC
            "#,
        )?;

        let rules = stmt
            .query_map([], |row| row_to_rule(row))?
            .collect::<Result<Vec<_>, _>>()?;
        Ok(rules)
    })
}

/// Update a rule
pub fn update_rule<P: AsRef<Path>>(path: P, rule: &Rule) -> Result<()> {
    with_connection(path, |conn| {
        conn.execute(
            r#"
            UPDATE rules SET
                name = ?2,
                enabled = ?3,
                trigger_type = ?4,
                trigger_conditions = ?5,
                action_type = ?6,
                action_params = ?7,
                updated_at = ?8
            WHERE id = ?1
            "#,
            params![
                rule.id,
                rule.name,
                rule.enabled as i32,
                serde_json::to_string(&rule.trigger.trigger_type)?,
                serde_json::to_string(&rule.trigger.conditions)?,
                serde_json::to_string(&rule.action.action_type)?,
                serde_json::to_string(&rule.action.params)?,
                rule.updated_at.to_rfc3339(),
            ],
        )?;
        Ok(())
    })
}

/// Delete a rule
pub fn delete_rule<P: AsRef<Path>>(path: P, id: &str) -> Result<bool> {
    with_connection(path, |conn| {
        let rows = conn.execute("DELETE FROM rules WHERE id = ?1", params![id])?;
        Ok(rows > 0)
    })
}

/// Toggle rule enabled state
pub fn toggle_rule<P: AsRef<Path>>(path: P, id: &str, enabled: bool) -> Result<bool> {
    with_connection(path, |conn| {
        let rows = conn.execute(
            "UPDATE rules SET enabled = ?2, updated_at = ?3 WHERE id = ?1",
            params![id, enabled as i32, Utc::now().to_rfc3339()],
        )?;
        Ok(rows > 0)
    })
}

fn row_to_rule(row: &rusqlite::Row) -> Result<Rule, rusqlite::Error> {
    let id: String = row.get(0)?;
    let name: String = row.get(1)?;
    let enabled: i32 = row.get(2)?;
    let trigger_type_str: String = row.get(3)?;
    let conditions_str: String = row.get(4)?;
    let action_type_str: String = row.get(5)?;
    let action_params_str: String = row.get(6)?;
    let created_at_str: String = row.get(7)?;
    let updated_at_str: String = row.get(8)?;

    let trigger_type: TriggerType =
        serde_json::from_str(&trigger_type_str).map_err(|_| rusqlite::Error::InvalidQuery)?;
    let conditions: Vec<Condition> =
        serde_json::from_str(&conditions_str).map_err(|_| rusqlite::Error::InvalidQuery)?;
    let action_type: ActionType =
        serde_json::from_str(&action_type_str).map_err(|_| rusqlite::Error::InvalidQuery)?;
    let action_params: serde_json::Value =
        serde_json::from_str(&action_params_str).map_err(|_| rusqlite::Error::InvalidQuery)?;

    let created_at = DateTime::parse_from_rfc3339(&created_at_str)
        .map(|dt| dt.with_timezone(&Utc))
        .map_err(|_| rusqlite::Error::InvalidQuery)?;
    let updated_at = DateTime::parse_from_rfc3339(&updated_at_str)
        .map(|dt| dt.with_timezone(&Utc))
        .map_err(|_| rusqlite::Error::InvalidQuery)?;

    Ok(Rule {
        id,
        name,
        enabled: enabled != 0,
        trigger: RuleTrigger {
            trigger_type,
            conditions,
        },
        action: Action {
            action_type,
            params: action_params,
        },
        created_at,
        updated_at,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::rules::ConditionType;
    use tempfile::TempDir;

    fn test_rule() -> Rule {
        Rule {
            id: "test-rule-1".to_string(),
            name: "Test rule".to_string(),
            enabled: true,
            trigger: RuleTrigger {
                trigger_type: TriggerType::IncomingCall,
                conditions: vec![Condition {
                    condition_type: ConditionType::PhoneNumber,
                    value: "+1234567890".to_string(),
                    negate: false,
                }],
            },
            action: Action {
                action_type: ActionType::TelegramNotify,
                params: serde_json::json!({"message": "Call from {{phone_number}}"}),
            },
            created_at: Utc::now(),
            updated_at: Utc::now(),
        }
    }

    #[test]
    fn rule_add_and_get() {
        let tmp = TempDir::new().unwrap();
        let db_path = tmp.path().join("rules.db");

        let rule = test_rule();
        add_rule(&db_path, &rule).unwrap();

        let retrieved = get_rule(&db_path, "test-rule-1").unwrap();
        assert!(retrieved.is_some());
        let retrieved = retrieved.unwrap();
        assert_eq!(retrieved.name, "Test rule");
        assert_eq!(retrieved.trigger.trigger_type, TriggerType::IncomingCall);
        assert_eq!(retrieved.action.action_type, ActionType::TelegramNotify);
    }

    #[test]
    fn rule_list_all() {
        let tmp = TempDir::new().unwrap();
        let db_path = tmp.path().join("rules.db");

        let mut rule1 = test_rule();
        rule1.id = "rule-1".to_string();
        rule1.name = "First rule".to_string();

        let mut rule2 = test_rule();
        rule2.id = "rule-2".to_string();
        rule2.name = "Second rule".to_string();

        add_rule(&db_path, &rule1).unwrap();
        add_rule(&db_path, &rule2).unwrap();

        let rules = list_rules(&db_path).unwrap();
        assert_eq!(rules.len(), 2);
    }

    #[test]
    fn rule_list_enabled() {
        let tmp = TempDir::new().unwrap();
        let db_path = tmp.path().join("rules.db");

        let mut rule1 = test_rule();
        rule1.id = "rule-1".to_string();
        rule1.enabled = true;

        let mut rule2 = test_rule();
        rule2.id = "rule-2".to_string();
        rule2.enabled = false;

        add_rule(&db_path, &rule1).unwrap();
        add_rule(&db_path, &rule2).unwrap();

        let enabled = list_enabled_rules(&db_path).unwrap();
        assert_eq!(enabled.len(), 1);
        assert_eq!(enabled[0].id, "rule-1");
    }

    #[test]
    fn rule_delete() {
        let tmp = TempDir::new().unwrap();
        let db_path = tmp.path().join("rules.db");

        let rule = test_rule();
        add_rule(&db_path, &rule).unwrap();

        let deleted = delete_rule(&db_path, "test-rule-1").unwrap();
        assert!(deleted);

        let retrieved = get_rule(&db_path, "test-rule-1").unwrap();
        assert!(retrieved.is_none());

        let deleted_again = delete_rule(&db_path, "test-rule-1").unwrap();
        assert!(!deleted_again);
    }

    #[test]
    fn rule_toggle() {
        let tmp = TempDir::new().unwrap();
        let db_path = tmp.path().join("rules.db");

        let rule = test_rule();
        add_rule(&db_path, &rule).unwrap();

        let toggled = toggle_rule(&db_path, "test-rule-1", false).unwrap();
        assert!(toggled);

        let retrieved = get_rule(&db_path, "test-rule-1").unwrap().unwrap();
        assert!(!retrieved.enabled);

        let enabled_rules = list_enabled_rules(&db_path).unwrap();
        assert_eq!(enabled_rules.len(), 0);
    }
}
