package com.example.transcribepro

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.transcribepro.media.AudioFileManager
import com.example.transcribepro.media.decodeWaveFile
import com.example.transcribepro.recorder.Recorder
import com.whispercpp.whisper.WhisperContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "MainActivity"
private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
private const val WHISPER_MODEL_PATH = "models/ggml-tiny.bin"
private const val BETA_VERSION = "0.9.0-beta"

class MainActivity : ComponentActivity() {

    private lateinit var recordButton: Button 
    private lateinit var transcribeButton: Button
    private lateinit var shareButton: Button
    private lateinit var logTextView: TextView
    private lateinit var titleTextView: TextView
    private lateinit var audioLauncher: ActivityResultLauncher<String>
    private var selectedAudioUri: Uri? = null
    private lateinit var selectedLanguage: String
    private lateinit var spinner: Spinner
    private lateinit var adapter: ArrayAdapter<Any>
    private var languages = arrayOf("English", "Russian", "Chinese", "French", "German")
    
    // Audio recording
    private val recorder = Recorder()
    private var isRecording = false
    private var recordingFile: File? = null
    
    // Whisper Context for transcription
    private var whisperContext: WhisperContext? = null
    private var modelLoaded = false

    // Audio file manager
    private lateinit var audioFileManager: AudioFileManager

