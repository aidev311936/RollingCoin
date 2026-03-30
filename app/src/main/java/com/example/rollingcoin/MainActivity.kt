package com.example.rollingcoin

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.MediaRecorder
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
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
import java.util.Random
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private lateinit var simulationView: SimulationView
    private var mediaRecorder: MediaRecorder? = null
    
    private var impactFile: File? = null
    private var scrapeFile: File? = null

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
        impactFile = File(filesDir, "custom_impact.mp3")
        scrapeFile = File(filesDir, "custom_scrape.mp3")

        val collapsible = findViewById<View>(R.id.collapsibleContent)
        val toggleHeader = findViewById<View>(R.id.toggleHeader)
        val toggleText = findViewById<TextView>(R.id.toggleText)

        toggleHeader.setOnClickListener {
            if (collapsible.visibility == View.VISIBLE) {
                collapsible.visibility = View.GONE
                toggleText.text = "EINSTELLUNGEN ZEIGEN"
            } else {
                collapsible.visibility = View.VISIBLE
                toggleText.text = "EINSTELLUNGEN AUSBLENDEN"
            }
        }

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

        findViewById<Button>(R.id.btnLoadImpact).setOnClickListener { pickImpactLauncher.launch("audio/*") }
        findViewById<Button>(R.id.btnLoadScrape).setOnClickListener { pickScrapeLauncher.launch("audio/*") }

        setupRecorderButton(findViewById(R.id.btnRecordImpact), impactFile!!) { simulationView.reloadImpactSound() }
        setupRecorderButton(findViewById(R.id.btnRecordScrape), scrapeFile!!) { simulationView.reloadScrapeSound() }

        countSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                val c = p.coerceAtLeast(1); simulationView.setCoinCount(c); countValue.text = c.toString()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        volumeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                simulationView.volumePercent = p / 100f; volumeValue.text = "$p%"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        radiusSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                simulationView.radiusPercent = p / 100f; radiusValue.text = "$p%"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        glideSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                simulationView.glidePercent = p / 100f; glideValue.text = "$p%"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        vibrationSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, f: Boolean) {
                simulationView.vibrationPercent = p / 100f; vibrationValue.text = "$p%"
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        countSlider.progress = 1
        volumeSlider.progress = 50
        radiusSlider.progress = 30
        glideSlider.progress = 30
        vibrationSlider.progress = 80
    }

    private fun saveCustomSound(uri: Uri, fileName: String, onComplete: () -> Unit) {
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val file = File(filesDir, fileName)
                FileOutputStream(file).use { output -> input.copyTo(output) }
                onComplete()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupRecorderButton(button: Button, file: File, onFinished: () -> Unit) {
        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (checkPermission()) {
                        startRecording(file); button.text = "REC..."; button.setBackgroundColor(Color.RED)
                    } else requestPermission()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopRecording()
                    button.text = if (file.name.contains("impact")) "Rec Impact" else "Rec Scrape"
                    button.setBackgroundColor(Color.parseColor("#DDDDDD"))
                    onFinished()
                    true
                }
                else -> false
            }
        }
    }

    private fun checkPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun requestPermission() = ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)

    private fun startRecording(file: File) {
        try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare(); start()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun stopRecording() {
        try { mediaRecorder?.stop(); mediaRecorder?.release(); mediaRecorder = null } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onResume() { super.onResume(); simulationView.start() }
    override fun onPause() { super.onPause(); simulationView.stop() }
}

class Coin(val id: Int, var x: Float, var y: Float, var vx: Float = 0f, var vy: Float = 0f) {
    var wasHitLeft = false; var wasHitRight = false; var wasHitTop = false; var wasHitBottom = false
    val collidingWith = mutableSetOf<Int>()
}

class SimulationView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private var soundPool: SoundPool
    private var impactSoundId: Int = -1
    private var scrapeSoundId: Int = -1
    var volumePercent = 0.5f

    private val coins = mutableListOf<Coin>()
    private val random = Random()
    private var nextCoinId = 0

    var radiusPercent = 0.3f; set(value) { field = value; updateCalculatedValues() }
    var weightPercent = 0.5f; set(value) { field = value; updateCalculatedValues() }
    var glidePercent = 0.3f; set(value) { field = value; updateCalculatedValues() }
    var vibrationPercent = 0.8f; set(value) { field = value; updateCalculatedValues() }

    private var coinRadius = 50f; private var accelerationMultiplier = 1.0f
    private var currentFriction = 0.97f; private var vibrationIntensityFactor = 1.0f; private var vibrationThreshold = 0.3f
    private var sensorX = 0f; private var sensorY = 0f
    private var lastVibrationTime = 0L; private var lastScrapeTime = 0L
    private val VIBRATION_COOLDOWN = 110L; private val SCRAPE_COOLDOWN = 200L

    private val paint = Paint().apply { color = Color.parseColor("#FFD700"); isAntiAlias = true; style = Paint.Style.FILL }
    private val borderPaint = Paint().apply { color = Color.parseColor("#DAA520"); strokeWidth = 10f; style = Paint.Style.STROKE }

    init {
        setBackgroundColor(Color.parseColor("#F5F5F5")); setLayerType(LAYER_TYPE_SOFTWARE, null); isHapticFeedbackEnabled = true
        val audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        soundPool = SoundPool.Builder().setMaxStreams(10).setAudioAttributes(audioAttributes).build()
        loadSounds()
        setCoinCount(1)
    }

    private fun loadSounds() {
        val f1 = File(context.filesDir, "custom_impact.mp3")
        if (f1.exists()) impactSoundId = soundPool.load(f1.absolutePath, 1)
        else {
            val resId = context.resources.getIdentifier("coin_impact", "raw", context.packageName)
            if (resId != 0) impactSoundId = soundPool.load(context, resId, 1)
        }
        val f2 = File(context.filesDir, "custom_scrape.mp3")
        if (f2.exists()) scrapeSoundId = soundPool.load(f2.absolutePath, 1)
        else {
            val resId = context.resources.getIdentifier("coin_scrape", "raw", context.packageName)
            if (resId != 0) scrapeSoundId = soundPool.load(context, resId, 1)
        }
    }

    fun reloadImpactSound() { impactSoundId = soundPool.load(File(context.filesDir, "custom_impact.mp3").absolutePath, 1) }
    fun reloadScrapeSound() { scrapeSoundId = soundPool.load(File(context.filesDir, "custom_scrape.mp3").absolutePath, 1) }

    fun setCoinCount(count: Int) {
        while (coins.size < count) coins.add(Coin(nextCoinId++, width / 2f + random.nextFloat() * 30, height / 2f + random.nextFloat() * 30))
        while (coins.size > count) coins.removeAt(coins.size - 1)
        invalidate()
    }

    private fun updateCalculatedValues() {
        if (width > 0) {
            val minDim = min(width, height); val maxR = minDim / 4.5f; val minR = 20f
            coinRadius = minR + (maxR - minR) * radiusPercent
            accelerationMultiplier = 2.8f - (weightPercent * 2.5f)
            currentFriction = 0.90f + (glidePercent * 0.098f)
            vibrationIntensityFactor = 0.4f + (vibrationPercent * 2.6f)
            vibrationThreshold = 1.5f - (vibrationPercent * 1.3f)
            val shadowDist = 5f + glidePercent * 30f; val shadowBlur = 10f + glidePercent * 25f
            paint.setShadowLayer(shadowBlur, shadowDist, shadowDist, Color.argb(130, 0, 0, 0))
            invalidate()
        }
    }

    fun start() { sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME); postInvalidateOnAnimation() }
    fun stop() { sensorManager.unregisterListener(this) }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val rx = -event.values[0]; val ry = event.values[1]
            sensorX = if (abs(rx) < 0.25f) 0f else rx
            sensorY = if (abs(ry) < 0.25f) 0f else ry
        }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        for (c in coins) { c.x = w / 2f; c.y = h / 2f }
        updateCalculatedValues()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        var triggerVibrate = false; var triggerImpactSound = false; var triggerScrapeSound = false; var maxImpactVel = 0f
        val bounce = 0.65f; val hapticPuffer = 2.5f 

        for (c in coins) {
            c.vx += sensorX * 0.85f * accelerationMultiplier; c.vy += sensorY * 0.85f * accelerationMultiplier
            c.vx *= currentFriction; c.vy *= currentFriction
            c.x += c.vx; c.y += c.vy
        }

        repeat(3) {
            for (c in coins) {
                resolveWallCollision(c, bounce, hapticPuffer) { impact ->
                    triggerVibrate = true; if (impact > 1.5f) triggerImpactSound = true
                    maxImpactVel = Math.max(maxImpactVel, impact)
                }
            }
            for (i in 0 until coins.size) {
                for (j in i + 1 until coins.size) {
                    val c1 = coins[i]; val c2 = coins[j]; val dx = c2.x - c1.x; val dy = c2.y - c1.y
                    val distSq = dx * dx + dy * dy; val minDist = coinRadius * 2
                    if (distSq < minDist * minDist) {
                        val dist = sqrt(distSq.toDouble()).toFloat()
                        val nx: Float; val ny: Float; val overlap: Float
                        if (dist < 0.1f) {
                            val angle = random.nextFloat() * 2 * Math.PI.toFloat()
                            nx = Math.cos(angle.toDouble()).toFloat(); ny = Math.sin(angle.toDouble()).toFloat(); overlap = minDist
                        } else { nx = dx / dist; ny = dy / dist; overlap = minDist - dist }
                        val jitter = (random.nextFloat() - 0.5f) * 0.15f; val corr = (overlap / 2f) + 0.15f
                        c1.x -= (nx + jitter) * corr; c1.y -= (ny + jitter) * corr
                        c2.x += (nx + jitter) * corr; c2.y += (ny + jitter) * corr
                        val rvx = c2.vx - c1.vx; val rvy = c2.vy - c1.vy; val velAlongNormal = rvx * nx + rvy * ny
                        if (velAlongNormal < 0) {
                            val impact = abs(velAlongNormal)
                            if (!c1.collidingWith.contains(c2.id)) {
                                if (impact > vibrationThreshold) {
                                    triggerVibrate = true; if (impact > 1.8f) triggerImpactSound = true
                                    maxImpactVel = Math.max(maxImpactVel, impact)
                                }
                            } else {
                                val tx = -ny; val ty = nx; val tangentVel = rvx * tx + rvy * ty
                                if (abs(tangentVel) > 3.0f) triggerScrapeSound = true
                            }
                            val jImpulse = -(1 + bounce) * velAlongNormal
                            val ix = (jImpulse / 2f) * nx; val iy = (jImpulse / 2f) * ny
                            c1.vx -= ix; c1.vy -= iy; c2.vx += ix; c2.vy += iy
                            c1.collidingWith.add(c2.id); c2.collidingWith.add(c1.id)
                        }
                    } else { c1.collidingWith.remove(c2.id); c2.collidingWith.remove(c1.id) }
                }
            }
        }

        for (c in coins) { canvas.drawCircle(c.x, c.y, coinRadius, paint); canvas.drawCircle(c.x, c.y, coinRadius, borderPaint) }
        if (triggerVibrate) vibrate(maxImpactVel)
        if (triggerImpactSound) playImpactSound(maxImpactVel)
        if (triggerScrapeSound) playScrapeSound()
        postInvalidateOnAnimation()
    }

    private fun resolveWallCollision(c: Coin, bounce: Float, puffer: Float, onImpact: (Float) -> Unit) {
        val bottomLimit = height - (coinRadius + 600f)
        if (c.x < coinRadius) {
            val impact = abs(c.vx); c.x = coinRadius + 0.15f
            if (!c.wasHitLeft && impact > vibrationThreshold) onImpact(impact)
            c.vx *= -bounce; c.wasHitLeft = true
        } else if (c.x > coinRadius + puffer) c.wasHitLeft = false
        if (c.x > width - coinRadius) {
            val impact = abs(c.vx); c.x = width - coinRadius - 0.15f
            if (!c.wasHitRight && impact > vibrationThreshold) onImpact(impact)
            c.vx *= -bounce; c.wasHitRight = true
        } else if (c.x < width - coinRadius - puffer) c.wasHitRight = false
        if (c.y < coinRadius) {
            val impact = abs(c.vy); c.y = coinRadius + 0.15f
            if (!c.wasHitTop && impact > vibrationThreshold) onImpact(impact)
            c.vy *= -bounce; c.wasHitTop = true
        } else if (c.y > coinRadius + puffer) c.wasHitTop = false
        if (c.y > bottomLimit) {
            val impact = abs(c.vy); c.y = bottomLimit - 0.15f
            if (!c.wasHitBottom && impact > vibrationThreshold) onImpact(impact)
            c.vy *= -bounce; c.wasHitBottom = true
        } else if (c.y < bottomLimit - puffer) c.wasHitBottom = false
    }

    private fun playImpactSound(velocity: Float) {
        if (impactSoundId != -1) {
            val vol = (velocity / 25f).coerceIn(0.1f, 1.0f) * volumePercent
            soundPool.play(impactSoundId, vol, vol, 1, 0, 1f)
        }
    }

    private fun playScrapeSound() {
        val currentTime = System.currentTimeMillis()
        if (scrapeSoundId != -1 && currentTime - lastScrapeTime > SCRAPE_COOLDOWN) {
            lastScrapeTime = currentTime
            val vol = 0.2f * volumePercent; val rate = 0.85f + random.nextFloat() * 0.3f
            soundPool.play(scrapeSoundId, vol, vol, 0, 0, rate)
        }
    }

    private fun vibrate(velocity: Float) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastVibrationTime < VIBRATION_COOLDOWN) return
        lastVibrationTime = currentTime
        if (Build.MANUFACTURER.contains("samsung", ignoreCase = true)) {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intensity = (velocity * 35 * vibrationIntensityFactor).coerceIn(45f, 255f).toInt()
            val duration = (velocity * 2.5f * vibrationIntensityFactor).coerceIn(15f, 45f).toLong()
            try {
                val effect = VibrationEffect.createWaveform(longArrayOf(0, duration), intArrayOf(0, intensity), -1)
                vibrator.vibrate(effect)
            } catch (e: Exception) { @Suppress("DEPRECATION") vibrator.vibrate(duration) }
        } else { @Suppress("DEPRECATION") vibrator.vibrate(30) }
    }
}