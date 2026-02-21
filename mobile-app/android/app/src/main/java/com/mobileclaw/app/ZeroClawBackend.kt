package com.mobileclaw.app

import android.util.Log

/**
 * JNI bridge to ZeroClaw Rust backend
 *
 * This replaces the subprocess architecture with in-process execution
 * to bypass Android SELinux restrictions.
 *
 * The Rust library (libzeroclaw.so) is loaded automatically and runs
 * in the same process as the Android app.
 */
class ZeroClawBackend {
    companion object {
        private const val TAG = "ZeroClawBackend"

        init {
            try {
                System.loadLibrary("zeroclaw")
                Log.i(TAG, "ZeroClaw native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load ZeroClaw native library", e)
                throw RuntimeException("ZeroClaw native library not found", e)
            }
        }

        /**
         * Start the ZeroClaw agent runtime
         *
         * @param configPath Path to .zeroclaw directory (workspace root)
         * @param apiKey Provider API key (empty string to use config file value)
         * @param model Default model name (empty string to use config file value)
         * @param telegramToken Telegram bot token (empty string to disable)
         * @return Handle ID for subsequent calls, or 0 on failure
         */
        @JvmStatic
        external fun startAgent(configPath: String, apiKey: String, model: String, telegramToken: String): Long

        /**
         * Process a message through the agent runtime
         *
         * This runs the full agent loop with tools, memory, and multi-step reasoning.
         *
         * @param handleId Handle from startAgent()
         * @param message User message to process
         * @return Agent response string
         */
        @JvmStatic
        external fun processMessage(handleId: Long, message: String): String

        /**
         * Check if agent is healthy
         *
         * @param handleId Handle from startAgent()
         * @return true if healthy, false otherwise
         */
        @JvmStatic
        external fun isHealthy(handleId: Long): Boolean

        /**
         * Stop the agent and release resources
         *
         * @param handleId Handle from startAgent()
         */
        @JvmStatic
        external fun stopAgent(handleId: Long)

        /**
         * Get the gateway URL for this agent instance
         *
         * @param handleId Handle from startAgent()
         * @return Gateway URL (e.g., "http://127.0.0.1:8000")
         */
        @JvmStatic
        external fun getGatewayUrl(handleId: Long): String

        /**
         * Execute a tool directly without going through agent loop
         *
         * Useful for system operations like camera, sensors, etc.
         *
         * @param handleId Handle from startAgent()
         * @param toolName Tool name (e.g., "shell", "file_read", "camera")
         * @param paramsJson Tool parameters as JSON string
         * @return Result as JSON string: {"success": bool, "result": any, "error": string}
         */
        @JvmStatic
        external fun executeTool(handleId: Long, toolName: String, paramsJson: String): String
    }
}
