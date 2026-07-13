package com.pannu.balloonblaster

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.pannu.balloonblaster.core.BalloonEngine
import com.pannu.balloonblaster.core.CalibrationMapper
import com.pannu.balloonblaster.core.CalibrationSettings
import com.pannu.balloonblaster.core.FingerGunDetector
import com.pannu.balloonblaster.core.FingerGunSettings
import com.pannu.balloonblaster.core.GunDetection
import com.pannu.balloonblaster.core.HandGeometry
import com.pannu.balloonblaster.core.Vec2
import kotlin.math.min

class BalloonBlasterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val engine = BalloonEngine()
    private val gunDetector = FingerGunDetector()
    private var gunSettings = FingerGunSettings()

    private val balloonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 230, 230, 245)
        strokeWidth = 2.2f
        style = Paint.Style.STROKE
    }
    private val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bulletTrailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 4f }
    private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(70, 255, 180)
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val aimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 101, 230, 255)
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 255, 255, 255)
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 27f
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 30f
        setShadowLayer(6f, 0f, 2f, Color.BLACK)
    }

    private val palette = intArrayOf(
        Color.rgb(255, 73, 142),
        Color.rgb(84, 218, 255),
        Color.rgb(255, 207, 58),
        Color.rgb(104, 242, 139),
        Color.rgb(172, 102, 255),
        Color.rgb(255, 118, 61),
        Color.rgb(66, 142, 255),
        Color.rgb(255, 94, 221)
    )

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(5)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val popSoundId: Int
    private var soundReady = false

    @Volatile
    private var latestHands: List<List<NormalizedPoint>> = emptyList()
    private var latestHandTimestampMs: Long = 0L
    private var processedHandTimestampMs: Long = -1L
    private var previousMapTimestampMs: Long = 0L
    private var previousMappedHand: List<Vec2> = emptyList()
    private var mappedHand: List<Vec2> = emptyList()
    private var currentGun = GunDetection(false, false, Vec2(), Vec2(1f, 0f), 0f)
    private var lastFrameNs: Long = 0L
    private var muzzleFlashUntilMs: Long = 0L
    private var pops = 0
    private var shots = 0

    var calibration = CalibrationSettings()
        set(value) {
            field = value.copy()
            previousMappedHand = emptyList()
            processedHandTimestampMs = -1L
            gunDetector.reset()
        }

    var showHandGuide: Boolean = true
    var showAimGuide: Boolean = true
    var showProjectionBorder: Boolean = true
    var showScore: Boolean = true
    var touchTestMode: Boolean = false
    var handSmoothing: Float = 0.54f
    var handTimeoutMs: Long = 350L
    var popVolume: Float = 0.85f

    init {
        setBackgroundColor(Color.BLACK)
        isLongClickable = true
        isFocusable = true
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) soundReady = true
        }
        popSoundId = soundPool.load(context, R.raw.balloon_pop, 1)
    }

    fun setHands(hands: List<List<NormalizedPoint>>, timestampMs: Long) {
        latestHands = hands
        latestHandTimestampMs = timestampMs
    }

    fun clearHands() {
        latestHands = emptyList()
        previousMappedHand = emptyList()
        mappedHand = emptyList()
        processedHandTimestampMs = -1L
        gunDetector.reset()
        currentGun = currentGun.copy(isGunPose = false, shouldFire = false)
    }

    fun setBalloonCount(count: Int) = engine.setBalloonCount(count, SystemClock.uptimeMillis())
    fun setBalloonSpeed(percent: Int) { engine.config.balloonSpeed = percent.coerceIn(20, 220) / 100f }
    fun setBalloonSize(percent: Int) { engine.config.balloonSize = percent.coerceIn(55, 150) / 100f }
    fun setBulletSpeed(value: Int) { engine.config.bulletSpeed = value.coerceIn(700, 3000).toFloat() }
    fun setParticleCount(value: Int) { engine.config.particleCount = value.coerceIn(6, 80) }
    fun setRecoilSensitivity(percentOfPalm: Int) {
        gunSettings.recoilDistancePalmFraction = percentOfPalm.coerceIn(5, 35) / 100f
    }
    fun setShotCooldown(milliseconds: Int) { gunSettings.cooldownMs = milliseconds.coerceIn(220, 1200).toLong() }
    fun resetBalloons() {
        engine.reset(SystemClock.uptimeMillis())
        pops = 0
        shots = 0
    }

    fun fireTestShot() {
        val target = engine.balloons.firstOrNull { it.active }?.position
            ?: Vec2(width * 0.72f, height * 0.35f)
        val origin = Vec2(width * 0.18f, height * 0.82f)
        fire(origin, target - origin)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        engine.setBounds(w.toFloat(), h.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val nowNs = System.nanoTime()
        val dt = if (lastFrameNs == 0L) 1f / 60f else (nowNs - lastFrameNs) / 1_000_000_000f
        lastFrameNs = nowNs
        val nowMs = SystemClock.uptimeMillis()

        updateMappedHand()
        val popEvents = engine.step(dt, nowMs)
        if (popEvents.isNotEmpty()) {
            pops += popEvents.size
            repeat(popEvents.size) { playPop() }
        }

        drawBalloons(canvas)
        drawBullets(canvas)
        drawParticles(canvas)
        if (showAimGuide) drawAimGuide(canvas)
        if (showHandGuide) drawHand(canvas)
        if (nowMs < muzzleFlashUntilMs) drawMuzzleFlash(canvas)
        if (showScore) canvas.drawText("POPS $pops   SHOTS $shots", 24f, 42f, scorePaint)
        if (showProjectionBorder) canvas.drawRect(3f, 3f, width - 3f, height - 3f, borderPaint)

        postInvalidateOnAnimation()
    }

    private fun updateMappedHand() {
        val nowMs = SystemClock.uptimeMillis()
        if (nowMs - latestHandTimestampMs > handTimeoutMs || latestHands.isEmpty()) {
            if (mappedHand.isNotEmpty()) clearHands()
            return
        }
        if (latestHandTimestampMs == processedHandTimestampMs) return
        processedHandTimestampMs = latestHandTimestampMs

        val raw = latestHands.first().map { point ->
            CalibrationMapper.mapNormalized(
                point.x, point.y,
                width.toFloat().coerceAtLeast(1f),
                height.toFloat().coerceAtLeast(1f),
                calibration
            )
        }

        val dt = if (previousMapTimestampMs == 0L) 1f / 30f
        else ((latestHandTimestampMs - previousMapTimestampMs) / 1000f).coerceIn(0.01f, 0.15f)
        previousMapTimestampMs = latestHandTimestampMs

        val smoothed = raw.mapIndexed { index, current ->
            val old = previousMappedHand.getOrNull(index)
            if (old == null) current else old * (1f - handSmoothing) + current * handSmoothing
        }
        previousMappedHand = smoothed
        mappedHand = smoothed

        currentGun = gunDetector.update(smoothed, latestHandTimestampMs, gunSettings)
        if (currentGun.shouldFire) fire(currentGun.muzzle, currentGun.direction)
    }

    private fun fire(origin: Vec2, direction: Vec2) {
        engine.fire(origin, direction)
        shots += 1
        muzzleFlashUntilMs = SystemClock.uptimeMillis() + 90L
    }

    private fun drawBalloons(canvas: Canvas) {
        val stringPath = Path()
        for (balloon in engine.balloons) {
            if (!balloon.active) continue
            val color = palette[balloon.colorIndex % palette.size]
            val rx = balloon.radius * 0.78f
            val ry = balloon.radius
            val oval = RectF(
                balloon.position.x - rx,
                balloon.position.y - ry,
                balloon.position.x + rx,
                balloon.position.y + ry
            )

            balloonPaint.shader = RadialGradient(
                balloon.position.x - rx * 0.34f,
                balloon.position.y - ry * 0.42f,
                balloon.radius * 1.35f,
                intArrayOf(Color.WHITE, color, darken(color, 0.62f)),
                floatArrayOf(0f, 0.28f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawOval(oval, balloonPaint)
            balloonPaint.shader = null

            balloonPaint.color = darken(color, 0.78f)
            val knot = Path().apply {
                moveTo(balloon.position.x, balloon.position.y + ry * 0.88f)
                lineTo(balloon.position.x - rx * 0.16f, balloon.position.y + ry * 1.10f)
                lineTo(balloon.position.x + rx * 0.16f, balloon.position.y + ry * 1.10f)
                close()
            }
            canvas.drawPath(knot, balloonPaint)

            stringPath.reset()
            val startY = balloon.position.y + ry * 1.10f
            stringPath.moveTo(balloon.position.x, startY)
            stringPath.cubicTo(
                balloon.position.x - rx * 0.20f, startY + ry * 0.28f,
                balloon.position.x + rx * 0.18f, startY + ry * 0.58f,
                balloon.position.x, startY + ry * 0.92f
            )
            canvas.drawPath(stringPath, stringPaint)
        }
    }

    private fun drawBullets(canvas: Canvas) {
        for (bullet in engine.bullets) {
            val color = Color.rgb(255, 236, 92)
            bulletTrailPaint.shader = LinearGradient(
                bullet.previousPosition.x, bullet.previousPosition.y,
                bullet.position.x, bullet.position.y,
                Color.TRANSPARENT, color, Shader.TileMode.CLAMP
            )
            canvas.drawLine(
                bullet.previousPosition.x, bullet.previousPosition.y,
                bullet.position.x, bullet.position.y, bulletTrailPaint
            )
            bulletTrailPaint.shader = null

            bulletPaint.shader = RadialGradient(
                bullet.position.x, bullet.position.y, 18f,
                intArrayOf(Color.WHITE, color, Color.TRANSPARENT),
                floatArrayOf(0f, 0.34f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawCircle(bullet.position.x, bullet.position.y, 18f, bulletPaint)
            bulletPaint.shader = null
        }
    }

    private fun drawParticles(canvas: Canvas) {
        for (particle in engine.particles) {
            val color = palette[particle.colorIndex % palette.size]
            val alpha = (255f * (particle.lifeSeconds / particle.totalLifeSeconds)).toInt().coerceIn(0, 255)
            particlePaint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
            val tail = particle.velocity.normalized() * -9f
            canvas.drawLine(
                particle.position.x, particle.position.y,
                particle.position.x + tail.x, particle.position.y + tail.y,
                particlePaint
            )
        }
    }

    private fun drawHand(canvas: Canvas) {
        if (mappedHand.size < 21) {
            textPaint.alpha = 155
            canvas.drawText("Make a finger-gun pose toward the rear camera", 24f, height - 26f, textPaint)
            return
        }
        for ((a, b) in HandGeometry.connections) {
            if (a < mappedHand.size && b < mappedHand.size) {
                canvas.drawLine(mappedHand[a].x, mappedHand[a].y, mappedHand[b].x, mappedHand[b].y, handPaint)
            }
        }
        mappedHand.forEach { canvas.drawCircle(it.x, it.y, 5.5f, handPaint) }
    }

    private fun drawAimGuide(canvas: Canvas) {
        if (!currentGun.isGunPose) return
        val end = rayToScreenEdge(currentGun.muzzle, currentGun.direction)
        canvas.drawLine(currentGun.muzzle.x, currentGun.muzzle.y, end.x, end.y, aimPaint)
        canvas.drawCircle(end.x, end.y, 13f, aimPaint)
        canvas.drawLine(end.x - 19f, end.y, end.x + 19f, end.y, aimPaint)
        canvas.drawLine(end.x, end.y - 19f, end.x, end.y + 19f, aimPaint)
    }

    private fun drawMuzzleFlash(canvas: Canvas) {
        val muzzle = currentGun.muzzle
        val direction = currentGun.direction
        val side = Vec2(-direction.y, direction.x)
        val flash = Path().apply {
            moveTo(muzzle.x, muzzle.y)
            val p1 = muzzle + direction * 35f + side * 15f
            val p2 = muzzle + direction * 22f
            val p3 = muzzle + direction * 35f - side * 15f
            lineTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p3.x, p3.y)
            close()
        }
        bulletPaint.shader = null
        bulletPaint.color = Color.rgb(255, 236, 92)
        canvas.drawPath(flash, bulletPaint)
    }

    private fun rayToScreenEdge(origin: Vec2, direction: Vec2): Vec2 {
        val d = direction.normalized(Vec2(1f, 0f))
        val candidates = mutableListOf<Float>()
        if (d.x > 0.0001f) candidates += (width - origin.x) / d.x
        if (d.x < -0.0001f) candidates += (0f - origin.x) / d.x
        if (d.y > 0.0001f) candidates += (height - origin.y) / d.y
        if (d.y < -0.0001f) candidates += (0f - origin.y) / d.y
        val t = candidates.filter { it > 0f }.minOrNull() ?: 1000f
        return origin + d * t
    }

    private fun playPop() {
        if (soundReady && popVolume > 0.001f) {
            soundPool.play(popSoundId, popVolume, popVolume, 1, 0, 1f)
        }
    }

    private fun darken(color: Int, factor: Float): Int = Color.rgb(
        (Color.red(color) * factor).toInt().coerceIn(0, 255),
        (Color.green(color) * factor).toInt().coerceIn(0, 255),
        (Color.blue(color) * factor).toInt().coerceIn(0, 255)
    )

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!touchTestMode) return super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val origin = Vec2(width * 0.50f, height * 0.92f)
            fire(origin, Vec2(event.x, event.y) - origin)
            return true
        }
        return true
    }

    override fun onDetachedFromWindow() {
        soundPool.release()
        super.onDetachedFromWindow()
    }
}

data class NormalizedPoint(val x: Float, val y: Float)
