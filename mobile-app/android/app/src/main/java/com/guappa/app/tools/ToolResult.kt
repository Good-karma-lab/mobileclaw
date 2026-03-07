package com.guappa.app.tools

import org.json.JSONObject

sealed class ToolResult {
    data class Success(
        val content: String,
        val data: JSONObject? = null,
        val attachments: List<String>? = null
    ) : ToolResult()

    data class Error(
        val message: String,
        val code: String = "TOOL_ERROR",
        val retryable: Boolean = false
    ) : ToolResult()

    data class NeedsApproval(
        val description: String,
        val approvalType: String = "user_confirm"
    ) : ToolResult()

    fun toJSON(): JSONObject {
        val json = JSONObject()
        when (this) {
            is Success -> {
                json.put("status", "success")
                json.put("content", content)
                if (data != null) json.put("data", data)
            }
            is Error -> {
                json.put("status", "error")
                json.put("message", message)
                json.put("code", code)
                json.put("retryable", retryable)
            }
            is NeedsApproval -> {
                json.put("status", "needs_approval")
                json.put("description", description)
                json.put("approval_type", approvalType)
            }
        }
        return json
    }
}
