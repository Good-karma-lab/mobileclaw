package com.guappa.app.tools

import android.content.Context
import com.guappa.app.tools.impl.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class ToolRegistry {
    private val tools = ConcurrentHashMap<String, Tool>()
    private val disabledTools = ConcurrentHashMap.newKeySet<String>()

    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    fun getTool(name: String): Tool? {
        if (name in disabledTools) return null
        return tools[name]
    }

    fun getAvailableTools(context: Context): List<Tool> {
        return tools.values.filter { it.name !in disabledTools && it.isAvailable(context) }
    }

    fun getToolSchemas(context: Context): List<JSONObject> {
        return getAvailableTools(context).map { tool ->
            val schema = JSONObject()
            schema.put("type", "function")
            val function = JSONObject()
            function.put("name", tool.name)
            function.put("description", tool.description)
            function.put("parameters", tool.parametersSchema)
            schema.put("function", function)
            schema
        }
    }

    /** Set a tool's enabled state. Disabled tools are excluded from schemas and execution. */
    fun setToolEnabled(name: String, enabled: Boolean) {
        if (enabled) {
            disabledTools.remove(name)
        } else {
            disabledTools.add(name)
        }
    }

    /** Get all registered tool names (enabled and disabled). */
    fun getAllToolNames(): List<String> = tools.keys().toList()

    /** Get only enabled tool names. */
    fun getEnabledToolNames(): List<String> = tools.keys().toList().filter { it !in disabledTools }

    fun registerCoreTools() {
        register(SetAlarmTool())
        register(SendSmsTool())
        register(ReadSmsTool())
        register(PlaceCallTool())
        register(GetBatteryTool())
        register(GetContactsTool())
        register(LaunchAppTool())
        register(OpenBrowserTool())
        register(SetTimerTool())
        register(ShareTextTool())
        register(WebFetchTool())
        register(WebSearchTool())
        register(CalculatorTool())
        register(TranslationTool())
        register(ImageAnalyzeTool())

        // File management tools
        register(ReadFileTool())
        register(WriteFileTool())
        register(ListFilesTool())
        register(DeleteFileTool())
        register(FileInfoTool())
        register(DownloadFileTool())
        register(FileSearchTool())
        register(DocumentPickerTool())
        register(MediaGalleryTool())
        register(PdfReaderTool())

        // AI/LLM tools
        register(SummarizeTool())
        register(GenerateImageTool())
        register(OCRTool())
        register(CodeInterpreterTool())
        register(QRCodeTool())
        register(BarcodeScanTool())

        // System tools
        register(SystemInfoTool())
        register(StorageInfoTool())
        register(NetworkInfoTool())
        register(ProcessListTool())
        register(ShellTool())
        register(PackageInfoTool())
        register(DateTimeTool())

        // Device control tools
        register(BrightnessTool())
        register(VolumeTool())
        register(WifiTool())
        register(BluetoothTool())
        register(FlashlightTool())
        register(VibrateTool())
        register(ScreenshotTool())
        register(ClipboardTool())
        register(BatteryInfoTool())
        register(ScreenRotationTool())

        // Device hardware tools
        register(CameraTool())
        register(AudioRecordTool())
        register(SensorTool())
        register(NFCReadTool())

        // Phase 3: App Control tools
        register(ListAppsTool())
        register(AppInfoTool())
        register(UninstallAppTool())
        register(AppNotificationsTool())
        register(ClearAppDataTool())

        // Phase 3: Intent & Navigation tools
        register(IntentTool())
        register(EmailComposeTool())
        register(MapsTool())
        register(MusicControlTool())
        register(SettingsNavTool())
        register(CalendarTool())

        // Phase 3: Social tools
        register(AddContactTool())
        register(ReadCallLogTool())
        register(TwitterPostTool())
        register(InstagramShareTool())
        register(TelegramSendTool())
        register(WhatsAppSendTool())
        register(SocialShareTool())

        // Phase 3: Web tools
        register(WebScrapeTool())
        register(RssReaderTool())
        register(WebApiTool())

        // Phase 3: Automation tools
        register(CronJobTool())
        register(ReminderTool())
        register(AutoReplyTool())
        register(LocationTool())
        register(GeofenceTool())
    }
}
