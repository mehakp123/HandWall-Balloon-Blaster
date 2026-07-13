package com.pannu.balloonblaster.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

data class Balloon(
    var position: Vec2,
    var velocity: Vec2,
    var radius: Float,
    var colorIndex: Int,
    var wobblePhase: Float,
    var active: Boolean = true,
    var respawnAtMs: Long = 0L
)

data class Bullet(
    var position: Vec2,
    var previousPosition: Vec2,
    var velocity: Vec2,
    var radius: Float = 7f,
    var lifeSeconds: Float = 1.8f
)

data class PopParticle(
    var position: Vec2,
    var velocity: Vec2,
    var lifeSeconds: Float,
    val totalLifeSeconds: Float,
    val colorIndex: Int
)

data class PopEvent(val position: Vec2, val colorIndex: Int)

data class BalloonConfig(
    var balloonSpeed: Float = 1f,
    var balloonSize: Float = 1f,
    var bulletSpeed: Float = 1550f,
    var particleCount: Int = 24
)

class BalloonEngine(seed: Long = 20260714L) {
    private val random = Random(seed)
    private var width = 1280f
    private var height = 720f
    private var requestedBalloonCount = 10
    private var simulatedTimeSeconds = 0f

    val balloons: MutableList<Balloon> = mutableListOf()
    val bullets: MutableList<Bullet> = mutableListOf()
    val particles: MutableList<PopParticle> = mutableListOf()
    val config = BalloonConfig()

    init { syncBalloonCount(0L) }

    fun setBounds(newWidth: Float, newHeight: Float) {
        if (newWidth > 1f) width = newWidth
        if (newHeight > 1f) height = newHeight
        balloons.filter { it.active }.forEach { keepInside(it) }
    }

    fun setBalloonCount(count: Int, nowMs: Long = 0L) {
        requestedBalloonCount = count.coerceIn(1, 40)
        syncBalloonCount(nowMs)
    }

    fun reset(nowMs: Long = 0L) {
        balloons.clear()
        bullets.clear()
        particles.clear()
        simulatedTimeSeconds = 0f
        syncBalloonCount(nowMs)
    }

    fun fire(origin: Vec2, direction: Vec2) {
        val safeDirection = direction.normalized(Vec2(1f, 0f))
        bullets += Bullet(
            position = origin + safeDirection * 10f,
            previousPosition = origin,
            velocity = safeDirection * config.bulletSpeed
        )
        if (bullets.size > 30) bullets.removeAt(0)
    }

    fun step(deltaSeconds: Float, nowMs: Long): List<PopEvent> {
        val dt = deltaSeconds.coerceIn(0.001f, 0.034f)
        simulatedTimeSeconds += dt
        syncBalloonCount(nowMs)
        updateBalloons(dt, nowMs)
        updateBullets(dt)
        val pops = detectHits(nowMs)
        updateParticles(dt)
        return pops
    }

    private fun syncBalloonCount(nowMs: Long) {
        while (balloons.size < requestedBalloonCount) {
            balloons += makeBalloon(balloons.size, nowMs)
        }
        while (balloons.size > requestedBalloonCount) balloons.removeAt(balloons.lastIndex)
    }

    private fun makeBalloon(index: Int, nowMs: Long): Balloon {
        val balloon = Balloon(
            position = Vec2(),
            velocity = Vec2(),
            radius = 48f,
            colorIndex = index % 8,
            wobblePhase = random.nextFloat() * 6.28318f
        )
        respawn(balloon, nowMs, anywhere = true)
        return balloon
    }

    private fun respawn(balloon: Balloon, nowMs: Long, anywhere: Boolean) {
        balloon.radius = random.nextInt(38, 62) * config.balloonSize
        val horizontalMargin = balloon.radius * 0.9f + 8f
        val verticalMargin = balloon.radius + 8f
        balloon.position.x = random.nextFloat() * max(1f, width - horizontalMargin * 2f) + horizontalMargin
        balloon.position.y = if (anywhere) {
            random.nextFloat() * max(1f, height - verticalMargin * 2f) + verticalMargin
        } else {
            height + balloon.radius * 1.5f
        }
        balloon.velocity = Vec2(
            random.nextFloat() * 42f - 21f,
            -(18f + random.nextFloat() * 32f)
        ) * config.balloonSpeed
        balloon.colorIndex = random.nextInt(0, 8)
        balloon.wobblePhase = random.nextFloat() * 6.28318f
        balloon.active = true
        balloon.respawnAtMs = nowMs
        keepInside(balloon)
    }

