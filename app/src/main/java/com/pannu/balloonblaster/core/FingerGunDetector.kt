package com.pannu.balloonblaster.core

import kotlin.math.acos
import kotlin.math.max

data class FingerGunSettings(
    var recoilDistancePalmFraction: Float = 0.13f,
    var recoilVelocityPalmPerSecond: Float = 0.55f,
    var cooldownMs: Long = 430L,
    var armDelayMs: Long = 110L
)

data class GunDetection(
    val isGunPose: Boolean,
    val shouldFire: Boolean,
    val muzzle: Vec2,
    val direction: Vec2,
    val confidence: Float
)

class FingerGunDetector {
    private var previousPose = false
    private var poseSinceMs = 0L
    private var previousTimestampMs = 0L
    private var previousRelativeY = 0f
    private var baselineRelativeY = 0f
    private var lastShotMs = Long.MIN_VALUE / 4
    private var recoilLatched = false
    private var stableDirection = Vec2(1f, 0f)
    private var stableMuzzle = Vec2()

    fun reset() {
        previousPose = false
        poseSinceMs = 0L
        previousTimestampMs = 0L
        previousRelativeY = 0f
        baselineRelativeY = 0f
        recoilLatched = false
        stableDirection = Vec2(1f, 0f)
        stableMuzzle = Vec2()
    }

    fun update(
        points: List<Vec2>,
        timestampMs: Long,
        settings: FingerGunSettings = FingerGunSettings()
    ): GunDetection {
        val pose = analyzePose(points)
        if (!pose.isGunPose) {
            reset()
            return pose
        }

        val wrist = points[0]
        val tip = points[8]
        val palmSize = max(0.0001f, wrist.distanceTo(points[9]))
        val relativeY = (tip.y - wrist.y) / palmSize

        if (!previousPose) {
            previousPose = true
            poseSinceMs = timestampMs
            previousTimestampMs = timestampMs
            previousRelativeY = relativeY
            baselineRelativeY = relativeY
            recoilLatched = false
            stableDirection = pose.direction
            stableMuzzle = pose.muzzle
            return pose
        }

        val dt = ((timestampMs - previousTimestampMs) / 1000f).coerceIn(0.008f, 0.2f)
        val upwardVelocity = (previousRelativeY - relativeY) / dt
        val upwardDistance = baselineRelativeY - relativeY
        val armed = timestampMs - poseSinceMs >= settings.armDelayMs
        val cooledDown = timestampMs - lastShotMs >= settings.cooldownMs

        var shouldFire = false
        var outputDirection = stableDirection
        var outputMuzzle = pose.muzzle
        if (!recoilLatched && armed && cooledDown &&
            upwardDistance >= settings.recoilDistancePalmFraction &&
            upwardVelocity >= settings.recoilVelocityPalmPerSecond
        ) {
            shouldFire = true
            recoilLatched = true
            lastShotMs = timestampMs
            outputDirection = stableDirection
            outputMuzzle = pose.muzzle
        }

        if (recoilLatched) {
            val resetLevel = baselineRelativeY - settings.recoilDistancePalmFraction * 0.30f
            if (relativeY >= resetLevel) {
                recoilLatched = false
                baselineRelativeY = relativeY
            }
        } else if (upwardVelocity < settings.recoilVelocityPalmPerSecond * 0.35f) {
            baselineRelativeY = baselineRelativeY * 0.92f + relativeY * 0.08f
            stableDirection = (stableDirection * 0.70f + pose.direction * 0.30f).normalized(pose.direction)
            stableMuzzle = stableMuzzle * 0.65f + pose.muzzle * 0.35f
            outputDirection = stableDirection
            outputMuzzle = stableMuzzle
        }

        previousTimestampMs = timestampMs
        previousRelativeY = relativeY
        return pose.copy(
            shouldFire = shouldFire,
            muzzle = outputMuzzle,
            direction = outputDirection
        )
    }

    companion object {
        fun analyzePose(points: List<Vec2>): GunDetection {
            if (points.size < 21) return emptyDetection()

            val wrist = points[0]
            val palmSize = wrist.distanceTo(points[9]).coerceAtLeast(0.0001f)
            val indexDirection = (points[8] - points[6]).normalized(Vec2(1f, 0f))
            val muzzle = points[8] + indexDirection * (palmSize * 0.08f)

            val indexExtended = isExtended(points, 5, 6, 8, 1.08f)
            val middleCurled = isCurled(points, 9, 10, 12)
            val ringCurled = isCurled(points, 13, 14, 16)
            val pinkyCurled = isCurled(points, 17, 18, 20)
            val curledCount = listOf(middleCurled, ringCurled, pinkyCurled).count { it }

            val thumbOpen = points[4].distanceTo(points[5]) > palmSize * 0.42f &&
                points[4].distanceTo(wrist) > points[3].distanceTo(wrist) * 1.02f

            val longEnough = points[8].distanceTo(points[6]) > palmSize * 0.42f
            val isGun = indexExtended && thumbOpen && curledCount >= 2 && longEnough

            var confidence = 0f
            if (indexExtended) confidence += 0.35f
            if (thumbOpen) confidence += 0.25f
            confidence += curledCount / 3f * 0.30f
            if (longEnough) confidence += 0.10f

            return GunDetection(
                isGunPose = isGun,
                shouldFire = false,
                muzzle = muzzle,
                direction = indexDirection,
                confidence = confidence.coerceIn(0f, 1f)
            )
        }

        private fun isExtended(
            points: List<Vec2>,
            mcp: Int,
            pip: Int,
            tip: Int,
            distanceRatio: Float
        ): Boolean {
            val wrist = points[0]
            val distanceExtended = points[tip].distanceTo(wrist) >
                points[pip].distanceTo(wrist) * distanceRatio
            val angleExtended = jointAngle(points[mcp], points[pip], points[tip]) > 135f
            return distanceExtended && angleExtended
        }

        private fun isCurled(points: List<Vec2>, mcp: Int, pip: Int, tip: Int): Boolean {
            val wrist = points[0]
            val tipCloser = points[tip].distanceTo(wrist) < points[pip].distanceTo(wrist) * 1.08f
            val bent = jointAngle(points[mcp], points[pip], points[tip]) < 125f
            return tipCloser || bent
        }

        private fun jointAngle(a: Vec2, b: Vec2, c: Vec2): Float {
            val ba = a - b
            val bc = c - b
            val denominator = ba.length() * bc.length()
            if (denominator < 0.0001f) return 0f
            val cosine = (ba.dot(bc) / denominator).coerceIn(-1f, 1f)
            return Math.toDegrees(acos(cosine).toDouble()).toFloat()
        }

        private fun emptyDetection() = GunDetection(
            isGunPose = false,
            shouldFire = false,
            muzzle = Vec2(),
            direction = Vec2(1f, 0f),
            confidence = 0f
        )
    }
}
