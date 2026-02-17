package com.zeroclaw.app

class RustAgentBridge(
    private val nativeBridge: NativeZeroClawBridge = NativeZeroClawBridge()
) {
    suspend fun sendMessage(input: String, config: AgentRuntimeConfig): String {
        return nativeBridge.chat(input, config).getOrThrow()
    }

    fun currentRuntimeConfig(): String {
        return "runtime.kind=android"
    }
}
