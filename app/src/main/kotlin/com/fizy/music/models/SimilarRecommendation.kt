// SimilarRecommendation.kt

package com.fizy.music.models

import com.music.innertube.models.YTItem
import com.fizy.music.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
