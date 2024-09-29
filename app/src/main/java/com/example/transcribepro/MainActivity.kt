package com.example.transcribepro

import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Button
import android.widget.TextView
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import android.content.ContentResolver
import android.database.Cursor
import android.provider.OpenableColumns

class MainActivity : ComponentActivity() {

    private lateinit var audioButton: Button
    private lateinit var logTextView: TextView
    private lateinit var audioLauncher: ActivityResultLauncher<String>
    private var selectedAudioUri: Uri? = null
    private lateinit var selectedLanguage: String
    private lateinit var spinner: Spinner
    private lateinit var adapter: ArrayAdapter<Any>
    private var languages = arrayOf("English", "Russian", "Chinese", "French", "German")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
                    audioLauncher.launch("audio/*")
                }
                "Choose Language" -> {
                    audioButton.text = "Transcribe"
                    selectedLanguage = spinner.selectedItem.toString()
                    hideSpinner()
                }
                "Transcribe" -> {
                    audioButton.text = "Save"
                    logTextView.visibility = View.VISIBLE
                    val fileName = getFileName(selectedAudioUri)
                    logTextView.text = """
                        Transcription started...
                        File name: $fileName
                        Language: $selectedLanguage
                    """.trimIndent()
                    // TODO: function uses audio uri, and chosen language as variable to transcribe audio into text
                }
                "Save" -> {
                    // TODO: function saves transcription to a file
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
                fileName = it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
            }
        }
        return fileName
    }
}
