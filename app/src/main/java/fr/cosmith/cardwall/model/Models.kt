package fr.cosmith.cardwall.model

import androidx.compose.ui.graphics.ImageBitmap

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
)

data class DeckInfo(
    val id: Long,
    val name: String,
    val learningCount: Int,
    val reviewCount: Int,
    val newCount: Int,
) {
    val dueCount: Int get() = learningCount + reviewCount + newCount
}

data class ReviewCard(
    val cardId: Long,
    val noteId: Long,
    val cardOrd: Int,
    val questionHtml: String,
    val answerHtml: String,
    val buttonCount: Int,
    val nextReviewTimes: List<String>,
    val scheduled: Boolean,
)

data class CardWallSettings(
    val targetPackages: Set<String>,
    val selectedDeckIds: Set<Long>,
    val requiredReviews: Int,
    val accessMinutes: Int,
    val skipEnabled: Boolean = false,
)
