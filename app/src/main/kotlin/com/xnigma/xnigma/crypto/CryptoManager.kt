package com.xnigma.xnigma.crypto

import android.util.Base64
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoManager {

    // =====================================================================
    // ECC IDENTITY GENERATION & PARSING
    // =====================================================================

    fun generateIdentityKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        return kpg.generateKeyPair()
    }

    fun getPublicKeyFromString(base64Key: String): PublicKey {
        val keyBytes = Base64.decode(base64Key, Base64.NO_WRAP)
        val spec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(spec)
    }

    fun getPrivateKeyFromBytes(keyBytes: ByteArray): PrivateKey {
        val spec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePrivate(spec)
    }

    // =====================================================================
    // ECC MULTI-KEY ENCRYPTION ENGINE
    // =====================================================================

    private fun getSharedSecret(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)
        return keyAgreement.generateSecret()
    }

    fun encryptMessage(plainText: String, recipientPublicKey: PublicKey, myPublicKey: PublicKey): String {
        val ephemeralKeyPair = generateIdentityKeyPair()

        val sessionKey = ByteArray(32)
        SecureRandom().nextBytes(sessionKey)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sessionKey, "AES"))
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val secret1 = getSharedSecret(ephemeralKeyPair.private, recipientPublicKey)
        val kek1Bytes = MessageDigest.getInstance("SHA-256").digest(secret1)
        
        val secret2 = getSharedSecret(ephemeralKeyPair.private, myPublicKey)
        val kek2Bytes = MessageDigest.getInstance("SHA-256").digest(secret2)

        val wrapCipher = Cipher.getInstance("AES/ECB/NoPadding")
        
        wrapCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek1Bytes, "AES"))
        val wrappedSessionKeyRecipient = wrapCipher.doFinal(sessionKey)

        wrapCipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek2Bytes, "AES"))
        val wrappedSessionKeySender = wrapCipher.doFinal(sessionKey)

        val p1 = Base64.encodeToString(ephemeralKeyPair.public.encoded, Base64.NO_WRAP)
        val p2 = Base64.encodeToString(wrappedSessionKeyRecipient, Base64.NO_WRAP)
        val p3 = Base64.encodeToString(wrappedSessionKeySender, Base64.NO_WRAP)
        val p4 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val p5 = Base64.encodeToString(cipherText, Base64.NO_WRAP)

        return "[XG]$p1:$p2:$p3:$p4:$p5[/XG]"
    }

    // =====================================================================
    // ECC MULTI-KEY DECRYPTION ENGINE
    // =====================================================================

    fun decryptMessage(xnigmaPayload: String, myPrivateKey: PrivateKey): String {
        val cleanPayload = xnigmaPayload.replace("[XG]", "").replace("[/XG]", "")
        val parts = cleanPayload.split(":")
        if (parts.size != 5) throw IllegalArgumentException("Invalid or outdated Xnigma payload")

        val ephemeralPub = getPublicKeyFromString(parts[0])
        val recipientEncKey = Base64.decode(parts[1], Base64.NO_WRAP)
        val senderEncKey = Base64.decode(parts[2], Base64.NO_WRAP)
        val iv = Base64.decode(parts[3], Base64.NO_WRAP)
        val cipherText = Base64.decode(parts[4], Base64.NO_WRAP)

        val sharedSecret = getSharedSecret(myPrivateKey, ephemeralPub)
        val kekBytes = MessageDigest.getInstance("SHA-256").digest(sharedSecret)
        
        val unwrapCipher = Cipher.getInstance("AES/ECB/NoPadding")
        unwrapCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kekBytes, "AES"))

        var sessionKeyBytes: ByteArray
        var plainTextBytes: ByteArray

        try {
            sessionKeyBytes = unwrapCipher.doFinal(recipientEncKey)
            val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
            aesCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(sessionKeyBytes, "AES"), GCMParameterSpec(128, iv))
            plainTextBytes = aesCipher.doFinal(cipherText) 
        } catch (e: Exception) {
            try {
                sessionKeyBytes = unwrapCipher.doFinal(senderEncKey)
                val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
                aesCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(sessionKeyBytes, "AES"), GCMParameterSpec(128, iv))
                plainTextBytes = aesCipher.doFinal(cipherText)
            } catch (e2: Exception) {
                throw SecurityException("Access Denied: Your identity does not match the sender or recipient.")
            }
        }

        return String(plainTextBytes, Charsets.UTF_8)
    }

    // =====================================================================
    // LOCAL KEYSTORE / EXPORT FALLBACKS 
    // =====================================================================
    
    fun securePrivateKey(privateKeyBytes: ByteArray): ByteArray {
        return privateKeyBytes
    }

    fun unlockPrivateKey(securedPrivateKeyBlob: ByteArray): ByteArray {
        return securedPrivateKeyBlob
    }

    @Suppress("UNUSED_PARAMETER")
    fun exportIdentityWithPassword(pub: String, priv: ByteArray, pass: String): String {
        return "EXPORT_FEATURE_WIP"
    }

    // FIX: Returns a non-nullable Pair to satisfy Kotlin's destructuring strictness
    @Suppress("UNUSED_PARAMETER")
    fun importIdentityWithPassword(payload: String, pass: String): Pair<String, ByteArray> {
        return Pair("", ByteArray(0))
    }
}