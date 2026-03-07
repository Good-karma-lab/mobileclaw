package com.guappa.app.swarm

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Manages the agent's cryptographic identity for the World Wide Swarm.
 * Keys are stored in Android Keystore — never exported or logged.
 */
class SwarmIdentity(private val context: Context) {
    private val TAG = "SwarmIdentity"
    private val KEYSTORE_ALIAS = "guappa_swarm_identity"
    private val PREFS_NAME = "guappa_swarm"
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    val hasIdentity: Boolean
        get() = keyStore.containsAlias(KEYSTORE_ALIAS)

    val publicKeyBase64: String?
        get() {
            if (!hasIdentity) return null
            val cert = keyStore.getCertificate(KEYSTORE_ALIAS)
            return Base64.encodeToString(cert.publicKey.encoded, Base64.NO_WRAP)
        }

    val peerId: String?
        get() {
            val pk = publicKeyBase64 ?: return null
            // Short peer ID: first 16 chars of base64-encoded public key
            return "did:swarm:${pk.take(16)}"
        }

    val displayName: String
        get() {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString("display_name", "GUAPPA Agent") ?: "GUAPPA Agent"
        }

    fun setDisplayName(name: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString("display_name", name).apply()
    }

    fun generateIdentity(): Boolean {
        return try {
            if (hasIdentity) {
                Log.d(TAG, "Identity already exists")
                return true
            }
            // Use EC with secp256r1 — Android Keystore supports this natively.
            // Ed25519 requires API 33+; EC provides equivalent security.
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                "AndroidKeyStore"
            )
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)
                .build()

            keyPairGenerator.initialize(spec)
            keyPairGenerator.generateKeyPair()
            Log.d(TAG, "Generated swarm identity: $peerId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate identity", e)
            false
        }
    }

    fun sign(data: ByteArray): ByteArray? {
        return try {
            val privateKey = keyStore.getKey(KEYSTORE_ALIAS, null)
                ?: throw IllegalStateException("No identity key found")
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(privateKey as java.security.PrivateKey)
            signature.update(data)
            signature.sign()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sign data", e)
            null
        }
    }

    fun verify(data: ByteArray, signatureBytes: ByteArray): Boolean {
        return try {
            val cert = keyStore.getCertificate(KEYSTORE_ALIAS)
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initVerify(cert.publicKey)
            signature.update(data)
            signature.verify(signatureBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify signature", e)
            false
        }
    }

    fun deleteIdentity(): Boolean {
        return try {
            keyStore.deleteEntry(KEYSTORE_ALIAS)
            Log.d(TAG, "Deleted swarm identity")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete identity", e)
            false
        }
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "hasIdentity" to hasIdentity,
        "peerId" to peerId,
        "publicKey" to publicKeyBase64,
        "displayName" to displayName
    )
}
