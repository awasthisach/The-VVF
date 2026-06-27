package com.vvf.smartfilemanager.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CryptoHelper {
    private const val KEY_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "VVF_SmartFileManager_SafeFolderKey"
    private const val BIOMETRIC_KEY_ALIAS = "VVF_SmartFileManager_BiometricKey"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12 // Standard IV size for GCM is 12 bytes
    private const val TAG_SIZE_BITS = 128

    @Synchronized
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEY_PROVIDER).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEY_PROVIDER)
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    @Synchronized
    fun getOrCreateBiometricKey() {
        val keyStore = KeyStore.getInstance(KEY_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) return

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEY_PROVIDER)
        val builder = KeyGenParameterSpec.Builder(
            BIOMETRIC_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setInvalidatedByBiometricEnrollment(true)
        }
        keyGenerator.init(builder.build())
    }

    fun getInitializedBiometricCipher(mode: Int, iv: ByteArray? = null): Cipher? {
        return try {
            getOrCreateBiometricKey()
            val keyStore = KeyStore.getInstance(KEY_PROVIDER).apply { load(null) }
            val key = keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance(TRANSFORMATION)
            if (mode == Cipher.ENCRYPT_MODE) {
                cipher.init(mode, key)
            } else {
                if (iv == null) return null
                cipher.init(mode, key, GCMParameterSpec(TAG_SIZE_BITS, iv))
            }
            cipher
        } catch (e: Exception) {
            android.util.Log.e("CryptoHelper", "Failed to initialize biometric cipher", e)
            null
        }
    }

    fun encryptPinWithCipher(cipher: Cipher, pin: String): Pair<ByteArray, ByteArray>? {
        return try {
            val encryptedBytes = cipher.doFinal(pin.toByteArray(Charsets.UTF_8))
            Pair(encryptedBytes, cipher.iv)
        } catch (e: Exception) {
            android.util.Log.e("CryptoHelper", "Failed to encrypt PIN with biometric cipher", e)
            null
        }
    }

    fun decryptPinWithCipher(cipher: Cipher, encryptedPin: ByteArray): String? {
        return try {
            val decryptedBytes = cipher.doFinal(encryptedPin)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.e("CryptoHelper", "Failed to decrypt PIN with biometric cipher", e)
            null
        }
    }

    fun encryptStream(input: InputStream, output: OutputStream): Boolean {
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv ?: throw IllegalStateException("Cipher IV was not generated")
            
            // 1. Write IV length (1 byte)
            output.write(iv.size)
            // 2. Write the IV bytes
            output.write(iv)
            
            // 3. Encrypt contents in blocks
            val buffer = ByteArray(16 * 1024)
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                val outputBytes = cipher.update(buffer, 0, bytesRead)
                if (outputBytes != null) {
                    output.write(outputBytes)
                }
                bytesRead = input.read(buffer)
            }
            val finalBytes = cipher.doFinal()
            if (finalBytes != null) {
                output.write(finalBytes)
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("CryptoHelper", "Stream encryption failed", e)
            false
        }
    }

    fun decryptStream(input: InputStream, output: OutputStream): Boolean {
        return try {
            val secretKey = getOrCreateSecretKey()
            // 1. Read IV size
            val ivSize = input.read()
            if (ivSize <= 0 || ivSize > 128) {
                throw IllegalArgumentException("Invalid IV size recorded in stream: $ivSize")
            }
            
            // 2. Read IV bytes
            val iv = ByteArray(ivSize)
            val readBytes = input.read(iv)
            if (readBytes != ivSize) {
                throw IllegalArgumentException("Incomplete IV read from stream")
            }
            
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(TAG_SIZE_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            // 3. Decrypt in blocks
            val buffer = ByteArray(16 * 1024)
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                val decryptedBytes = cipher.update(buffer, 0, bytesRead)
                if (decryptedBytes != null) {
                    output.write(decryptedBytes)
                }
                bytesRead = input.read(buffer)
            }
            val finalBytes = cipher.doFinal()
            if (finalBytes != null) {
                output.write(finalBytes)
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("CryptoHelper", "Stream decryption failed", e)
            false
        }
    }

    fun encryptFile(inputFile: File, outputFile: File): Boolean {
        if (!inputFile.exists()) return false
        return try {
            inputFile.inputStream().use { input ->
                outputFile.outputStream().use { output ->
                    encryptStream(input, output)
                }
            }
        } catch (e: Exception) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            false
        }
    }

    fun decryptFile(inputFile: File, outputFile: File): Boolean {
        if (!inputFile.exists()) return false
        return try {
            inputFile.inputStream().use { input ->
                outputFile.outputStream().use { output ->
                    decryptStream(input, output)
                }
            }
        } catch (e: Exception) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            false
        }
    }
}
