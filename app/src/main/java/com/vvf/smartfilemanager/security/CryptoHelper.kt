package com.vvf.smartfilemanager.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object CryptoHelper {
    private const val KEY_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "VVF_SmartFileManager_SafeFolderKey"
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

    fun encryptFile(inputFile: File, outputFile: File): Boolean {
        if (!inputFile.exists()) return false
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val iv = cipher.iv ?: throw IllegalStateException("Cipher IV was not generated")
            
            inputFile.inputStream().use { input ->
                outputFile.outputStream().use { output ->
                    // 1. Write IV length (1 byte)
                    output.write(iv.size)
                    // 2. Write the IV bytes
                    output.write(iv)
                    
                    // 3. Encrypt file contents in blocks
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
                }
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("CryptoHelper", "File encryption failed", e)
            if (outputFile.exists()) {
                outputFile.delete()
            }
            false
        }
    }

    fun decryptFile(inputFile: File, outputFile: File): Boolean {
        if (!inputFile.exists()) return false
        return try {
            val secretKey = getOrCreateSecretKey()
            inputFile.inputStream().use { input ->
                // 1. Read IV size
                val ivSize = input.read()
                if (ivSize <= 0 || ivSize > 128) {
                    throw IllegalArgumentException("Invalid IV size recorded in file: $ivSize")
                }
                
                // 2. Read IV bytes
                val iv = ByteArray(ivSize)
                val readBytes = input.read(iv)
                if (readBytes != ivSize) {
                    throw IllegalArgumentException("Incomplete IV read from file")
                }
                
                val cipher = Cipher.getInstance(TRANSFORMATION)
                val spec = GCMParameterSpec(TAG_SIZE_BITS, iv)
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                
                outputFile.outputStream().use { output ->
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
                }
            }
            true
        } catch (e: Exception) {
            android.util.Log.e("CryptoHelper", "File decryption failed", e)
            if (outputFile.exists()) {
                outputFile.delete()
            }
            false
        }
    }
}
