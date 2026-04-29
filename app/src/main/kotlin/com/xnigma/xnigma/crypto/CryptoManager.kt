package com.xnigma.xnigma.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoManager {

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private val MASTER_KEY_ALIAS = "xnigma_master_key"

    init {
        // Automatically generate the Hardware Master Key the first time this class is accessed
        if (!keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            generateHardwareMasterKey()
        }
    }

    /**
     * Step 1: Generate the un-exportable hardware-backed AES key.
     * This is used ONLY to encrypt/decrypt the user's actual RSA private key on this specific device.
     */
    private fun generateHardwareMasterKey() {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()

        keyGenerator.init(keyGenParameterSpec)
        keyGenerator.generateKey()
    }

    /**
     * Step 2: Generate the user's Identity Keypair (RSA) in software.
     * This can be exported later.
     */
    fun generateIdentityKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Step 3: Encrypt the software RSA Private Key using the hardware Master Key.
     * The resulting byte array is what we will save to SharedPreferences.
     */
    fun securePrivateKey(plainTextPrivateKey: ByteArray): ByteArray {
        val secretKey = keyStore.getKey(MASTER_KEY_ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        
        val iv = cipher.iv // Initialization Vector
        val cipherText = cipher.doFinal(plainTextPrivateKey)
        
        // We must store the IV with the cipher text to decrypt it later
        return iv + cipherText
    }

    /**
     * Step 4: Decrypt the stored RSA Private Key back into memory for active use.
     */
    fun unlockPrivateKey(securedPrivateKeyBlob: ByteArray): ByteArray {
        val secretKey = keyStore.getKey(MASTER_KEY_ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        
        // GCM uses a 12-byte IV. We extract it from the beginning of our stored blob.
        val iv = securedPrivateKeyBlob.copyOfRange(0, 12)
        val cipherText = securedPrivateKeyBlob.copyOfRange(12, securedPrivateKeyBlob.size)
        
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        
        return cipher.doFinal(cipherText)
    }

    // =====================================================================
    // HYBRID ENCRYPTION ENGINE & KEY CONVERTERS
    // =====================================================================

    /**
     * Converts a Base64 string back into a usable RSA PublicKey object.
     * Used when pulling a contact's public key from the Room database.
     */
    fun getPublicKeyFromString(base64PublicKey: String): PublicKey {
        val keyBytes = Base64.decode(base64PublicKey, Base64.NO_WRAP)
        val spec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(spec)
    }

    /**
     * Converts decrypted raw bytes back into a usable RSA PrivateKey object.
     * Used immediately after calling unlockPrivateKey().
     */
    fun getPrivateKeyFromBytes(unlockedBytes: ByteArray): PrivateKey {
        val spec = PKCS8EncodedKeySpec(unlockedBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePrivate(spec)
    }

    /**
     * The Hybrid Encryption Engine.
     * Encrypts a plaintext message using a one-time AES key, then encrypts that AES key with RSA.
     */
    fun encryptMessage(plainText: String, recipientPublicKey: PublicKey): String {
        // 1. Generate a random one-time AES session key
        val sessionKey = ByteArray(32) // 256-bit AES key
        SecureRandom().nextBytes(sessionKey)
        val secretKeySpec = SecretKeySpec(sessionKey, "AES")

        // 2. Encrypt the plaintext message with AES-GCM
        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        aesCipher.init(Cipher.ENCRYPT_MODE, secretKeySpec)
        val iv = aesCipher.iv // 12-byte IV generated by GCM
        val cipherText = aesCipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        // 3. Encrypt the AES session key with the recipient's RSA Public Key
        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsaCipher.init(Cipher.ENCRYPT_MODE, recipientPublicKey)
        val encryptedSessionKey = rsaCipher.doFinal(sessionKey)

        // 4. Encode all parts to Base64
        val base64EncryptedKey = Base64.encodeToString(encryptedSessionKey, Base64.NO_WRAP)
        val base64Iv = Base64.encodeToString(iv, Base64.NO_WRAP)
        val base64CipherText = Base64.encodeToString(cipherText, Base64.NO_WRAP)

        // 5. Package into our signature format
        val combinedPayload = "$base64EncryptedKey:$base64Iv:$base64CipherText"
        return "[XG]$combinedPayload[/XG]"
    }

    /**
     * The Hybrid Decryption Engine.
     * Unpacks the payload, decrypts the AES key using the local RSA Private Key, 
     * and uses the AES key to reveal the message.
     */
    fun decryptMessage(xnigmaPayload: String, myPrivateKey: PrivateKey): String {
        // 1. Strip the [XG] wrappers
        val cleanPayload = xnigmaPayload.replace("[XG]", "").replace("[/XG]", "")
        
        // 2. Split the payload into its three distinct parts
        val parts = cleanPayload.split(":")
        if (parts.size != 3) throw IllegalArgumentException("Invalid Xnigma payload format")

        val encryptedSessionKey = Base64.decode(parts[0], Base64.NO_WRAP)
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipherText = Base64.decode(parts[2], Base64.NO_WRAP)

        // 3. Decrypt the AES session key using our RSA Private Key
        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsaCipher.init(Cipher.DECRYPT_MODE, myPrivateKey)
        val sessionKeyBytes = rsaCipher.doFinal(encryptedSessionKey)
        val secretKeySpec = SecretKeySpec(sessionKeyBytes, "AES")

        // 4. Decrypt the actual message using the recovered AES key and IV
        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        aesCipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmSpec)
        val plainTextBytes = aesCipher.doFinal(cipherText)

        return String(plainTextBytes, Charsets.UTF_8)
    }
}