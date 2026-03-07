package com.guappa.app.tools.impl

import android.content.Context
import com.guappa.app.tools.Tool
import com.guappa.app.tools.ToolResult
import org.json.JSONObject

class TranslationTool : Tool {
    override val name = "translate"
    override val description = "Translate text between languages. Returns instructions for the orchestrator to use the LLM for translation."
    override val requiredPermissions = listOf<String>()
    override val parametersSchema = JSONObject("""
        {
            "type": "object",
            "properties": {
                "text": {
                    "type": "string",
                    "description": "The text to translate"
                },
                "target_language": {
                    "type": "string",
                    "description": "The target language (e.g. 'Spanish', 'French', 'Japanese')"
                },
                "source_language": {
                    "type": "string",
                    "description": "The source language (auto-detected if not specified)"
                }
            },
            "required": ["text", "target_language"]
        }
    """.trimIndent())

    override suspend fun execute(params: JSONObject, context: Context): ToolResult {
        val text = params.optString("text", "")
        val targetLanguage = params.optString("target_language", "")
        val sourceLanguage = params.optString("source_language", "")

        if (text.isEmpty()) {
            return ToolResult.Error("Text to translate is required.", "INVALID_PARAMS")
        }
        if (targetLanguage.isEmpty()) {
            return ToolResult.Error("Target language is required.", "INVALID_PARAMS")
        }

        val sourceInfo = if (sourceLanguage.isNotEmpty()) " from $sourceLanguage" else ""
        val instruction = "Please translate the following text$sourceInfo to $targetLanguage:\n\n$text"

        val data = JSONObject().apply {
            put("text", text)
            put("target_language", targetLanguage)
            if (sourceLanguage.isNotEmpty()) put("source_language", sourceLanguage)
            put("type", "llm_delegation")
            put("prompt", instruction)
        }

        return ToolResult.Success(
            content = instruction,
            data = data
        )
    }
}
