package com.example.transcribepro.media

import android.content.Context
import android.net.Uri
import android.util.LruCache
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Intelligent audio file manager that provides:
 * 1. Automatic cleanup of old recordings
 * 2. In-memory caching of recent audio data
 * 3. Efficient file naming and organization
 * 4. Audio metadata tracking
 */
class AudioFileManager(private val context: Context) {
    
    companion object {
        private const val MAX_CACHE_SIZE = 5 * 1024 * 1024 // 5MB cache
        private const val MAX_FILE_AGE_DAYS = 30
        private const val RECORDINGS_DIR = "recordings"
    }
    
    // Cache for storing recently processed audio data
    private val audioCache = LruCache<String, FloatArray>(MAX_CACHE_SIZE)
    
    // Map to track metadata for recordings
    private val metadataMap = ConcurrentHashMap<String, AudioMetadata>()
    
    // Counter for recording sessions
    private val sessionCounter = AtomicInteger(0)
    
    // Get the base directory for our recordings
    private val recordingsDir: File
        get() {
            val dir = File(context.filesDir, RECORDINGS_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }
    
    // Create a new file for recording
    fun createRecordingFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val sessionId = sessionCounter.incrementAndGet()
        val fileName = "recording_${timestamp}_$sessionId.wav"
        return File(recordingsDir, fileName)
    }
    
    // Get audio data with caching
    suspend fun getAudioData(file: File): FloatArray = withContext(Dispatchers.IO) {
        val cacheKey = file.absolutePath
        
        // Try to get from cache first
        audioCache.get(cacheKey)?.let { 
            return@withContext it
        }
        
        // Otherwise load and cache
        val audioData = decodeWaveFile(file)
        audioCache.put(cacheKey, audioData)
        
        // Update metadata
        updateMetadata(file)
        
        return@withContext audioData
    }
    
    // Get audio data from Uri
    suspend fun getAudioDataFromUri(uri: Uri): FloatArray = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.wav")
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        val result = getAudioData(tempFile)
        
        // Clean up temp file
        tempFile.delete()
        
        return@withContext result
    }
    
    // Save a transcription with its associated audio file
    suspend fun saveTranscription(audioFile: File, text: String): File = withContext(Dispatchers.IO) {
        val baseName = audioFile.nameWithoutExtension
        val transcriptionFile = File(audioFile.parentFile, "$baseName.txt")
        transcriptionFile.writeText(text)
        
        // Update metadata
        val metadata = metadataMap[audioFile.absolutePath] ?: AudioMetadata(audioFile.absolutePath)
        metadata.hasTranscription = true
        metadata.transcriptionPath = transcriptionFile.absolutePath
        metadata.lastAccessed = System.currentTimeMillis()
        metadataMap[audioFile.absolutePath] = metadata
        
        return@withContext transcriptionFile
    }
    
    // Clean up old files
    suspend fun cleanupOldFiles() = withContext(Dispatchers.IO) {
        val cutoffTime = System.currentTimeMillis() - (MAX_FILE_AGE_DAYS * 24 * 60 * 60 * 1000)
        
        recordingsDir.listFiles()?.forEach { file ->
            // Skip if recently accessed according to metadata
            val metadata = metadataMap[file.absolutePath]
            if (metadata != null && metadata.lastAccessed > cutoffTime) {
                return@forEach
            }
            
            // Delete old files
            if (file.lastModified() < cutoffTime) {
                // If this is a WAV file, also delete the transcription if it exists
                if (file.extension.equals("wav", ignoreCase = true)) {
                    val transcriptionFile = File(file.parentFile, "${file.nameWithoutExtension}.txt")
                    if (transcriptionFile.exists()) {
                        transcriptionFile.delete()
                    }
                }
                
                file.delete()
                metadataMap.remove(file.absolutePath)
            }
        }
    }
    
    // Update metadata for a file
    private fun updateMetadata(file: File) {
        val path = file.absolutePath
        val metadata = metadataMap[path] ?: AudioMetadata(path)
        metadata.lastAccessed = System.currentTimeMillis()
        metadata.fileSizeBytes = file.length()
        metadataMap[path] = metadata
    }
    
    // Data class to hold metadata
    data class AudioMetadata(
        val filePath: String,
        var lastAccessed: Long = System.currentTimeMillis(),
        var fileSizeBytes: Long = 0,
        var hasTranscription: Boolean = false,
        var transcriptionPath: String? = null
    )
} 