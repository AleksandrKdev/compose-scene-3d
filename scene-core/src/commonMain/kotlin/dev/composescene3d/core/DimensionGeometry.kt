package dev.composescene3d.core

import kotlin.math.sqrt

/** Builds two arrow halves and two extension lines for this dimension. */
fun LinearDimensionNode.geometry(): List<Geometry3D> {
    val dimensionStart = start + offset
    val dimensionEnd = end + offset
    val midpoint = (dimensionStart + dimensionEnd) * 0.5f
    val extensionDirection = offset.normalized()
    val firstExtensionStart = start + extensionDirection * extensionGap
    val secondExtensionStart = end + extensionDirection * extensionGap
    val firstExtensionEnd = dimensionStart + extensionDirection * extensionOvershoot
    val secondExtensionEnd = dimensionEnd + extensionDirection * extensionOvershoot
    return listOf(
        ArrowNode(
            key, midpoint, dimensionStart, radius, arrowHeadRadius, arrowHeadLength,
            segments, material, castShadows = castShadows, receiveShadows = receiveShadows,
        ).geometry(),
        ArrowNode(
            key, midpoint, dimensionEnd, radius, arrowHeadRadius, arrowHeadLength,
            segments, material, castShadows = castShadows, receiveShadows = receiveShadows,
        ).geometry(),
        LineNode(
            key, firstExtensionStart, firstExtensionEnd, radius, segments, material,
            castShadows = castShadows, receiveShadows = receiveShadows,
        ).geometry(),
        LineNode(
            key, secondExtensionStart, secondExtensionEnd, radius, segments, material,
            castShadows = castShadows, receiveShadows = receiveShadows,
        ).geometry(),
    )
}

private operator fun Vec3.plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
private operator fun Vec3.times(value: Float) = Vec3(x * value, y * value, z * value)
private fun Vec3.normalized(): Vec3 {
    val length = sqrt(x * x + y * y + z * z)
    return this * (1f / length)
}
