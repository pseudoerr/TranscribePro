package com.example.transcribepro

import android.Manifest
import android.content.ContentResolver
import android.content.Context
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

class MainActivity : ComponentActivity() {

    private lateinit var audioButton: Button
    private lateinit var logTextView: TextView
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

    // Audio file manager
    private lateinit var audioFileManager: AudioFileManager

    // Permission
    private var permissionToRecordAccepted = false
    private val permissions = arrayOf(Manifest.permission.RECORD_AUDIO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        audioFileManager = AudioFileManager(this)
        
        // Request permissions
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)
        
        audioButton = findViewById(R.id.selectAudioButton)
        logTextView = findViewById(R.id.logTextView)
        spinner = findViewById(R.id.language_spinner)
        adapter = ArrayAdapter(this, R.layout.spinner_list, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        audioLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                selectedAudioUri = it
                audioButton.text = "Choose Language"
                showSpinner()
            }
        }

        audioButton.setOnClickListener {
            when (audioButton.text) {
                "Select Audio" -> {
                    if (permissionToRecordAccepted) {
                        toggleRecording()
                    } else {
                        audioLauncher.launch("audio/*")
                    }
                }
                "Choose Language" -> {
                    audioButton.text = "Transcribe"
                    selectedLanguage = spinner.selectedItem.toString()
                    hideSpinner()
                }
                "Stop Recording" -> {
                    toggleRecording()
                }
                "Transcribe" -> {
                    transcribeAudio()
                }
                "Save" -> {
                    saveTranscription()
                }
            }
        }
        
        // Load the model asynchronously
        lifecycleScope.launch {
            try {
                loadWhisperModel()
                Log.d(TAG, "Model loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model", e)
                withContext(Dispatchers.Main) {
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
                            audioButton.text = "Select Audio"
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        isRecording = true
                        audioButton.text = "Stop Recording"
                        logTextView.visibility = View.VISIBLE
                        logTextView.text = "Recording in progress...\nFile: ${it.name}"
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
                    audioButton.text = "Choose Language"
                    showSpinner()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop recording", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to stop recording: ${e.message}", Toast.LENGTH_SHORT).show()
                    isRecording = false
                    audioButton.text = "Select Audio"
                }
            }
        }
    }
    
    private suspend fun loadWhisperModel() {
        try {
            // Use the model in models directory
            assets.open("models/ggml-tiny.bin").use { inputStream ->
                Log.d(TAG, "Loading model from models/ggml-tiny.bin")
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
        
        audioButton.isEnabled = false
        audioButton.text = "Transcribing..."
        logTextView.text = "Transcription in progress...\n"
        
        lifecycleScope.launch {
            try {
                val audioData = if (recordingFile != null) {
                    audioFileManager.getAudioData(recordingFile!!)
                } else if (selectedAudioUri != null) {
                    audioFileManager.getAudioDataFromUri(selectedAudioUri!!)
                } else {
                    throw IllegalStateException("No audio source available")
                }
                
                val transcription = whisper.transcribeData(audioData)
                
                withContext(Dispatchers.Main) {
                    logTextView.text = transcription
                    audioButton.text = "Save"
                    audioButton.isEnabled = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Transcription failed", e)
                withContext(Dispatchers.Main) {
                    logTextView.text = "Transcription failed: ${e.message}"
                    audioButton.text = "Transcribe"
                    audioButton.isEnabled = true
                }
            }
        }
    }
    
    private fun saveTranscription() {
        val transcriptionText = logTextView.text.toString()
        
        if (transcriptionText.isBlank() || recordingFile == null) {
            Toast.makeText(this, "No transcription or audio file to save", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                audioFileManager.saveTranscription(recordingFile!!, transcriptionText)
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Transcription saved", Toast.LENGTH_SHORT).show()
                    audioButton.text = "Select Audio"
                    logTextView.visibility = View.GONE
                    recordingFile = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save transcription", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, "Permission to record audio denied", Toast.LENGTH_SHORT).show()
                } else {
                    audioButton.text = "Select Audio"
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
