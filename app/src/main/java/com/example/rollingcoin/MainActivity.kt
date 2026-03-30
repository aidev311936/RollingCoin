package com.example.rollingcoin

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var simulationView: SimulationView
    private var mediaRecorder: MediaRecorder? = null

    private val pickImpactLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { saveCustomSound(it, "custom_impact.mp3") { simulationView.reloadImpactSound() } }
    }

    private val pickScrapeLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { saveCustomSound(it, "custom_scrape.mp3") { simulationView.reloadScrapeSound() } }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        simulationView = findViewById(R.id.simulationView)

        setupSettingsToggle()
        setupSliders()
        setupAudioButtons()
    }

    // region Settings panel
    private fun setupSettingsToggle() {
        val collapsibleContent = findViewById<View>(R.id.collapsibleContent)
        val toggleHeader = findViewById<View>(R.id.toggleHeader)
        val toggleText = findViewById<TextView>(R.id.toggleText)

        toggleHeader.setOnClickListener {
            val isVisible = collapsibleContent.visibility == View.VISIBLE
            collapsibleContent.visibility = if (isVisible) View.GONE else View.VISIBLE
            toggleText.text = if (isVisible) "EINSTELLUNGEN ZEIGEN" else "EINSTELLUNGEN AUSBLENDEN"
        }
    }

    private fun setupSliders() {
        val countSlider = findViewById<SeekBar>(R.id.countSlider)
        val volumeSlider = findViewById<SeekBar>(R.id.volumeSlider)
        val radiusSlider = findViewById<SeekBar>(R.id.radiusSlider)
        val glideSlider = findViewById<SeekBar>(R.id.glideSlider)
        val vibrationSlider = findViewById<SeekBar>(R.id.vibrationSlider)

        val countValue = findViewById<TextView>(R.id.countValue)
        val volumeValue = findViewById<TextView>(R.id.volumeValue)
        val radiusValue = findViewById<TextView>(R.id.radiusValue)
        val glideValue = findViewById<TextView>(R.id.glideValue)
        val vibrationValue = findViewById<TextView>(R.id.vibrationValue)

        countSlider.onChange { progress ->
            val count = progress.coerceAtLeast(1)
            simulationView.setCoinCount(count)
            countValue.text = count.toString()
        }
        volumeSlider.onChange { progress ->
            simulationView.volumePercent = progress / 100f
            volumeValue.text = "$progress%"
        }
        radiusSlider.onChange { progress ->
            simulationView.radiusPercent = progress / 100f
            radiusValue.text = "$progress%"
        }
        glideSlider.onChange { progress ->
            simulationView.glidePercent = progress / 100f
            glideValue.text = "$progress%"
        }
        vibrationSlider.onChange { progress ->
            simulationView.vibrationPercent = progress / 100f
            vibrationValue.text = "$progress%"
        }

        countSlider.progress = 1
        volumeSlider.progress = 50
        radiusSlider.progress = 30
        glideSlider.progress = 30
        vibrationSlider.progress = 80
    }
    // endregion

    // region Audio buttons
    @SuppressLint("ClickableViewAccessibility")
    private fun setupAudioButtons() {
        val impactFile = File(filesDir, "custom_impact.mp3")
        val scrapeFile = File(filesDir, "custom_scrape.mp3")

        findViewById<Button>(R.id.btnLoadImpact).setOnClickListener { pickImpactLauncher.launch("audio/*") }
        findViewById<Button>(R.id.btnLoadScrape).setOnClickListener { pickScrapeLauncher.launch("audio/*") }

        setupRecordButton(
            button = findViewById(R.id.btnRecordImpact),
            file = impactFile,
            label = "Rec Impact",
            onFinished = { simulationView.reloadImpactSound() }
        )
        setupRecordButton(
            button = findViewById(R.id.btnRecordScrape),
            file = scrapeFile,
            label = "Rec Scrape",
            onFinished = { simulationView.reloadScrapeSound() }
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupRecordButton(button: Button, file: File, label: String, onFinished: () -> Unit) {
        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (checkAudioPermission()) {
                        startRecording(file)
                        button.text = "REC..."
                        button.setBackgroundColor(Color.RED)
                    } else {
                        requestAudioPermission()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRecording()
                    button.text = label
                    button.setBackgroundColor(Color.parseColor("#DDDDDD"))
                    onFinished()
                    true
                }
                else -> false
            }
        }
    }
    // endregion

    // region Audio recording
    private fun checkAudioPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestAudioPermission() =
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)

    private fun startRecording(file: File) {
        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveCustomSound(uri: Uri, fileName: String, onComplete: () -> Unit) {
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val file = File(filesDir, fileName)
                FileOutputStream(file).use { output -> input.copyTo(output) }
                onComplete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    // endregion

    override fun onResume() {
        super.onResume()
        simulationView.start()
    }

    override fun onPause() {
        super.onPause()
        simulationView.stop()
    }
}

// Kotlin extension to reduce SeekBar listener boilerplate
private fun SeekBar.onChange(action: (progress: Int) -> Unit) {
    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = action(progress)
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
    })
}
