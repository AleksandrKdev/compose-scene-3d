package dev.composescene3d.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Builds the capped cylindrical geometry used by a [LineNode]. */
fun LineNode.geometry(): Geometry3D = GeometryBuilder().apply {
    cylinder(start, end, radius, segments)
}.build()

/** Builds the capped shaft and conical head geometry used by an [ArrowNode]. */
fun ArrowNode.geometry(): Geometry3D {
    val direction = (end - start).normalized()
    val headStart = end - direction * headLength
    return GeometryBuilder().apply {
        cylinder(start, headStart, shaftRadius, segments)
        cone(headStart, end, headRadius, segments)
    }.build()
}

private class GeometryBuilder {
    private val positions = mutableListOf<Float>()
    private val normals = mutableListOf<Float>()
    private val indices = mutableListOf<Int>()

    fun cylinder(start: Vec3, end: Vec3, radius: Float, segments: Int) {
        val direction = (end - start).normalized()
        val (u, v) = direction.basis()
        val startRing = IntArray(segments)
        val endRing = IntArray(segments)
        repeat(segments) { segment ->
            val angle = 2f * PI.toFloat() * segment / segments
            val radial = u * cos(angle) + v * sin(angle)
            startRing[segment] = vertex(start + radial * radius, radial)
            endRing[segment] = vertex(end + radial * radius, radial)
        }
        repeat(segments) { segment ->
            val next = (segment + 1) % segments
            quad(startRing[segment], startRing[next], endRing[next], endRing[segment])
        }
        cap(start, start, direction * -1f, radius, u, v, segments, reverse = true)
        cap(end, end, direction, radius, u, v, segments, reverse = false)
    }

    fun cone(base: Vec3, tip: Vec3, radius: Float, segments: Int) {
        val direction = (tip - base).normalized()
        val height = (tip - base).length()
        val (u, v) = direction.basis()
        repeat(segments) { segment ->
            val next = (segment + 1) % segments
            fun radial(index: Int): Vec3 {
                val angle = 2f * PI.toFloat() * index / segments
                return u * cos(angle) + v * sin(angle)
            }
            val firstRadial = radial(segment)
            val nextRadial = radial(next)
            val first = vertex(base + firstRadial * radius, (firstRadial * height + direction * radius).normalized())
            val second = vertex(base + nextRadial * radius, (nextRadial * height + direction * radius).normalized())
            val apexNormal = (firstRadial + nextRadial + direction * (2f * radius / height)).normalized()
            val apex = vertex(tip, apexNormal)
            triangle(first, second, apex)
        }
        cap(base, base, direction * -1f, radius, u, v, segments, reverse = true)
    }

    private fun cap(
        center: Vec3,
        ringCenter: Vec3,
        normal: Vec3,
        radius: Float,
        u: Vec3,
        v: Vec3,
        segments: Int,
        reverse: Boolean,
    ) {
        val centerIndex = vertex(center, normal)
        val ring = IntArray(segments) { segment ->
            val angle = 2f * PI.toFloat() * segment / segments
            vertex(ringCenter + (u * cos(angle) + v * sin(angle)) * radius, normal)
        }
        repeat(segments) { segment ->
            val next = (segment + 1) % segments
            if (reverse) triangle(centerIndex, ring[next], ring[segment])
            else triangle(centerIndex, ring[segment], ring[next])
        }
    }

    private fun vertex(position: Vec3, normal: Vec3): Int {
        positions += listOf(position.x, position.y, position.z)
        normals += listOf(normal.x, normal.y, normal.z)
        return positions.size / 3 - 1
    }

    private fun triangle(a: Int, b: Int, c: Int) { indices += listOf(a, b, c) }
    private fun quad(a: Int, b: Int, c: Int, d: Int) {
        triangle(a, b, c)
        triangle(a, c, d)
    }

    fun build() = Geometry3D(positions.toFloatArray(), indices.toIntArray(), normals.toFloatArray())
}

private operator fun Vec3.plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
private operator fun Vec3.minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
private operator fun Vec3.times(value: Float) = Vec3(x * value, y * value, z * value)
private fun Vec3.length() = sqrt(x * x + y * y + z * z)
private fun Vec3.normalized(): Vec3 = this * (1f / length())
private fun Vec3.cross(other: Vec3) = Vec3(
    y * other.z - z * other.y,
    z * other.x - x * other.z,
    x * other.y - y * other.x,
)
private fun Vec3.basis(): Pair<Vec3, Vec3> {
    val reference = if (abs(y) < 0.9f) Vec3(0f, 1f, 0f) else Vec3(1f, 0f, 0f)
    val u = reference.cross(this).normalized()
    return u to this.cross(u).normalized()
}
