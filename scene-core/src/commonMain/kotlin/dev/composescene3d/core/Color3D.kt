package dev.composescene3d.core

import kotlin.math.pow

enum class ColorSpace3D {
    Srgb,
    LinearSrgb,
}

data class Color3D(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float = 1f,
    val colorSpace: ColorSpace3D = ColorSpace3D.Srgb,
) {
    init {
        require(red.isFinite() && green.isFinite() && blue.isFinite()) {
            "Color channels must be finite"
        }
        require(red >= 0f && green >= 0f && blue >= 0f) {
            "Color channels cannot be negative"
        }
        require(alpha in 0f..1f) { "Alpha must be between 0 and 1" }
        if (colorSpace == ColorSpace3D.Srgb) {
            require(red <= 1f && green <= 1f && blue <= 1f) {
                "sRGB color channels must be between 0 and 1"
            }
        }
    }

    fun toLinearSrgb(): Color3D = when (colorSpace) {
        ColorSpace3D.LinearSrgb -> this
        ColorSpace3D.Srgb -> Color3D(
            red = red.srgbToLinear(),
            green = green.srgbToLinear(),
            blue = blue.srgbToLinear(),
            alpha = alpha,
            colorSpace = ColorSpace3D.LinearSrgb,
        )
    }

    companion object {
        val Black = Color3D(0f, 0f, 0f)
        val White = Color3D(1f, 1f, 1f)
        val Red = Color3D(1f, 0f, 0f)
        val Green = Color3D(0f, 1f, 0f)
        val Blue = Color3D(0f, 0f, 1f)
        val Yellow = Color3D(1f, 1f, 0f)
        val Cyan = Color3D(0f, 1f, 1f)
        val Magenta = Color3D(1f, 0f, 1f)
        val Transparent = Color3D(0f, 0f, 0f, alpha = 0f)

        fun rgb(red: Int, green: Int, blue: Int): Color3D =
            rgba(red, green, blue, 255)

        fun rgba(red: Int, green: Int, blue: Int, alpha: Int): Color3D {
            require(red in 0..255 && green in 0..255 && blue in 0..255 && alpha in 0..255) {
                "8-bit color channels must be between 0 and 255"
            }
            return Color3D(red / 255f, green / 255f, blue / 255f, alpha / 255f)
        }

        fun argb(value: Int): Color3D = rgba(
            red = value ushr 16 and 0xff,
            green = value ushr 8 and 0xff,
            blue = value and 0xff,
            alpha = value ushr 24 and 0xff,
        )
    }
}

private fun Float.srgbToLinear(): Float =
    if (this <= 0.04045f) this / 12.92f else ((this + 0.055f) / 1.055f).pow(2.4f)