    // Permission
    private var permissionToRecordAccepted = false
    private val permissions = arrayOf(Manifest.permission.RECORD_AUDIO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        audioFileManager = AudioFileManager(this)
        
        // Check if permission is already granted
        permissionToRecordAccepted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        
        // If not, request permissions
        if (!permissionToRecordAccepted) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)
        }
        
        recordButton = findViewById(R.id.recordButton)
        transcribeButton = findViewById(R.id.transcribeButton)
        shareButton = findViewById(R.id.shareButton)
        logTextView = findViewById(R.id.logTextView)
        titleTextView = findViewById(R.id.titleTextView)
        spinner = findViewById(R.id.language_spinner)
        
        // Set title with beta version
        titleTextView.text = "TranscribePro $BETA_VERSION"
        
        adapter = ArrayAdapter(this, R.layout.spinner_list, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        selectedLanguage = languages[0] // Default to English

        audioLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                selectedAudioUri = it
                logTextView.visibility = View.VISIBLE
                logTextView.text = "Selected file: ${getFileName(it)}"
                transcribeButton.visibility = View.VISIBLE
            }
        }

        recordButton.setOnClickListener {
            if (!permissionToRecordAccepted) {
                Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
                ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)
                return@setOnClickListener
            }
            
            toggleRecording()
        }
        
        transcribeButton.setOnClickListener {
            if (!modelLoaded) {
                Toast.makeText(this, "Whisper model not loaded. Please wait or check logs.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            transcribeAudio()
        }
        
        shareButton.setOnClickListener {
            shareTranscription()
        }
        
        // Show beta status
        logTextView.visibility = View.VISIBLE
        logTextView.text = "BETA TEST VERSION\nSetting up..."
        
        // Load the model asynchronously
        lifecycleScope.launch {
            try {
                checkModelExists()
                loadWhisperModel()
                Log.d(TAG, "Model loaded successfully")
                
                withContext(Dispatchers.Main) {
                    modelLoaded = true
                    logTextView.text = "Ready to record! (Model loaded successfully)"
                    Toast.makeText(this@MainActivity, "Model loaded successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model", e)
                withContext(Dispatchers.Main) {
                    logTextView.text = "ERROR: Failed to load Whisper model: ${e.message}\n\nPlease make sure ggml-tiny.bin is in the assets/models folder."
                    Toast.makeText(this@MainActivity, "Failed to load model: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        
        // Clean up old files
        lifecycleScope.launch {
            try {
                audioFileManager.cleanupOldFiles()
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up old files", e)
            }
        }
        
        // Initialize UI state
        updateUIState()
    }
    
    private suspend fun checkModelExists() = withContext(Dispatchers.IO) {
        try {
            assets.open(WHISPER_MODEL_PATH).use { 
                // Just checking if it exists and can be opened
                Log.d(TAG, "Found Whisper model file at $WHISPER_MODEL_PATH")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Whisper model file not found at $WHISPER_MODEL_PATH", e)
            throw RuntimeException("Model file not found. Please add the ggml-tiny.bin file to assets/models folder")
        }
    }
    
    private fun updateUIState() {
        if (isRecording) {
            recordButton.text = "Stop Recording"
            transcribeButton.visibility = View.GONE
            shareButton.visibility = View.GONE
        } else {
            recordButton.text = "Start Recording"
            // Only show transcribe button if we have a file to transcribe and model is loaded
            transcribeButton.visibility = if ((recordingFile != null || selectedAudioUri != null) && modelLoaded) 
                                           View.VISIBLE else View.GONE
            // Only show share button if we have transcription text
            shareButton.visibility = if (logTextView.text.isNotEmpty() && 
                                        logTextView.text.toString() != "Recording in progress..." && 
                                        !logTextView.text.toString().startsWith("Selected file:") &&
                                        !logTextView.text.toString().startsWith("BETA TEST VERSION") &&
                                        !logTextView.text.toString().startsWith("Ready to record") &&
                                        !logTextView.text.toString().startsWith("ERROR:"))
                                      View.VISIBLE else View.GONE
        }
    }
    
    private fun toggleRecording() {
        if (isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }
    
    private fun startRecording() {
        lifecycleScope.launch {
            try {
                recordingFile = audioFileManager.createRecordingFile()
                recordingFile?.let {
                    recorder.startRecording(it) { error ->
                        Log.e(TAG, "Recording error", error)
                        lifecycleScope.launch(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Recording failed: ${error.message}", Toast.LENGTH_SHORT).show()
                            isRecording = false
                            updateUIState()
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        isRecording = true
                        selectedAudioUri = null // Clear any selected file
                        logTextView.visibility = View.VISIBLE
                        logTextView.text = "Recording in progress..."
                        updateUIState()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recording", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to start recording: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun stopRecording() {
        lifecycleScope.launch {
            try {
                recorder.stopRecording()
                
                withContext(Dispatchers.Main) {
                    isRecording = false
                    logTextView.text = "Recording finished. Tap 'Transcribe' to process."
                    updateUIState()
                    
                    // Auto-transcribe after recording
                    if (modelLoaded) {
                        transcribeAudio()
                    } else {
                        Toast.makeText(this@MainActivity, "Cannot transcribe - model not loaded", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop recording", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to stop recording: ${e.message}", Toast.LENGTH_SHORT).show()
                    isRecording = false
                    updateUIState()
                }
            }
        }
    }
    
    private suspend fun loadWhisperModel() {
        try {
            // Use the model in models directory
            assets.open(WHISPER_MODEL_PATH).use { inputStream ->
                Log.d(TAG, "Loading model from $WHISPER_MODEL_PATH")
                whisperContext = WhisperContext.createContextFromInputStream(inputStream)
                Log.d(TAG, "Model loaded successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading whisper model", e)
            throw e
        }
    }
    
    private fun transcribeAudio() {
        val whisper = whisperContext ?: run {
            Toast.makeText(this, "Model not loaded yet", Toast.LENGTH_SHORT).show()
            return
        }
        
        transcribeButton.isEnabled = false
        logTextView.text = "Transcription in progress...\n(This may take a minute for the tiny model)"
        
        lifecycleScope.launch {
            try {
                val audioData = if (recordingFile != null) {
                    audioFileManager.getAudioData(recordingFile!!)
                } else if (selectedAudioUri != null) {
                    audioFileManager.getAudioDataFromUri(selectedAudioUri!!)
                } else {
                    throw IllegalStateException("No audio source available")
                }
                
                // Get selected language
                val langCode = when (selectedLanguage) {
                    "English" -> "en"
                    "Russian" -> "ru"
                    "Chinese" -> "zh"
                    "French" -> "fr"
                    "German" -> "de"
                    else -> "en"
                }
                
                val startTime = System.currentTimeMillis()
                val transcription = whisper.transcribeData(audioData, language = langCode)
                val elapsedTime = System.currentTimeMillis() - startTime
                
                withContext(Dispatchers.Main) {
                    logTextView.text = "Language: $selectedLanguage\nTranscription time: ${elapsedTime/1000.0} seconds\n\n$transcription"
                    transcribeButton.isEnabled = true
                    updateUIState()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                withContext(Dispatchers.Main) {
                    logTextView.text = "Transcription failed: ${e.message}"
                    transcribeButton.isEnabled = true
                }
            }
        }
    }
    
    private fun shareTranscription() {
        val transcriptionText = logTextView.text.toString()
        
        if (transcriptionText.isBlank()) {
            Toast.makeText(this, "No transcription to share", Toast.LENGTH_SHORT).show()
            return
        }
        
        // First save the transcription
        lifecycleScope.launch {
            try {
                if (recordingFile != null) {
                    val transcriptionFile = audioFileManager.saveTranscription(recordingFile!!, transcriptionText)
                    
                    withContext(Dispatchers.Main) {
                        // Then share it
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "TranscribePro Transcription")
                            putExtra(Intent.EXTRA_TEXT, transcriptionText)
                            
                            // Also attach the file if possible
                            try {
                                val fileUri = FileProvider.getUriForFile(
                                    this@MainActivity,
                                    "${applicationContext.packageName}.provider",
                                    transcriptionFile
                                )
                                putExtra(Intent.EXTRA_STREAM, fileUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to attach file to share intent", e)
                            }
                        }
                        startActivity(Intent.createChooser(shareIntent, "Share Transcription"))
                    }
                } else {
                    // No recording file to save to, just share the text
                    withContext(Dispatchers.Main) {
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "TranscribePro Transcription")
                            putExtra(Intent.EXTRA_TEXT, transcriptionText)
                        }
                        startActivity(Intent.createChooser(shareIntent, "Share Transcription"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to share transcription", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_RECORD_AUDIO_PERMISSION -> {
                permissionToRecordAccepted = grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
                
                if (!permissionToRecordAccepted) {
                    Toast.makeText(this, "Microphone permission required for recording", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Ready to record audio", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showSpinner() {
        spinner.visibility = View.VISIBLE
    }

    private fun hideSpinner() {
        spinner.visibility = View.GONE
    }

    private fun getFileName(uri: Uri?): String {
        uri ?: return "Unknown"
        var fileName = "Unknown"
        val contentResolver: ContentResolver = contentResolver
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (columnIndex >= 0) {
                    fileName = it.getString(columnIndex)
                }
            }
        }
        return fileName
    }
    
    override fun onDestroy() {
        lifecycleScope.launch {
            whisperContext?.release()
        }
        super.onDestroy()
    }
}
