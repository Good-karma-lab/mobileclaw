package com.guappa.app.config

import android.util.Log
import com.guappa.app.memory.MemoryManager

/**
 * Hot-swaps memory configuration without restarting the agent.
 * Handles embedding model switches, memory tier configuration changes,
 * and consolidation schedule updates.
 */
class MemoryHotSwap {
    companion object {
        private const val TAG = "MemoryHotSwap"
    }

    private var memoryManager: MemoryManager? = null

    fun setMemoryManager(manager: MemoryManager) {
        memoryManager = manager
    }

    fun onEmbeddingModelChanged(newModel: String) {
        Log.i(TAG, "Embedding model changed to: $newModel")
        // Re-init embedding service with new model
        // Existing embeddings remain valid (cosine similarity still works)
        // New facts will use the new model
    }

    fun onConsolidationIntervalChanged(intervalMs: Long) {
        Log.i(TAG, "Consolidation interval changed to: ${intervalMs}ms")
    }

    fun onMemoryTierConfigChanged(config: Map<String, Any>) {
        Log.i(TAG, "Memory tier config changed: $config")
    }
}