    private fun updateBalloons(dt: Float, nowMs: Long) {
        balloons.forEachIndexed { index, balloon ->
            if (!balloon.active) {
                if (nowMs >= balloon.respawnAtMs) respawn(balloon, nowMs, anywhere = false)
                return@forEachIndexed
            }

            val wobble = sin(simulatedTimeSeconds * (0.75f + index * 0.015f) + balloon.wobblePhase)
            balloon.velocity.x += wobble * 8f * config.balloonSpeed * dt
            balloon.position.x += balloon.velocity.x * dt
            balloon.position.y += balloon.velocity.y * dt

            val rx = balloon.radius * 0.78f
            val ry = balloon.radius
            if (balloon.position.x - rx < 0f) {
                balloon.position.x = rx
                balloon.velocity.x = abs(balloon.velocity.x)
            } else if (balloon.position.x + rx > width) {
                balloon.position.x = width - rx
                balloon.velocity.x = -abs(balloon.velocity.x)
            }

            if (balloon.position.y + ry < -10f) {
                balloon.position.y = height + ry
                balloon.position.x = random.nextFloat() * max(1f, width - rx * 2f) + rx
            } else if (balloon.position.y - ry > height + ry) {
                balloon.position.y = height + ry
                balloon.velocity.y = -abs(balloon.velocity.y)
            }
        }
    }

    private fun updateBullets(dt: Float) {
        val iterator = bullets.iterator()
        while (iterator.hasNext()) {
            val bullet = iterator.next()
            bullet.previousPosition = bullet.position.copy()
            bullet.position.x += bullet.velocity.x * dt
            bullet.position.y += bullet.velocity.y * dt
            bullet.lifeSeconds -= dt
            val outside = bullet.position.x < -80f || bullet.position.x > width + 80f ||
                bullet.position.y < -80f || bullet.position.y > height + 80f
            if (bullet.lifeSeconds <= 0f || outside || !bullet.position.finite()) iterator.remove()
        }
    }

    private fun detectHits(nowMs: Long): List<PopEvent> {
        val events = mutableListOf<PopEvent>()
        val bulletIterator = bullets.iterator()
        while (bulletIterator.hasNext()) {
            val bullet = bulletIterator.next()
            var hit: Balloon? = null
            for (balloon in balloons) {
                if (!balloon.active) continue
                val collisionRadius = balloon.radius * 0.82f + bullet.radius
                if (distancePointToSegment(balloon.position, bullet.previousPosition, bullet.position) <= collisionRadius) {
                    hit = balloon
                    break
                }
            }
            if (hit != null) {
                val popPosition = hit.position.copy()
                val color = hit.colorIndex
                hit.active = false
                hit.respawnAtMs = nowMs + random.nextLong(750L, 1550L)
                createParticles(popPosition, hit.radius, color)
                bulletIterator.remove()
                events += PopEvent(popPosition, color)
            }
        }
        return events
    }

    private fun createParticles(position: Vec2, radius: Float, colorIndex: Int) {
        repeat(config.particleCount.coerceIn(6, 80)) {
            val angle = random.nextFloat() * 6.28318f
            val speed = random.nextFloat() * 380f + 120f
            val direction = Vec2(kotlin.math.cos(angle), kotlin.math.sin(angle))
            val life = random.nextFloat() * 0.42f + 0.35f
            particles += PopParticle(
                position = position.copy(),
                velocity = direction * speed + Vec2(0f, -radius * 1.5f),
                lifeSeconds = life,
                totalLifeSeconds = life,
                colorIndex = colorIndex
            )
        }
        if (particles.size > 600) particles.subList(0, particles.size - 600).clear()
    }

    private fun updateParticles(dt: Float) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            particle.velocity.y += 520f * dt
            particle.position.x += particle.velocity.x * dt
            particle.position.y += particle.velocity.y * dt
            particle.lifeSeconds -= dt
            if (particle.lifeSeconds <= 0f || !particle.position.finite()) iterator.remove()
        }
    }

    private fun keepInside(balloon: Balloon) {
        val rx = balloon.radius * 0.78f
        balloon.position.x = balloon.position.x.coerceIn(rx, max(rx, width - rx))
        balloon.position.y = balloon.position.y.coerceIn(-balloon.radius, height + balloon.radius)
    }

    private fun distancePointToSegment(point: Vec2, start: Vec2, end: Vec2): Float {
        val segment = end - start
        val lengthSq = segment.lengthSquared()
        val t = if (lengthSq > 0.0001f) {
            ((point - start).dot(segment) / lengthSq).coerceIn(0f, 1f)
        } else 0f
        return point.distanceTo(start + segment * t)
    }
}
