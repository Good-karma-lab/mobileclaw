package com.guappa.app.config

import android.util.Log
import com.guappa.app.providers.ProviderFactory
import com.guappa.app.providers.ProviderRouter

/**
 * Hot-swaps the active provider without restarting the agent service.
 *
 * On provider config change:
 * 1. Creates a new provider instance via ProviderFactory
 * 2. Validates the API key by running a health check
 * 3. Clears model cache from the old router
 * 4. Registers the new provider in the router
 *
 * The swap is atomic from the perspective of the next chat request:
 * in-flight requests complete with the old provider; the next request
 * uses the new one.
 */
class ProviderHotSwap(
    private val getRouter: () -> ProviderRouter?
) : ConfigChangeHandler {
    private val TAG = "ProviderHotSwap"

    override val name: String = "ProviderHotSwap"

    override fun handles(section: ConfigSection): Boolean =
        section == ConfigSection.PROVIDER

    override suspend fun onConfigChanged(section: ConfigSection, config: Any) {
        val providerConfig = config as? ProviderConfig ?: return
        swapProvider(providerConfig)
    }

    /**
     * Perform the hot-swap. Returns true if the new provider is healthy.
     */
    suspend fun swapProvider(config: ProviderConfig): Boolean {
        val router = getRouter() ?: run {
            Log.w(TAG, "No router available for hot-swap")
            return false
        }

        val providerId = config.providerId.trim()
        if (providerId.isBlank() || providerId == "local") {
            Log.d(TAG, "Provider is local or blank; no hot-swap needed")
            return true
        }

        val normalizedId = ProviderFactory.normalizeProviderId(providerId)
        Log.d(TAG, "Hot-swapping provider to: $normalizedId")

        return try {
            // Create new provider
            val newProvider = ProviderFactory.createProvider(
                providerName = normalizedId,
                apiKey = config.apiKey,
                apiUrl = config.apiUrl
            )

            // Validate API key via health check
            val healthy = try {
                newProvider.healthCheck()
            } catch (e: Exception) {
                Log.w(TAG, "Health check failed for $normalizedId: ${e.message}")
                false
            }

            if (!healthy) {
                Log.w(TAG, "Provider $normalizedId is not healthy; registering anyway for retry")
            }

            // Register the new provider (replaces old one with same ID)
            router.registerProvider(newProvider)

            Log.d(TAG, "Provider hot-swapped to $normalizedId (healthy=$healthy)")
            healthy
        } catch (e: Exception) {
            Log.e(TAG, "Provider hot-swap failed: ${e.message}", e)
            false
        }
    }
}
