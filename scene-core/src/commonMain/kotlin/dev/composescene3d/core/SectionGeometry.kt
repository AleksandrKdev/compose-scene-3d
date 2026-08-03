package dev.composescene3d.core

import kotlin.math.abs
import kotlin.math.sqrt

/** CPU geometry produced by clipping a mesh and optionally closing its cut contour. */
data class SectionGeometry3D(
    val surface: Geometry3D?,
    val cap: Geometry3D?,
)

/**
 * Clips triangles against [plane] and closes concave, disconnected, or nested cut contours. The
 * plane uses local mesh coordinates.
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
    val cutSegments = mutableListOf<CutSegment>()

    for (triangle in indices.indices step 3) {
        val polygon = indices.sliceArray(triangle..triangle + 2).map { vertex(it) }
        val clipped = mutableListOf<SectionVertex>()
        val triangleIntersections = mutableListOf<Vec3>()
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
                triangleIntersections += intersection.position
            }
            if (currentInside) clipped += current
        }
        if (clipped.size >= 3) {
            for (index in 1 until clipped.lastIndex) output.triangle(clipped[0], clipped[index], clipped[index + 1])
        }
        val endpoints = triangleIntersections.distinctWithin(1e-5f)
        if (endpoints.size == 2) cutSegments += CutSegment(endpoints[0], endpoints[1])
    }

    val surface = output.buildOrNull()
    val contours = cutSegments.closedContours(1e-4f)
    if (contours.isEmpty()) return SectionGeometry3D(surface, null)

    val reference = if (abs(normal.y) < 0.9f) Vec3(0f, 1f, 0f) else Vec3(1f, 0f, 0f)
    val u = reference.cross(normal).normalized()
    val v = normal.cross(u).normalized()
    val capNormal = normal * -1f
    val cap = VertexCollector(hasUvs = true)
    val allPoints = contours.flatten()
    val minU = allPoints.minOf { it.dot(u) }
    val minV = allPoints.minOf { it.dot(v) }
    val extent = maxOf(
        allPoints.maxOf { it.dot(u) } - minU,
        allPoints.maxOf { it.dot(v) } - minV,
    ).coerceAtLeast(1e-6f)
    val planarContours = contours.map { contour ->
        contour.map { point -> PlanarPoint(point, Point2D(point.dot(u), point.dot(v))) }
    }
    planarContours.triangulateNestedContours().forEach { triangle ->
        fun capVertex(point: PlanarPoint): SectionVertex {
            return SectionVertex(
                point.position, capNormal,
                (point.point.x - minU) / extent,
                (point.point.y - minV) / extent,
            )
        }
        // Ear clipping returns counter-clockwise triangles facing +normal; reverse for the cap.
        cap.triangle(capVertex(triangle[0]), capVertex(triangle[2]), capVertex(triangle[1]))
    }
    return SectionGeometry3D(surface, cap.buildOrNull())
}

private data class CutSegment(val first: Vec3, val second: Vec3)

private fun List<CutSegment>.closedContours(epsilon: Float): List<List<Vec3>> {
    val remaining = toMutableList()
    val contours = mutableListOf<List<Vec3>>()
    while (remaining.isNotEmpty()) {
        val seed = remaining.removeAt(remaining.lastIndex)
        val contour = mutableListOf(seed.first, seed.second)
        while ((contour.last() - contour.first()).length() > epsilon) {
            val end = contour.last()
            val index = remaining.indexOfFirst {
                (it.first - end).length() <= epsilon || (it.second - end).length() <= epsilon
            }
            if (index < 0) break
            val segment = remaining.removeAt(index)
            contour += if ((segment.first - end).length() <= epsilon) segment.second else segment.first
        }
        if (contour.size >= 4 && (contour.last() - contour.first()).length() <= epsilon) {
            contours += contour.dropLast(1).removeCollinear(epsilon)
        }
    }
    return contours.filter { it.size >= 3 }
}

private fun List<Vec3>.removeCollinear(epsilon: Float): List<Vec3> = filterIndexed { index, point ->
    val previous = this[(index + lastIndex) % size]
    val next = this[(index + 1) % size]
    (point - previous).cross(next - point).length() > epsilon
}

private data class Point2D(val x: Float, val y: Float)
private data class PlanarPoint(val position: Vec3, val point: Point2D)

private fun List<List<PlanarPoint>>.triangulateNestedContours(): List<List<PlanarPoint>> {
    val depths = map { contour ->
        count { candidate -> candidate !== contour && contour[0].point.insidePolygon(candidate.map { it.point }) }
    }
    val triangles = mutableListOf<List<PlanarPoint>>()
    indices.filter { depths[it] % 2 == 0 }.forEach { outerIndex ->
        var polygon = this[outerIndex].counterClockwise()
        val holes = indices.filter { holeIndex ->
            depths[holeIndex] == depths[outerIndex] + 1 &&
                this[holeIndex][0].point.insidePolygon(this[outerIndex].map { it.point })
        }
        holes.sortedByDescending { hole -> this[hole].maxOf { it.point.x } }.forEach { holeIndex ->
            polygon = polygon.bridge(this[holeIndex].clockwise())
        }
        polygon.map { it.point }.earTriangles().forEach { triangle ->
            triangles += triangle.map(polygon::get)
        }
    }
    return triangles
}

private fun List<PlanarPoint>.counterClockwise() =
    if (map { it.point }.signedArea() >= 0f) this else reversed()
private fun List<PlanarPoint>.clockwise() =
    if (map { it.point }.signedArea() <= 0f) this else reversed()

private fun List<PlanarPoint>.bridge(hole: List<PlanarPoint>): List<PlanarPoint> {
    val holeIndex = hole.indices.maxBy { hole[it].point.x }
    val holePoint = hole[holeIndex].point
    val candidates = indices.sortedBy { index -> holePoint.distanceSquared(this[index].point) }
    val outerIndex = candidates.firstOrNull { index ->
        val outerPoint = this[index].point
        val midpoint = Point2D((holePoint.x + outerPoint.x) / 2f, (holePoint.y + outerPoint.y) / 2f)
        midpoint.insidePolygon(map { it.point }) && !midpoint.insidePolygon(hole.map { it.point }) &&
            bridgeDoesNotCross(holePoint, outerPoint, map { it.point }, hole.map { it.point })
    } ?: candidates.first()
    return buildList {
        addAll(this@bridge.take(outerIndex + 1))
        addAll(hole.drop(holeIndex))
        addAll(hole.take(holeIndex + 1))
        addAll(this@bridge.drop(outerIndex))
    }
}

private fun bridgeDoesNotCross(
    first: Point2D,
    second: Point2D,
    outer: List<Point2D>,
    hole: List<Point2D>,
): Boolean = listOf(outer, hole).all { polygon ->
    polygon.indices.all { index ->
        val edgeFirst = polygon[index]
        val edgeSecond = polygon[(index + 1) % polygon.size]
        edgeFirst == first || edgeSecond == first || edgeFirst == second || edgeSecond == second ||
            !segmentsIntersect(first, second, edgeFirst, edgeSecond)
    }
}

private fun segmentsIntersect(a: Point2D, b: Point2D, c: Point2D, d: Point2D): Boolean {
    val abC = cross(a, b, c)
    val abD = cross(a, b, d)
    val cdA = cross(c, d, a)
    val cdB = cross(c, d, b)
    return abC * abD < -1e-7f && cdA * cdB < -1e-7f
}

private fun Point2D.insidePolygon(polygon: List<Point2D>): Boolean {
    var inside = false
    var previous = polygon.last()
    polygon.forEach { current ->
        if ((current.y > y) != (previous.y > y) &&
            x < (previous.x - current.x) * (y - current.y) / (previous.y - current.y) + current.x
        ) inside = !inside
        previous = current
    }
    return inside
}

private fun Point2D.distanceSquared(other: Point2D): Float =
    (x - other.x) * (x - other.x) + (y - other.y) * (y - other.y)

private fun List<Point2D>.earTriangles(): List<IntArray> {
    if (size < 3) return emptyList()
    val remaining = indices.toMutableList()
    if (signedArea() < 0f) remaining.reverse()
    val triangles = mutableListOf<IntArray>()
    var attempts = 0
    while (remaining.size > 3 && attempts < size * size) {
        var earFound = false
        for (position in remaining.indices) {
            val previous = remaining[(position + remaining.lastIndex) % remaining.size]
            val current = remaining[position]
            val next = remaining[(position + 1) % remaining.size]
            if (cross(this[previous], this[current], this[next]) <= 1e-7f) continue
            if (remaining.any { candidate ->
                    candidate != previous && candidate != current && candidate != next &&
                        this[candidate].insideTriangle(this[previous], this[current], this[next])
                }) continue
            triangles += intArrayOf(previous, current, next)
            remaining.removeAt(position)
            earFound = true
            break
        }
        if (!earFound) break
        attempts++
    }
    if (remaining.size == 3) triangles += remaining.toIntArray()
    return triangles
}

private fun List<Point2D>.signedArea(): Float = indices.sumOf { index ->
    val next = this[(index + 1) % size]
    (this[index].x * next.y - next.x * this[index].y).toDouble()
}.toFloat() / 2f

private fun Point2D.insideTriangle(a: Point2D, b: Point2D, c: Point2D): Boolean {
    val first = cross(a, b, this)
    val second = cross(b, c, this)
    val third = cross(c, a, this)
    return first > 1e-7f && second > 1e-7f && third > 1e-7f
}

private fun cross(a: Point2D, b: Point2D, c: Point2D) =
    (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

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
