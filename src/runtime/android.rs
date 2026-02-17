use super::traits::RuntimeAdapter;
use crate::config::AndroidRuntimeConfig;
use std::path::{Path, PathBuf};

/// Android runtime adapter for app-hosted deployments.
#[derive(Debug, Clone)]
pub struct AndroidRuntime {
    config: AndroidRuntimeConfig,
}

impl AndroidRuntime {
    pub fn new(config: AndroidRuntimeConfig) -> Self {
        Self { config }
    }

    fn default_storage_path() -> PathBuf {
        PathBuf::from("/data/user/0/com.zeroclaw.app/files/zeroclaw")
    }
}

impl RuntimeAdapter for AndroidRuntime {
    fn name(&self) -> &str {
        "android"
    }

    fn has_shell_access(&self) -> bool {
        false
    }

    fn has_filesystem_access(&self) -> bool {
        true
    }

    fn storage_path(&self) -> PathBuf {
        self.config
            .app_data_dir
            .as_ref()
            .map_or_else(Self::default_storage_path, PathBuf::from)
    }

    fn supports_long_running(&self) -> bool {
        self.config.use_foreground_service
    }

    fn build_shell_command(
        &self,
        _command: &str,
        _workspace_dir: &Path,
    ) -> anyhow::Result<tokio::process::Command> {
        anyhow::bail!(
            "Android runtime does not support shell command execution. Use android_device tool actions instead."
        )
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn android_runtime_reports_capabilities() {
        let runtime = AndroidRuntime::new(AndroidRuntimeConfig::default());
        assert_eq!(runtime.name(), "android");
        assert!(!runtime.has_shell_access());
        assert!(runtime.has_filesystem_access());
    }

    #[test]
    fn android_runtime_default_storage_path() {
        let runtime = AndroidRuntime::new(AndroidRuntimeConfig::default());
        let storage = runtime.storage_path();
        assert!(storage.to_string_lossy().contains("zeroclaw"));
    }
}
