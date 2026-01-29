package com.example.lifesaiver.protocol.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.lifesaiver.protocol.codec.PacketEncoder
import com.example.lifesaiver.protocol.core.ProtocolConstants
import com.example.lifesaiver.protocol.model.IdentityAnnouncementPayload
import com.example.lifesaiver.protocol.model.Packet
import com.example.lifesaiver.protocol.model.PacketType
import com.example.lifesaiver.protocol.util.toHexString
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

class SignatureManager(
    context: Context,
    private val encoder: PacketEncoder
) {
    private val prefs: SharedPreferences = createEncryptedPrefs(context)
    private val signingPrivateKey: Ed25519PrivateKeyParameters
    private val signingPublicKey: Ed25519PublicKeyParameters
    private val noisePublicKey: X25519PublicKeyParameters
    private val peerSigningKeys = ConcurrentHashMap<String, ByteArray>()

    init {
        val signingKeys = loadOrCreateSigningKeyPair()
        signingPrivateKey = signingKeys.first
        signingPublicKey = signingKeys.second
        val noiseKeys = loadOrCreateNoiseKeyPair()
        noisePublicKey = noiseKeys.second
    }

    fun getPublicKeyBytes(): ByteArray = signingPublicKey.encoded

    fun getNoisePublicKeyBytes(): ByteArray = noisePublicKey.encoded

    fun sign(packet: Packet): Packet {
        val data = toSigningBytes(packet) ?: return packet
        val signature = signBytes(data)
        return packet.copy(
            header = packet.header.copy(signature = signature)
        )
    }

    fun verify(packet: Packet): Boolean {
        if (!shouldVerify(packet.header.type)) return true
        val signature = packet.header.signature ?: return false
        val peerId = packet.header.senderId.toHexString()
        val publicKeyBytes = when (packet.header.type) {
            PacketType.ANNOUNCE -> extractAnnouncePublicKey(packet.payload)
            else -> peerSigningKeys[peerId]
        } ?: return false

        val data = toSigningBytes(packet) ?: return false
        val isValid = verifyBytes(signature, data, publicKeyBytes)
        if (isValid && packet.header.type == PacketType.ANNOUNCE) {
            peerSigningKeys[peerId] = publicKeyBytes
        }
        return isValid
    }

    private fun shouldVerify(type: PacketType): Boolean {
        return type == PacketType.ANNOUNCE ||
            type == PacketType.MESSAGE ||
            type == PacketType.FILE_TRANSFER
    }

    private fun toSigningBytes(packet: Packet): ByteArray? {
        val unsignedHeader = packet.header.copy(
            signature = null,
            ttl = ProtocolConstants.SYNC_TTL_HOPS
        )
        val unsignedPacket = packet.copy(header = unsignedHeader)
        return encoder.encode(unsignedPacket)
    }

    private fun signBytes(data: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, signingPrivateKey)
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    private fun verifyBytes(signature: ByteArray, data: ByteArray, publicKeyBytes: ByteArray): Boolean {
        return try {
            val publicKey = Ed25519PublicKeyParameters(publicKeyBytes, 0)
            val verifier = Ed25519Signer()
            verifier.init(false, publicKey)
            verifier.update(data, 0, data.size)
            verifier.verifySignature(signature)
        } catch (_: Exception) {
            false
        }
    }

    private fun extractAnnouncePublicKey(payload: ByteArray): ByteArray? {
        val announcement = IdentityAnnouncementPayload.decode(payload) ?: return null
        return announcement.signingPublicKey
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun loadOrCreateSigningKeyPair(): Pair<Ed25519PrivateKeyParameters, Ed25519PublicKeyParameters> {
        val privateKeyString = prefs.getString(KEY_SIGNING_PRIVATE, null)
        val publicKeyString = prefs.getString(KEY_SIGNING_PUBLIC, null)

        if (privateKeyString != null) {
            try {
                val privateKeyBytes = Base64.decode(privateKeyString, Base64.NO_WRAP)
                if (privateKeyBytes.size == SIGNING_KEY_SIZE) {
                    val privateKey = Ed25519PrivateKeyParameters(privateKeyBytes, 0)
                    val publicKey = publicKeyString?.let { stored ->
                        val publicKeyBytes = Base64.decode(stored, Base64.NO_WRAP)
                        if (publicKeyBytes.size == SIGNING_KEY_SIZE) {
                            Ed25519PublicKeyParameters(publicKeyBytes, 0)
                        } else {
                            privateKey.generatePublicKey()
                        }
                    } ?: privateKey.generatePublicKey()
                    saveSigningKeyPair(privateKey, publicKey)
                    return privateKey to publicKey
                }
            } catch (_: Exception) {
                // Fall through to generate a new keypair.
            }
        }

        val keyGen = Ed25519KeyPairGenerator()
        keyGen.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val keyPair = keyGen.generateKeyPair()
        val privateKey = keyPair.private as Ed25519PrivateKeyParameters
        val publicKey = keyPair.public as Ed25519PublicKeyParameters
        saveSigningKeyPair(privateKey, publicKey)
        return privateKey to publicKey
    }

    private fun loadOrCreateNoiseKeyPair(): Pair<X25519PrivateKeyParameters, X25519PublicKeyParameters> {
        val privateKeyString = prefs.getString(KEY_NOISE_PRIVATE, null)
        val publicKeyString = prefs.getString(KEY_NOISE_PUBLIC, null)

        if (privateKeyString != null) {
            try {
                val privateKeyBytes = Base64.decode(privateKeyString, Base64.NO_WRAP)
                if (privateKeyBytes.size == NOISE_KEY_SIZE) {
                    val privateKey = X25519PrivateKeyParameters(privateKeyBytes, 0)
                    val publicKey = publicKeyString?.let { stored ->
                        val publicKeyBytes = Base64.decode(stored, Base64.NO_WRAP)
                        if (publicKeyBytes.size == NOISE_KEY_SIZE) {
                            X25519PublicKeyParameters(publicKeyBytes, 0)
                        } else {
                            privateKey.generatePublicKey()
                        }
                    } ?: privateKey.generatePublicKey()
                    saveNoiseKeyPair(privateKey, publicKey)
                    return privateKey to publicKey
                }
            } catch (_: Exception) {
                // Fall through to generate a new keypair.
            }
        }

        val privateKey = X25519PrivateKeyParameters(SecureRandom())
        val publicKey = privateKey.generatePublicKey()
        saveNoiseKeyPair(privateKey, publicKey)
        return privateKey to publicKey
    }

    private fun saveSigningKeyPair(
        privateKey: Ed25519PrivateKeyParameters,
        publicKey: Ed25519PublicKeyParameters
    ) {
        val privateKeyString = Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)
        val publicKeyString = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        prefs.edit()
            .putString(KEY_SIGNING_PRIVATE, privateKeyString)
            .putString(KEY_SIGNING_PUBLIC, publicKeyString)
            .apply()
    }

    private fun saveNoiseKeyPair(
        privateKey: X25519PrivateKeyParameters,
        publicKey: X25519PublicKeyParameters
    ) {
        val privateKeyBytes = ByteArray(NOISE_KEY_SIZE)
        val publicKeyBytes = ByteArray(NOISE_KEY_SIZE)
        privateKey.encode(privateKeyBytes, 0)
        publicKey.encode(publicKeyBytes, 0)
        val privateKeyString = Base64.encodeToString(privateKeyBytes, Base64.NO_WRAP)
        val publicKeyString = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)
        prefs.edit()
            .putString(KEY_NOISE_PRIVATE, privateKeyString)
            .putString(KEY_NOISE_PUBLIC, publicKeyString)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "signature_prefs"
        const val KEY_SIGNING_PRIVATE = "ed25519_signing_private"
        const val KEY_SIGNING_PUBLIC = "ed25519_signing_public"
        const val KEY_NOISE_PRIVATE = "noise_static_private"
        const val KEY_NOISE_PUBLIC = "noise_static_public"
        const val SIGNING_KEY_SIZE = 32
        const val NOISE_KEY_SIZE = 32
    }
}
