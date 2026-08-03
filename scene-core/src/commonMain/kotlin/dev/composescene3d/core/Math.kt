package dev.composescene3d.core

/** Backend-neutral three-component vector. Scene distances use arbitrary, internally consistent units. */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    companion object {
        val Zero = Vec3(0f, 0f, 0f)
        val One = Vec3(1f, 1f, 1f)
    }
}

/** Rotation quaternion in `(x, y, z, w)` component order. */
data class Quaternion(val x: Float, val y: Float, val z: Float, val w: Float) {
    companion object {
        val Identity = Quaternion(0f, 0f, 0f, 1f)
    }
}

/** Local translation, rotation and scale applied in TRS order by renderer backends. */
data class Transform(
    val translation: Vec3 = Vec3.Zero,
    val rotation: Quaternion = Quaternion.Identity,
    val scale: Vec3 = Vec3.One,
)
