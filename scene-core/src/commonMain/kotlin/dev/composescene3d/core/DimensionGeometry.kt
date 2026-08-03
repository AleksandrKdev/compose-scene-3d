package dev.composescene3d.core

import kotlin.math.sqrt
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

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

fun RadialDimensionNode.geometry(): List<Geometry3D> {
    val direction = (edge - center).normalized()
    val leaderEnd = edge + direction * labelOffset
    return listOf(
        ArrowNode(key, leaderEnd, edge, radius, arrowHeadRadius, arrowHeadLength, segments, material).geometry(),
        LineNode(key, center, leaderEnd, radius, segments, material).geometry(),
    )
}

fun AngularDimensionNode.geometry(): List<Geometry3D> {
    val start = startDirection.normalized()
    val rawEnd = endDirection.normalized()
    val normal = start.cross(rawEnd).normalized()
    val tangent = normal.cross(start).normalized()
    val angle = acos(start.dot(rawEnd).coerceIn(-1f, 1f))
    require(angle > 0.001f) { "Angular dimension directions must not be parallel" }
    val points = (0..arcSegments).map { index ->
        val step = angle * index / arcSegments
        center + (start * cos(step) + tangent * sin(step)) * arcRadius
    }
    val result = mutableListOf<Geometry3D>()
    points.zipWithNext().forEach { (a, b) -> result += LineNode(key, a, b, radius, 8, material).geometry() }
    result += ArrowNode(key, points.first() + tangent * (arrowHeadLength * 1.5f), points.first(), radius, arrowHeadRadius, arrowHeadLength, 12, material).geometry()
    val endTangent = normal.cross(rawEnd).normalized()
    result += ArrowNode(key, points.last() - endTangent * (arrowHeadLength * 1.5f), points.last(), radius, arrowHeadRadius, arrowHeadLength, 12, material).geometry()
    result += LineNode(key, center, center + start * (arcRadius + radialOvershoot), radius, 8, material).geometry()
    result += LineNode(key, center, center + rawEnd * (arcRadius + radialOvershoot), radius, 8, material).geometry()
    return result
}

private operator fun Vec3.plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
private operator fun Vec3.minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
private operator fun Vec3.times(value: Float) = Vec3(x * value, y * value, z * value)
private fun Vec3.dot(other: Vec3) = x * other.x + y * other.y + z * other.z
private fun Vec3.cross(other: Vec3) = Vec3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x)
private fun Vec3.normalized(): Vec3 {
    val length = sqrt(x * x + y * y + z * z)
    return this * (1f / length)
}
