package dev.composescene3d.core

data class Vec3(val x: Float, val y: Float, val z: Float) {
    companion object {
        val Zero = Vec3(0f, 0f, 0f)
        val One = Vec3(1f, 1f, 1f)
    }
}

data class Quaternion(val x: Float, val y: Float, val z: Float, val w: Float) {
    companion object {
        val Identity = Quaternion(0f, 0f, 0f, 1f)
    }
}

data class Transform(
    val translation: Vec3 = Vec3.Zero,
    val rotation: Quaternion = Quaternion.Identity,
    val scale: Vec3 = Vec3.One,
)
