package com.pannu.balloonblaster.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BalloonBlasterCoreTest {
    @Test
    fun fingerGunPoseIsRecognized() {
        val pose = syntheticGunHand()
        val detection = FingerGunDetector.analyzePose(pose)
        assertTrue(detection.isGunPose)
        assertTrue(detection.direction.x > 0.9f)
    }

    @Test
    fun openHandIsNotFingerGun() {
        val hand = syntheticGunHand().toMutableList()
        hand[12] = Vec2(1.02f, 0.18f)
        hand[16] = Vec2(0.96f, 0.34f)
        hand[20] = Vec2(0.88f, 0.48f)
        assertFalse(FingerGunDetector.analyzePose(hand).isGunPose)
    }

    @Test
    fun upwardRecoilFiresOnceAndRequiresReset() {
        val detector = FingerGunDetector()
        val settings = FingerGunSettings(
            recoilDistancePalmFraction = 0.10f,
            recoilVelocityPalmPerSecond = 0.30f,
            cooldownMs = 300L,
            armDelayMs = 80L
        )
        val neutral = syntheticGunHand()
        assertFalse(detector.update(neutral, 0L, settings).shouldFire)
        assertFalse(detector.update(neutral, 100L, settings).shouldFire)

        val recoil = moveIndexTipAndDipUp(neutral, 0.08f)
        val firstShot = detector.update(recoil, 150L, settings)
        assertTrue(firstShot.shouldFire)
        assertTrue("Recoil should keep the pre-recoil aim", firstShot.direction.x > 0.9f)
        assertFalse(detector.update(moveIndexTipAndDipUp(neutral, 0.10f), 180L, settings).shouldFire)
        assertFalse(detector.update(neutral, 260L, settings).shouldFire)
        assertFalse(detector.update(neutral, 500L, settings).shouldFire)
        assertTrue(detector.update(recoil, 560L, settings).shouldFire)
    }

    @Test
    fun bulletPopsBalloon() {
        val engine = BalloonEngine(7)
        engine.setBounds(1000f, 700f)
        engine.setBalloonCount(1)
        val balloon = engine.balloons.first()
        balloon.active = true
        balloon.radius = 45f
        balloon.position = Vec2(500f, 350f)
        balloon.velocity = Vec2()
        engine.config.bulletSpeed = 1000f
        engine.fire(Vec2(100f, 350f), Vec2(1f, 0f))

        var popped = false
        var time = 0L
        repeat(40) {
            time += 16L
            if (engine.step(0.016f, time).isNotEmpty()) popped = true
        }
        assertTrue(popped)
        assertFalse(balloon.active)
        assertTrue(engine.particles.isNotEmpty())
    }

    @Test
    fun calibrationMirrorWorks() {
        val p = CalibrationMapper.mapNormalized(
            0.25f, 0.75f, 1000f, 800f,
            CalibrationSettings(mirrorX = true)
        )
        assertEquals(750f, p.x, 0.01f)
        assertEquals(600f, p.y, 0.01f)
    }

    @Test
    fun longStressRunStaysFinite() {
        val engine = BalloonEngine(11)
        engine.setBounds(1920f, 1080f)
        engine.setBalloonCount(28)
        var now = 0L
        repeat(18_000) { frame ->
            if (frame % 24 == 0) {
                val target = engine.balloons.firstOrNull { it.active }?.position ?: Vec2(1500f, 400f)
                engine.fire(Vec2(100f, 900f), target - Vec2(100f, 900f))
            }
            now += 16L
            engine.step(1f / 60f, now)
        }
        assertTrue(engine.balloons.all { it.position.finite() && it.velocity.finite() })
        assertTrue(engine.bullets.all { it.position.finite() && it.velocity.finite() })
        assertTrue(engine.particles.all { it.position.finite() && it.velocity.finite() })
    }

    private fun syntheticGunHand(): List<Vec2> {
        val p = MutableList(21) { Vec2() }
        p[0] = Vec2(0f, 0f)
        p[1] = Vec2(0.12f, -0.04f)
        p[2] = Vec2(0.24f, -0.14f)
        p[3] = Vec2(0.38f, -0.25f)
        p[4] = Vec2(0.55f, -0.37f)

        p[5] = Vec2(0.22f, -0.03f)
        p[6] = Vec2(0.50f, -0.03f)
        p[7] = Vec2(0.78f, -0.03f)
        p[8] = Vec2(1.08f, -0.03f)

        p[9] = Vec2(0.18f, 0.18f)
        p[10] = Vec2(0.38f, 0.17f)
        p[11] = Vec2(0.30f, 0.31f)
        p[12] = Vec2(0.15f, 0.20f)

        p[13] = Vec2(0.12f, 0.30f)
        p[14] = Vec2(0.30f, 0.31f)
        p[15] = Vec2(0.22f, 0.44f)
        p[16] = Vec2(0.08f, 0.31f)

        p[17] = Vec2(0.04f, 0.39f)
        p[18] = Vec2(0.21f, 0.41f)
        p[19] = Vec2(0.13f, 0.53f)
        p[20] = Vec2(-0.01f, 0.40f)
        return p
    }

    private fun moveIndexTipAndDipUp(hand: List<Vec2>, amount: Float): List<Vec2> {
        val moved = hand.map { it.copy() }.toMutableList()
        moved[7].y -= amount * 0.7f
        moved[8].y -= amount
        return moved
    }
}
