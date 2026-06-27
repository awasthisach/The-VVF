package com.vvf.smartfilemanager.utils

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ArchiveHelper {

    fun compressToZip(files: List<File>, destZipFile: File): Flow<Float> = flow {
        val totalSize = files.sumOf { if (it.isFile) it.length() else 0L }
        var bytesWritten = 0L

        ZipOutputStream(FileOutputStream(destZipFile)).use { zos ->
            val buffer = ByteArray(8 * 1024)
            for (file in files) {
                if (!file.exists() || !file.isFile) continue
                
                // Add Zip Entry
                val entry = ZipEntry(file.name)
                zos.putNextEntry(entry)
                
                file.inputStream().use { input ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        // Check for disk space before writing
                        if (destZipFile.parentFile?.let { it.usableSpace < read } == true) {
                            throw java.io.IOException("Disk Full: Cannot complete compression")
                        }
                        zos.write(buffer, 0, read)
                        bytesWritten += read
                        if (totalSize > 0L) {
                            emit(bytesWritten.toFloat() / totalSize)
                        }
                    }
                }
                zos.closeEntry()
            }
        }
        emit(1.0f)
    }

    fun decompressZip(zipFile: File, destDir: File): Flow<Float> = flow {
        val totalSize = zipFile.length()
        var bytesReadTotal = 0L
        val canonicalDestDirPath = destDir.canonicalPath

        if (!destDir.exists()) destDir.mkdirs()

        ZipInputStream(zipFile.inputStream()).use { zis ->
            val buffer = ByteArray(8 * 1024)
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val targetFile = File(destDir, entry.name)
                
                // Prevent ZIP Slip Vulnerability
                val canonicalTargetFilePath = targetFile.canonicalPath
                if (!canonicalTargetFilePath.startsWith(canonicalDestDirPath + File.separator) &&
                    canonicalTargetFilePath != canonicalDestDirPath) {
                    throw SecurityException("Malicious ZIP entry detected: ZIP Slip attack path blocked! (${entry.name})")
                }

                if (entry.isDirectory) {
                    targetFile.mkdirs()
                } else {
                    targetFile.parentFile?.mkdirs()
                    FileOutputStream(targetFile).use { fos ->
                        var read: Int
                        while (zis.read(buffer).also { read = it } != -1) {
                            if (destDir.usableSpace < read) {
                                throw java.io.IOException("Disk Full: Cannot complete decompression")
                            }
                            fos.write(buffer, 0, read)
                            bytesReadTotal += read
                            if (totalSize > 0L) {
                                emit(minOf(0.99f, bytesReadTotal.toFloat() / totalSize))
                            }
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        emit(1.0f)
    }

    fun decompressTar(tarFile: File, destDir: File): Flow<Float> = flow {
        val totalSize = tarFile.length()
        var bytesReadTotal = 0L
        val canonicalDestDirPath = destDir.canonicalPath
        val buffer = ByteArray(8 * 1024)

        if (!destDir.exists()) destDir.mkdirs()

        tarFile.inputStream().use { fis ->
            val header = ByteArray(512)
            while (fis.read(header) == 512) {
                bytesReadTotal += 512
                
                // Tar archives end with 512 empty bytes
                if (header.all { it == 0.toByte() }) break

                // Extract name
                val nameBytes = header.sliceArray(0 until 100)
                val nameStr = String(nameBytes).trim { it <= ' ' || it == '\u0000' }
                if (nameStr.isEmpty()) continue

                // Extract file size in octal (bytes 124 to 135)
                val sizeBytes = header.sliceArray(124 until 135)
                val sizeStr = String(sizeBytes).trim { it <= ' ' || it == '\u0000' }
                val fileSize = if (sizeStr.isNotEmpty()) sizeStr.toLong(8) else 0L

                val typeFlag = header[156]
                val isDir = typeFlag == '5'.code.toByte() || nameStr.endsWith("/")

                val targetFile = File(destDir, nameStr)
                
                // Prevent ZIP Slip Vulnerability
                val canonicalTargetFilePath = targetFile.canonicalPath
                if (!canonicalTargetFilePath.startsWith(canonicalDestDirPath + File.separator) &&
                    canonicalTargetFilePath != canonicalDestDirPath) {
                    throw SecurityException("Malicious TAR entry detected: ZIP Slip attack path blocked! ($nameStr)")
                }

                if (isDir) {
                    targetFile.mkdirs()
                } else {
                    targetFile.parentFile?.mkdirs()
                    FileOutputStream(targetFile).use { fos ->
                        var remaining = fileSize
                        while (remaining > 0) {
                            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                            val read = fis.read(buffer, 0, toRead)
                            if (read == -1) break
                            
                            if (destDir.usableSpace < read) {
                                throw java.io.IOException("Disk Full: Cannot complete TAR extraction")
                            }
                            fos.write(buffer, 0, read)
                            bytesReadTotal += read
                            remaining -= read
                        }
                    }
                    
                    // Tar entries are padded to 512 byte block boundaries
                    val padding = (512 - (fileSize % 512)) % 512
                    if (padding > 0) {
                        val skipped = fis.skip(padding)
                        bytesReadTotal += skipped
                    }
                }
                
                if (totalSize > 0L) {
                    emit(minOf(0.99f, bytesReadTotal.toFloat() / totalSize))
                }
            }
        }
        emit(1.0f)
    }
}
