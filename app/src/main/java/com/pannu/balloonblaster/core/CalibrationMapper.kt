package com.pannu.balloonblaster.core

import kotlin.math.cos
import kotlin.math.sin

data class CalibrationSettings(
    var mirrorX: Boolean = false,
    var mirrorY: Boolean = false,
    var scaleX: Float = 1f,
    var scaleY: Float = 1f,
    var offsetX: Float = 0f,
    var offsetY: Float = 0f,
    var rotationDegrees: Float = 0f
)

object CalibrationMapper {
    fun mapNormalized(
        inputX: Float,
        inputY: Float,
        outputWidth: Float,
        outputHeight: Float,
        settings: CalibrationSettings
    ): Vec2 {
        var x = if (settings.mirrorX) 1f - inputX else inputX
        var y = if (settings.mirrorY) 1f - inputY else inputY

        x = (x - 0.5f) * settings.scaleX
        y = (y - 0.5f) * settings.scaleY

        val radians = Math.toRadians(settings.rotationDegrees.toDouble())
        val c = cos(radians).toFloat()
        val s = sin(radians).toFloat()
        val rotatedX = x * c - y * s
        val rotatedY = x * s + y * c

        return Vec2(
            (rotatedX + 0.5f + settings.offsetX) * outputWidth,
            (rotatedY + 0.5f + settings.offsetY) * outputHeight
        )
    }
}
