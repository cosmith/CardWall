package fr.cosmith.cardwall.data

import android.content.Context
import androidx.core.content.edit
import fr.cosmith.cardwall.model.CardWallSettings

class CardWallPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): CardWallSettings =
        CardWallSettings(
            targetPackages = preferences.getStringSet(KEY_TARGET_PACKAGES, emptySet()).orEmpty().toSet(),
            selectedDeckIds =
                preferences
                    .getStringSet(KEY_DECK_IDS, emptySet())
                    .orEmpty()
                    .mapNotNull(String::toLongOrNull)
                    .toSet(),
            requiredReviews = preferences.getInt(KEY_REQUIRED_REVIEWS, DEFAULT_REQUIRED_REVIEWS).coerceIn(1, 20),
            accessMinutes = preferences.getInt(KEY_ACCESS_MINUTES, DEFAULT_ACCESS_MINUTES).coerceIn(1, 120),
            skipEnabled = preferences.getBoolean(KEY_SKIP_ENABLED, false),
        )

    fun saveTargetPackages(packages: Set<String>) {
        preferences.edit { putStringSet(KEY_TARGET_PACKAGES, packages) }
    }

    fun saveDeckIds(deckIds: Set<Long>) {
        preferences.edit { putStringSet(KEY_DECK_IDS, deckIds.map(Long::toString).toSet()) }
    }

    fun saveRequiredReviews(value: Int) {
        preferences.edit { putInt(KEY_REQUIRED_REVIEWS, value.coerceIn(1, 20)) }
    }

    fun saveAccessMinutes(value: Int) {
        preferences.edit { putInt(KEY_ACCESS_MINUTES, value.coerceIn(1, 120)) }
    }

    fun saveSkipEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_SKIP_ENABLED, enabled) }
    }

    companion object {
        const val DEFAULT_REQUIRED_REVIEWS = 3
        const val DEFAULT_ACCESS_MINUTES = 10

        private const val PREFERENCES_NAME = "cardwall_settings"
        private const val KEY_TARGET_PACKAGES = "target_packages"
        private const val KEY_DECK_IDS = "deck_ids"
        private const val KEY_REQUIRED_REVIEWS = "required_reviews"
        private const val KEY_ACCESS_MINUTES = "access_minutes"
        private const val KEY_SKIP_ENABLED = "skip_enabled"
    }
}
