package dev.composescene3d.core

/** Final local-space displacement of one model part in a fully exploded assembly. */
data class ExplodedPart3D(
    val key: ModelPartKey,
    val translation: Vec3,
) {
    init {
        require(translation.x.isFinite() && translation.y.isFinite() && translation.z.isFinite()) {
            "Exploded part translation must be finite"
        }
    }
}

/**
 * Reusable exploded-view definition. Parts that represent hierarchy nodes move their descendants
 * naturally because [ModelPartOverride.transformOffset] is a local transform.
 */
data class ExplodedView3D(
    val parts: List<ExplodedPart3D>,
) {
    init {
        require(parts.map(ExplodedPart3D::key).distinct().size == parts.size) {
            "An exploded view cannot contain duplicate model part keys"
        }
    }

    /** Builds overrides at [progress], where zero is assembled and one is fully exploded. */
    fun overrides(
        progress: Float,
        base: Map<ModelPartKey, ModelPartOverride> = emptyMap(),
    ): Map<ModelPartKey, ModelPartOverride> {
        require(progress in 0f..1f && progress.isFinite()) {
            "Exploded view progress must be between zero and one"
        }
        if (progress == 0f) return base
        return buildMap {
            putAll(base)
            parts.forEach { part ->
                val current = base[part.key] ?: ModelPartOverride()
                val translation = current.transformOffset.translation
                put(
                    part.key,
                    current.copy(
                        transformOffset = current.transformOffset.copy(
                            translation = Vec3(
                                translation.x + part.translation.x * progress,
                                translation.y + part.translation.y * progress,
                                translation.z + part.translation.z * progress,
                            ),
                        ),
                    ),
                )
            }
        }
    }
}

/** Returns this model with [explodedView] merged into its existing part overrides. */
fun ModelNode.withExplodedView(explodedView: ExplodedView3D, progress: Float): ModelNode =
    copy(partOverrides = explodedView.overrides(progress, partOverrides))
