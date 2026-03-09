package com.guappa.app.tools

import android.content.Context
import android.content.pm.PackageManager

enum class ToolRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

sealed class PermissionResult {
    object Approved : PermissionResult()

    data class AndroidPermissionNeeded(
        val permission: String
    ) : PermissionResult()

    data class NeedsApproval(
        val description: String
    ) : PermissionResult()
}

class ToolPermissions {
    private val sessionGrants = mutableSetOf<String>()

    private val toolRiskLevels = mapOf(
        // LOW — auto-approved
        "calculator" to ToolRiskLevel.LOW,
        "get_battery" to ToolRiskLevel.LOW,
        "battery_info" to ToolRiskLevel.LOW,
        "system_info" to ToolRiskLevel.LOW,
        "storage_info" to ToolRiskLevel.LOW,
        "network_info" to ToolRiskLevel.LOW,
        "clipboard" to ToolRiskLevel.LOW,
        "set_alarm" to ToolRiskLevel.LOW,
        "set_timer" to ToolRiskLevel.LOW,
        "flashlight" to ToolRiskLevel.LOW,
        "vibrate" to ToolRiskLevel.LOW,
        "screen_rotation" to ToolRiskLevel.LOW,
        "translation" to ToolRiskLevel.LOW,
        "date_time" to ToolRiskLevel.LOW,
        "qr_code" to ToolRiskLevel.LOW,
        "barcode_scan" to ToolRiskLevel.LOW,
        "package_info" to ToolRiskLevel.LOW,
        "list_apps" to ToolRiskLevel.LOW,
        "app_info" to ToolRiskLevel.LOW,
        "rss_reader" to ToolRiskLevel.LOW,
        "ocr" to ToolRiskLevel.LOW,

        // MEDIUM — auto after first grant per session
        "web_search" to ToolRiskLevel.MEDIUM,
        "web_fetch" to ToolRiskLevel.MEDIUM,
        "web_scrape" to ToolRiskLevel.MEDIUM,
        "web_api" to ToolRiskLevel.MEDIUM,
        "open_browser" to ToolRiskLevel.MEDIUM,
        "share_text" to ToolRiskLevel.MEDIUM,
        "launch_app" to ToolRiskLevel.MEDIUM,
        "brightness" to ToolRiskLevel.MEDIUM,
        "volume" to ToolRiskLevel.MEDIUM,
        "read_file" to ToolRiskLevel.MEDIUM,
        "list_files" to ToolRiskLevel.MEDIUM,
        "file_info" to ToolRiskLevel.MEDIUM,
        "file_search" to ToolRiskLevel.MEDIUM,
        "document_picker" to ToolRiskLevel.MEDIUM,
        "media_gallery" to ToolRiskLevel.MEDIUM,
        "pdf_reader" to ToolRiskLevel.MEDIUM,
        "read_sms" to ToolRiskLevel.MEDIUM,
        "get_contacts" to ToolRiskLevel.MEDIUM,
        "read_call_log" to ToolRiskLevel.MEDIUM,
        "calendar" to ToolRiskLevel.MEDIUM,
        "location" to ToolRiskLevel.MEDIUM,
        "sensor" to ToolRiskLevel.MEDIUM,
        "nfc_read" to ToolRiskLevel.MEDIUM,
        "camera" to ToolRiskLevel.MEDIUM,
        "audio_record" to ToolRiskLevel.MEDIUM,
        "screenshot" to ToolRiskLevel.MEDIUM,
        "image_analyze" to ToolRiskLevel.MEDIUM,
        "summarize" to ToolRiskLevel.MEDIUM,
        "generate_image" to ToolRiskLevel.MEDIUM,
        "code_interpreter" to ToolRiskLevel.MEDIUM,
        "compose_email" to ToolRiskLevel.MEDIUM,
        "maps" to ToolRiskLevel.MEDIUM,
        "music_control" to ToolRiskLevel.MEDIUM,
        "settings_nav" to ToolRiskLevel.MEDIUM,
        "process_list" to ToolRiskLevel.MEDIUM,
        "reminder" to ToolRiskLevel.MEDIUM,
        "app_notifications" to ToolRiskLevel.MEDIUM,

        // HIGH — requires approval every time
        "send_sms" to ToolRiskLevel.HIGH,
        "place_call" to ToolRiskLevel.HIGH,
        "add_contact" to ToolRiskLevel.HIGH,
        "write_file" to ToolRiskLevel.HIGH,
        "delete_file" to ToolRiskLevel.HIGH,
        "download_file" to ToolRiskLevel.HIGH,
        "wifi" to ToolRiskLevel.HIGH,
        "bluetooth" to ToolRiskLevel.HIGH,
        "fire_intent" to ToolRiskLevel.HIGH,
        "geofence" to ToolRiskLevel.HIGH,
        "cron_job" to ToolRiskLevel.HIGH,
        "auto_reply" to ToolRiskLevel.HIGH,
        "social_share" to ToolRiskLevel.HIGH,
        "twitter_post" to ToolRiskLevel.HIGH,
        "instagram_share" to ToolRiskLevel.HIGH,
        "telegram_send" to ToolRiskLevel.HIGH,
        "whatsapp_send" to ToolRiskLevel.HIGH,

        // CRITICAL — always requires explicit approval
        "uninstall_app" to ToolRiskLevel.CRITICAL,
        "clear_app_data" to ToolRiskLevel.CRITICAL,
        "run_shell" to ToolRiskLevel.CRITICAL
    )

    fun getRiskLevel(toolName: String): ToolRiskLevel {
        return toolRiskLevels[toolName] ?: ToolRiskLevel.MEDIUM
    }

    fun checkPermission(tool: Tool, context: Context): PermissionResult {
        // First check Android runtime permissions
        for (perm in tool.requiredPermissions) {
            val fullPerm = "android.permission.$perm"
            if (context.checkSelfPermission(fullPerm) != PackageManager.PERMISSION_GRANTED) {
                return PermissionResult.AndroidPermissionNeeded(fullPerm)
            }
        }

        // Then check risk-based approval
        val riskLevel = getRiskLevel(tool.name)
        return when (riskLevel) {
            ToolRiskLevel.LOW -> PermissionResult.Approved

            ToolRiskLevel.MEDIUM -> {
                if (tool.name in sessionGrants) {
                    PermissionResult.Approved
                } else {
                    PermissionResult.NeedsApproval(
                        "Tool '${tool.name}' requires one-time session approval (medium risk)"
                    )
                }
            }

            ToolRiskLevel.HIGH -> {
                PermissionResult.NeedsApproval(
                    "Tool '${tool.name}' requires approval for each execution (high risk)"
                )
            }

            ToolRiskLevel.CRITICAL -> {
                PermissionResult.NeedsApproval(
                    "Tool '${tool.name}' is critical-risk and always requires explicit approval"
                )
            }
        }
    }

    fun grantSession(toolName: String) {
        sessionGrants.add(toolName)
    }

    fun revokeSession(toolName: String) {
        sessionGrants.remove(toolName)
    }

    fun clearSessionGrants() {
        sessionGrants.clear()
    }

    fun isSessionGranted(toolName: String): Boolean {
        return toolName in sessionGrants
    }
}
