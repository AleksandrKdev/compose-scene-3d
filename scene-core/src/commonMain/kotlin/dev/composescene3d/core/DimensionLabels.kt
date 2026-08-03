package dev.composescene3d.core

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

data class DimensionTolerance(
    val upper: Double,
    val lower: Double = -upper,
) {
    init {
        require(upper >= 0.0 && upper.isFinite()) { "Upper tolerance must be finite and non-negative" }
        require(lower <= 0.0 && lower.isFinite()) { "Lower tolerance must be finite and non-positive" }
    }
}

/** Locale-independent formatting options for engineering dimension labels. */
data class DimensionTextFormat(
    val decimals: Int = 2,
    val unit: String = "",
    val prefix: String = "",
    val decimalSeparator: Char = '.',
    val trimTrailingZeros: Boolean = true,
    val spaceBeforeUnit: Boolean = true,
    val tolerance: DimensionTolerance? = null,
) {
    init {
        require(decimals in 0..9) { "Dimension decimals must be between 0 and 9" }
        require(decimalSeparator == '.' || decimalSeparator == ',') {
            "Decimal separator must be a dot or comma"
        }
    }
}

fun formatDimensionValue(value: Double, format: DimensionTextFormat = DimensionTextFormat()): String {
    require(value.isFinite()) { "Dimension value must be finite" }
    val main = formatNumber(value, format)
    val tolerance = format.tolerance?.let {
        " +${formatNumber(abs(it.upper), format)}/-${formatNumber(abs(it.lower), format)}"
    }.orEmpty()
    val unit = format.unit.takeIf(String::isNotEmpty)?.let {
        (if (format.spaceBeforeUnit) " " else "") + it
    }.orEmpty()
    return format.prefix + main + tolerance + unit
}

private fun formatNumber(value: Double, format: DimensionTextFormat): String {
    val factor = 10.0.pow(format.decimals)
    val rounded = round(value * factor) / factor
    val sign = if (rounded < 0.0) "-" else ""
    val absolute = abs(rounded)
    val whole = absolute.toLong()
    if (format.decimals == 0) return sign + whole
    val fraction = round((absolute - whole) * factor).toLong()
        .toString().padStart(format.decimals, '0')
        .let { if (format.trimTrailingZeros) it.trimEnd('0') else it }
    return if (fraction.isEmpty()) sign + whole else sign + whole + format.decimalSeparator + fraction
}

data class ScreenLabel3D(
    val key: String,
    val anchor: ScreenPosition3D,
    val width: Float,
    val height: Float,
    val priority: Int = 0,
) {
    init {
        require(width > 0f && height > 0f) { "Screen label dimensions must be positive" }
    }
}

data class PositionedScreenLabel3D(
    val key: String,
    val x: Float,
    val y: Float,
    val visible: Boolean,
)

/** Places labels near their anchors without overlap, returning results in input order. */
fun layoutScreenLabels(
    labels: List<ScreenLabel3D>,
    viewportWidth: Int,
    viewportHeight: Int,
    gap: Float = 4f,
    maxDisplacement: Float = 120f,
): List<PositionedScreenLabel3D> {
    require(viewportWidth > 0 && viewportHeight > 0) { "Viewport dimensions must be positive" }
    require(gap >= 0f && maxDisplacement >= 0f) { "Label gap and displacement must be non-negative" }
    require(labels.map(ScreenLabel3D::key).distinct().size == labels.size) { "Screen label keys must be unique" }
    val placed = mutableListOf<LabelRect>()
    val results = mutableMapOf<String, PositionedScreenLabel3D>()
    labels.sortedWith(compareByDescending<ScreenLabel3D> { it.priority }.thenBy { it.anchor.depth }.thenBy { it.key })
        .forEach { label ->
            if (!label.anchor.visible || label.width > viewportWidth || label.height > viewportHeight) {
                results[label.key] = PositionedScreenLabel3D(label.key, label.anchor.x, label.anchor.y, false)
                return@forEach
            }
            val step = label.height + gap
            val candidates = buildList {
                add(0f)
                var distance = step
                while (distance <= maxDisplacement + 0.001f) {
                    add(-distance)
                    add(distance)
                    distance += step
                }
            }
            val rect = candidates.firstNotNullOfOrNull { offset ->
                val left = (label.anchor.x - label.width / 2f).coerceIn(0f, viewportWidth - label.width)
                val top = (label.anchor.y - label.height / 2f + offset).coerceIn(0f, viewportHeight - label.height)
                LabelRect(left, top, label.width, label.height).takeIf { candidate ->
                    placed.none { it.overlaps(candidate, gap) }
                }
            }
            if (rect == null) {
                results[label.key] = PositionedScreenLabel3D(label.key, label.anchor.x, label.anchor.y, false)
            } else {
                placed += rect
                results[label.key] = PositionedScreenLabel3D(label.key, rect.x, rect.y, true)
            }
        }
    return labels.map { results.getValue(it.key) }
}

private data class LabelRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    fun overlaps(other: LabelRect, gap: Float): Boolean =
        x < other.x + other.width + gap && x + width + gap > other.x &&
            y < other.y + other.height + gap && y + height + gap > other.y
}
