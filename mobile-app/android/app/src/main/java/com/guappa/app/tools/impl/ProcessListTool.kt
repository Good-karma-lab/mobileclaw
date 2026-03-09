package com.guappa.app.tools.impl

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONArray
import org.json.JSONObject

class ProcessListTool : Tool {
    override val name = "process_list"
    override val description = "List running processes and apps on the device with memory usage information."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "limit": {
                    "type": "integer",
                    "description": "Maximum number of processes to return (default: 20)"
                }
            },
            "required": []
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val limit = params.optInt("limit", 20).coerceIn(1, 100)

        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

            // Get running app processes
            val runningProcesses = activityManager.runningAppProcesses ?: emptyList()

            val processesArray = JSONArray()
            val summary = StringBuilder()
            summary.appendLine("Running Processes (${runningProcesses.size} total):")
            summary.appendLine()

            val sorted = runningProcesses.sortedByDescending { it.importance }
            for ((index, process) in sorted.take(limit).withIndex()) {
                val importanceLabel = when (process.importance) {
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "Foreground"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "Foreground Service"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "Visible"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "Service"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "Cached"
                    else -> "Background"
                }

                // Get memory info for this process
                val memInfo = try {
                    val pids = intArrayOf(process.pid)
                    val debugInfos = activityManager.getProcessMemoryInfo(pids)
                    if (debugInfos.isNotEmpty()) debugInfos[0].totalPss else 0
                } catch (_: Exception) {
                    0
                }

                val memMb = memInfo / 1024.0

                val obj = JSONObject().apply {
                    put("name", process.processName)
                    put("pid", process.pid)
                    put("uid", process.uid)
                    put("importance", importanceLabel)
                    put("importance_value", process.importance)
                    put("memory_pss_kb", memInfo)
                    put("memory_mb", "${"%.1f".format(memMb)}")
                }
                processesArray.put(obj)

                summary.appendLine("${index + 1}. ${process.processName}")
                summary.appendLine("   PID: ${process.pid} | ${importanceLabel} | ${"%.1f".format(memMb)} MB")
            }

            if (runningProcesses.size > limit) {
                summary.appendLine("\n...and ${runningProcesses.size - limit} more processes")
            }

            val data = JSONObject().apply {
                put("total_count", runningProcesses.size)
                put("returned_count", sorted.take(limit).size)
                put("processes", processesArray)
            }

            ToolResult.Success(content = summary.toString().trim(), data = data)
        } catch (e: Exception) {
            ToolResult.Error("Failed to list processes: ${e.message}", "EXECUTION_ERROR")
        }
    }
}
