package dev.composescene3d.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/** CPU geometry produced by clipping a mesh and optionally closing its cut contour. */
data class SectionGeometry3D(
    val surface: Geometry3D?,
    val cap: Geometry3D?,
)

/**
 * Clips triangles against [plane] and closes a convex cut contour. The plane uses local mesh
 * coordinates. Multiple disconnected contours and contours with holes require separate meshes.
 */
fun Geometry3D.section(plane: ClippingPlane3D): SectionGeometry3D {
    val normalLength = sqrt(
        plane.normal.x * plane.normal.x + plane.normal.y * plane.normal.y +
            plane.normal.z * plane.normal.z,
    )
    val sign = if (plane.keepPositive) 1f else -1f
    val normal = plane.normal * (sign / normalLength)
    val offset = plane.offset * sign / normalLength
    val output = VertexCollector(uvs != null)
    val intersections = mutableListOf<Vec3>()

    for (triangle in indices.indices step 3) {
        val polygon = indices.sliceArray(triangle..triangle + 2).map { vertex(it) }
        val clipped = mutableListOf<SectionVertex>()
        polygon.forEachIndexed { index, current ->
            val previous = polygon[(index + polygon.lastIndex) % polygon.size]
            val currentDistance = normal.dot(current.position) - offset
            val previousDistance = normal.dot(previous.position) - offset
            val currentInside = currentDistance >= 0f
            val previousInside = previousDistance >= 0f
            if (currentInside != previousInside) {
                val amount = previousDistance / (previousDistance - currentDistance)
                val intersection = previous.interpolate(current, amount)
                clipped += intersection
                intersections += intersection.position
            }
            if (currentInside) clipped += current
        }
        if (clipped.size >= 3) {
            for (index in 1 until clipped.lastIndex) output.triangle(clipped[0], clipped[index], clipped[index + 1])
        }
    }

    val surface = output.buildOrNull()
    val unique = intersections.distinctWithin(1e-4f)
    if (unique.size < 3) return SectionGeometry3D(surface, null)

    val center = unique.reduce(Vec3::plus) * (1f / unique.size)
    val reference = if (abs(normal.y) < 0.9f) Vec3(0f, 1f, 0f) else Vec3(1f, 0f, 0f)
    val u = reference.cross(normal).normalized()
    val v = normal.cross(u).normalized()
    val ring = unique.sortedBy { point ->
        val relative = point - center
        atan2(relative.dot(v), relative.dot(u))
    }
    val capNormal = normal * -1f
    val cap = VertexCollector(hasUvs = true)
    val centerVertex = SectionVertex(center, capNormal, 0.5f, 0.5f)
    val extent = ring.maxOf { (it - center).length() }.coerceAtLeast(1e-6f)
    ring.forEachIndexed { index, point ->
        val next = ring[(index + 1) % ring.size]
        fun capVertex(value: Vec3): SectionVertex {
            val relative = value - center
            return SectionVertex(
                value, capNormal,
                0.5f + relative.dot(u) / (2f * extent),
                0.5f + relative.dot(v) / (2f * extent),
            )
        }
        cap.triangle(centerVertex, capVertex(next), capVertex(point))
    }
    return SectionGeometry3D(surface, cap.buildOrNull())
}

private data class SectionVertex(
    val position: Vec3,
    val normal: Vec3,
    val u: Float? = null,
    val v: Float? = null,
) {
    fun interpolate(other: SectionVertex, amount: Float) = SectionVertex(
        position.lerp(other.position, amount),
        normal.lerp(other.normal, amount).normalized(),
        if (u == null || other.u == null) null else u + (other.u - u) * amount,
        if (v == null || other.v == null) null else v + (other.v - v) * amount,
    )
}

private class VertexCollector(private val hasUvs: Boolean) {
    private val positions = mutableListOf<Float>()
    private val normals = mutableListOf<Float>()
    private val uvs = mutableListOf<Float>()
    private val indices = mutableListOf<Int>()

    fun triangle(first: SectionVertex, second: SectionVertex, third: SectionVertex) {
        listOf(first, second, third).forEach { vertex ->
            positions += listOf(vertex.position.x, vertex.position.y, vertex.position.z)
            normals += listOf(vertex.normal.x, vertex.normal.y, vertex.normal.z)
            if (hasUvs) uvs += listOf(vertex.u ?: 0f, vertex.v ?: 0f)
            indices += indices.size
        }
    }

    fun buildOrNull(): Geometry3D? = if (indices.isEmpty()) null else Geometry3D(
        positions.toFloatArray(), indices.toIntArray(), normals.toFloatArray(),
        if (hasUvs) uvs.toFloatArray() else null,
    )
}

private fun Geometry3D.vertex(index: Int) = SectionVertex(
    Vec3(positions[index * 3], positions[index * 3 + 1], positions[index * 3 + 2]),
    Vec3(normals[index * 3], normals[index * 3 + 1], normals[index * 3 + 2]),
    uvs?.get(index * 2), uvs?.get(index * 2 + 1),
)
private fun List<Vec3>.distinctWithin(epsilon: Float): List<Vec3> = fold(mutableListOf()) { result, point ->
    if (result.none { (it - point).length() <= epsilon }) result += point
    result
}
private operator fun Vec3.plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
private operator fun Vec3.minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
private operator fun Vec3.times(value: Float) = Vec3(x * value, y * value, z * value)
private fun Vec3.dot(other: Vec3) = x * other.x + y * other.y + z * other.z
private fun Vec3.cross(other: Vec3) = Vec3(
    y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x,
)
private fun Vec3.length() = sqrt(dot(this))
private fun Vec3.normalized() = this * (1f / length())
private fun Vec3.lerp(other: Vec3, amount: Float) = this + (other - this) * amount
