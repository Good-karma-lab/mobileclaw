package com.guappa.app.tools

import android.content.Context
import org.json.JSONObject

interface Tool {
    val name: String
    val description: String
    val parametersSchema: JSONObject
    val requiredPermissions: List<String>

    fun isAvailable(context: Context): Boolean {
        return requiredPermissions.all { perm ->
            context.checkSelfPermission("android.permission.$perm") ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    suspend fun execute(params: JSONObject, context: Context): ToolResult
}
