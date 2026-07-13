package com.pannu.balloonblaster.core

import kotlin.math.sqrt

data class Vec2(var x: Float = 0f, var y: Float = 0f) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(value: Float) = Vec2(x * value, y * value)
    operator fun div(value: Float) = Vec2(x / value, y / value)

    fun dot(other: Vec2): Float = x * other.x + y * other.y
    fun lengthSquared(): Float = x * x + y * y
    fun length(): Float = sqrt(lengthSquared())
    fun distanceTo(other: Vec2): Float = (this - other).length()

    fun normalized(fallback: Vec2 = Vec2(1f, 0f)): Vec2 {
        val len = length()
        return if (len > 0.0001f) this / len else fallback
    }

    fun finite(): Boolean = x.isFinite() && y.isFinite()
}
