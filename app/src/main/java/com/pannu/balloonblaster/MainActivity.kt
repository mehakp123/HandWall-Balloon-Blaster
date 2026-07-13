package com.pannu.balloonblaster

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pannu.balloonblaster.core.CalibrationSettings
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity(), HandTrackingController.Listener {

    private lateinit var root: FrameLayout
    private lateinit var blasterView: BalloonBlasterView
    private lateinit var controlsPanel: View
    private lateinit var statusText: TextView
    private lateinit var cameraExecutor: ExecutorService
    private var handTrackingController: HandTrackingController? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var controlsVisible = true

    private val prefs by lazy { getSharedPreferences("balloon_blaster_settings", MODE_PRIVATE) }

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCameraAndTracker()
        else updateStatus("Camera permission denied — use Touch Test or Test Shot")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        enterImmersiveMode()

        cameraExecutor = Executors.newSingleThreadExecutor()
        buildInterface()
        loadSavedSettings()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCameraAndTracker()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun buildInterface() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        blasterView = BalloonBlasterView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setOnLongClickListener {
                showControls(true)
                true
            }
        }
        root.addView(blasterView)
        controlsPanel = createControlsPanel()
        root.addView(controlsPanel)
        setContentView(root)
    }

    private fun createControlsPanel(): View {
        val panelWidth = dp(388)
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.argb(240, 18, 20, 28))
            isFillViewport = true
            layoutParams = FrameLayout.LayoutParams(
                panelWidth,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.END
            )
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(28))
        }
        scroll.addView(content)

        content.addView(TextView(this).apply {
            text = "HANDWALL BALLOON BLASTER"
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        statusText = TextView(this).apply {
            text = "Starting rear camera…"
            textSize = 13f
            setTextColor(Color.rgb(101, 230, 255))
            setPadding(0, dp(5), 0, dp(8))
        }
        content.addView(statusText)

        content.addView(TextView(this).apply {
            text = "Pose: index finger straight, thumb raised, other fingers curled. Aim at a balloon, then flick the index fingertip slightly upward and return it to fire."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(8))
        })

        content.addView(Button(this).apply {
            text = "START SHOW / HIDE CONTROLS"
            setOnClickListener { showControls(false) }
        }, fullWidthParams())

        content.addView(Button(this).apply {
            text = "TEST SHOT"
            setOnClickListener { blasterView.fireTestShot() }
        }, fullWidthParams())

        content.addView(Button(this).apply {
            text = "RESET BALLOONS AND SCORE"
            setOnClickListener { blasterView.resetBalloons() }
        }, fullWidthParams())

        content.addView(sectionTitle("Balloons and shooting"))
        addSeek(content, "Number of balloons", 3, 28, prefs.getInt("balloonCount", 10)) { value ->
            prefs.edit().putInt("balloonCount", value).apply()
            blasterView.setBalloonCount(value)
        }
        addSeek(content, "Balloon movement speed %", 20, 220, prefs.getInt("balloonSpeed", 85)) { value ->
            prefs.edit().putInt("balloonSpeed", value).apply()
            blasterView.setBalloonSpeed(value)
        }
        addSeek(content, "Balloon size %", 55, 150, prefs.getInt("balloonSize", 100)) { value ->
            prefs.edit().putInt("balloonSize", value).apply()
            blasterView.setBalloonSize(value)
        }
        addSeek(content, "Bullet speed", 700, 3000, prefs.getInt("bulletSpeed", 1550)) { value ->
            prefs.edit().putInt("bulletSpeed", value).apply()
            blasterView.setBulletSpeed(value)
        }
        addSeek(content, "Pop particles", 8, 60, prefs.getInt("particles", 24)) { value ->
            prefs.edit().putInt("particles", value).apply()
            blasterView.setParticleCount(value)
        }
        addSeek(content, "Pop sound volume %", 0, 100, prefs.getInt("volume", 85)) { value ->
            prefs.edit().putInt("volume", value).apply()
            blasterView.popVolume = value / 100f
        }

        content.addView(sectionTitle("Finger-gun trigger"))
        content.addView(TextView(this).apply {
            text = "Lower sensitivity value = smaller upward flick needed. Raise it if accidental shots happen."
            textSize = 12f
            setTextColor(Color.LTGRAY)
        })
        addSeek(content, "Recoil movement % of palm", 5, 35, prefs.getInt("recoil", 13)) { value ->
            prefs.edit().putInt("recoil", value).apply()
            blasterView.setRecoilSensitivity(value)
        }
        addSeek(content, "Time between shots ms", 220, 1200, prefs.getInt("cooldown", 430)) { value ->
            prefs.edit().putInt("cooldown", value).apply()
            blasterView.setShotCooldown(value)
        }
        addSeek(content, "Hand smoothing", 20, 85, prefs.getInt("smoothing", 54)) { value ->
            prefs.edit().putInt("smoothing", value).apply()
            blasterView.handSmoothing = value / 100f
        }

        content.addView(sectionTitle("Sensor alignment"))
        content.addView(TextView(this).apply {
            text = "Keep the phone fixed close to the projector lens. Adjust until the green hand and blue aiming line are projected over your real hand and pointing direction."
            textSize = 12f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(6))
        })

        content.addView(checkBox("Show green hand guide", prefs.getBoolean("showHand", true)) { checked ->
            prefs.edit().putBoolean("showHand", checked).apply()
            blasterView.showHandGuide = checked
        })
        content.addView(checkBox("Show blue aiming guide", prefs.getBoolean("showAim", true)) { checked ->
            prefs.edit().putBoolean("showAim", checked).apply()
            blasterView.showAimGuide = checked
        })
        content.addView(checkBox("Show projection border", prefs.getBoolean("showBorder", true)) { checked ->
            prefs.edit().putBoolean("showBorder", checked).apply()
            blasterView.showProjectionBorder = checked
        })
        content.addView(checkBox("Show score", prefs.getBoolean("showScore", true)) { checked ->
            prefs.edit().putBoolean("showScore", checked).apply()
            blasterView.showScore = checked
        })
        content.addView(checkBox("Mirror hand horizontally", prefs.getBoolean("mirrorX", false)) { checked ->
            prefs.edit().putBoolean("mirrorX", checked).apply()
            updateCalibrationFromPrefs()
        })
        content.addView(checkBox("Mirror hand vertically", prefs.getBoolean("mirrorY", false)) { checked ->
            prefs.edit().putBoolean("mirrorY", checked).apply()
            updateCalibrationFromPrefs()
        })

        addSeek(content, "Horizontal offset", -50, 50, prefs.getInt("offsetX", 0)) { value ->
            prefs.edit().putInt("offsetX", value).apply()
            updateCalibrationFromPrefs()
        }
        addSeek(content, "Vertical offset", -50, 50, prefs.getInt("offsetY", 0)) { value ->
            prefs.edit().putInt("offsetY", value).apply()
            updateCalibrationFromPrefs()
        }
        addSeek(content, "Horizontal scale", 40, 180, prefs.getInt("scaleX", 100)) { value ->
            prefs.edit().putInt("scaleX", value).apply()
            updateCalibrationFromPrefs()
        }
        addSeek(content, "Vertical scale", 40, 180, prefs.getInt("scaleY", 100)) { value ->
            prefs.edit().putInt("scaleY", value).apply()
            updateCalibrationFromPrefs()
        }
        addSeek(content, "Sensor rotation", -35, 35, prefs.getInt("sensorRotation", 0)) { value ->
            prefs.edit().putInt("sensorRotation", value).apply()
            updateCalibrationFromPrefs()
        }

        content.addView(sectionTitle("Testing"))
        content.addView(checkBox("Touch test mode: tap screen to shoot", false) { checked ->
            blasterView.touchTestMode = checked
            if (checked) updateStatus("Touch test active — tap a balloon on the phone screen")
            else updateStatus("Rear camera finger-gun tracking active")
        })

        content.addView(Button(this).apply {
            text = "RESET SENSOR ALIGNMENT"
            setOnClickListener {
                prefs.edit()
                    .putBoolean("mirrorX", false)
                    .putBoolean("mirrorY", false)
                    .putInt("offsetX", 0)
                    .putInt("offsetY", 0)
                    .putInt("scaleX", 100)
                    .putInt("scaleY", 100)
                    .putInt("sensorRotation", 0)
                    .apply()
                recreate()
            }
        }, fullWidthParams())

        content.addView(TextView(this).apply {
            text = "Use Smart View / Screen Cast before Start Show. Long-press the screen or press Volume Down to reopen controls. Keep media volume up for the pop sound. The app works offline after installation."
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, dp(12), 0, 0)
        })
        return scroll
    }

    private fun sectionTitle(textValue: String): TextView = TextView(this).apply {
        text = textValue
        textSize = 16f
        setTextColor(Color.WHITE)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(16), 0, dp(6))
    }

    private fun checkBox(textValue: String, initial: Boolean, onChange: (Boolean) -> Unit): CheckBox =
        CheckBox(this).apply {
            text = textValue
            isChecked = initial
            setTextColor(Color.WHITE)
            textSize = 13f
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }

    private fun addSeek(
        parent: LinearLayout,
        label: String,
        minimum: Int,
        maximum: Int,
        initial: Int,
        onChange: (Int) -> Unit
    ) {
        val rowLabel = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            textSize = 13f
            text = "$label: $initial"
            setPadding(0, dp(5), 0, 0)
        }
        val seekBar = SeekBar(this).apply {
            max = maximum - minimum
            progress = initial.coerceIn(minimum, maximum) - minimum
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress + minimum
                    rowLabel.text = "$label: $value"
                    onChange(value)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        parent.addView(rowLabel)
        parent.addView(seekBar, fullWidthParams())
        onChange(initial.coerceIn(minimum, maximum))
    }

    private fun fullWidthParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { setMargins(0, dp(4), 0, dp(4)) }

    private fun loadSavedSettings() {
        blasterView.setBalloonCount(prefs.getInt("balloonCount", 10))
        blasterView.setBalloonSpeed(prefs.getInt("balloonSpeed", 85))
        blasterView.setBalloonSize(prefs.getInt("balloonSize", 100))
        blasterView.setBulletSpeed(prefs.getInt("bulletSpeed", 1550))
        blasterView.setParticleCount(prefs.getInt("particles", 24))
        blasterView.setRecoilSensitivity(prefs.getInt("recoil", 13))
        blasterView.setShotCooldown(prefs.getInt("cooldown", 430))
        blasterView.popVolume = prefs.getInt("volume", 85) / 100f
        blasterView.handSmoothing = prefs.getInt("smoothing", 54) / 100f
        blasterView.showHandGuide = prefs.getBoolean("showHand", true)
        blasterView.showAimGuide = prefs.getBoolean("showAim", true)
        blasterView.showProjectionBorder = prefs.getBoolean("showBorder", true)
        blasterView.showScore = prefs.getBoolean("showScore", true)
        updateCalibrationFromPrefs()
    }

    private fun updateCalibrationFromPrefs() {
        blasterView.calibration = CalibrationSettings(
            mirrorX = prefs.getBoolean("mirrorX", false),
            mirrorY = prefs.getBoolean("mirrorY", false),
            scaleX = prefs.getInt("scaleX", 100) / 100f,
            scaleY = prefs.getInt("scaleY", 100) / 100f,
            offsetX = prefs.getInt("offsetX", 0) / 100f,
            offsetY = prefs.getInt("offsetY", 0) / 100f,
            rotationDegrees = prefs.getInt("sensorRotation", 0).toFloat()
        )
    }

    private fun startCameraAndTracker() {
        if (handTrackingController == null) {
            handTrackingController = HandTrackingController(applicationContext, this)
        }
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()
                val analysis = ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(android.util.Size(640, 480))
                    .build()
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    handTrackingController?.process(imageProxy) ?: imageProxy.close()
                }
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
                updateStatus("Rear camera active — make a finger-gun pose")
            } catch (error: Exception) {
                updateStatus("Camera start failed: ${error.message ?: error.javaClass.simpleName}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showControls(show: Boolean) {
        controlsVisible = show
        controlsPanel.visibility = if (show) View.VISIBLE else View.GONE
        blasterView.showProjectionBorder = if (show) prefs.getBoolean("showBorder", true) else false
        blasterView.showHandGuide = if (show) prefs.getBoolean("showHand", true) else false
        enterImmersiveMode()
    }

    private fun enterImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun updateStatus(message: String) {
        runOnUiThread { statusText.text = message }
    }

    override fun onHands(hands: List<List<NormalizedPoint>>, timestampMs: Long) {
        if (!blasterView.touchTestMode) blasterView.setHands(hands, timestampMs)
    }

    override fun onStatus(message: String) = updateStatus(message)
    override fun onError(message: String) = updateStatus(message)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveMode()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            showControls(!controlsVisible)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        handTrackingController?.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
