package fr.cosmith.cardwall.anki

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import fr.cosmith.cardwall.model.DeckInfo
import fr.cosmith.cardwall.model.ReviewCard
import org.json.JSONArray
import kotlin.random.Random

class AnkiRepository(private val context: Context) {
    private val resolver get() = context.contentResolver

    fun isInstalled(): Boolean =
        try {
            context.packageManager.getPackageInfo(AnkiContract.PACKAGE_NAME, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, AnkiContract.PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun loadDecks(): List<DeckInfo> =
        guarded {
            val projection = arrayOf(AnkiContract.Deck.ID, AnkiContract.Deck.NAME, AnkiContract.Deck.COUNTS)
            resolver.query(AnkiContract.decksUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(AnkiContract.Deck.ID)
                val nameIndex = cursor.getColumnIndexOrThrow(AnkiContract.Deck.NAME)
                val countsIndex = cursor.getColumnIndexOrThrow(AnkiContract.Deck.COUNTS)
                buildList {
                    while (cursor.moveToNext()) {
                        val counts = JSONArray(cursor.getString(countsIndex))
                        add(
                            DeckInfo(
                                id = cursor.getLong(idIndex),
                                name = cursor.getString(nameIndex),
                                learningCount = counts.optInt(0),
                                reviewCount = counts.optInt(1),
                                newCount = counts.optInt(2),
                            ),
                        )
                    }
                }
            }.orEmpty().sortedBy { it.name.lowercase() }
        }

    fun nextCard(selectedDeckIds: Set<Long>): ReviewCard? =
        guarded {
            require(selectedDeckIds.isNotEmpty()) { "Select at least one Anki deck" }
            val selectedDecks = collapseSelectedDeckBranches(loadDecks().filter { it.id in selectedDeckIds })
            if (selectedDecks.isEmpty()) return@guarded null

            val candidates = selectedDecks.filter { it.dueCount > 0 }.toMutableList()
            while (candidates.isNotEmpty()) {
                val chosen = weightedChoice(candidates)
                queryScheduledCard(chosen.id)?.let { return@guarded it }
                candidates.remove(chosen)
            }

            queryRandomFallback(selectedDecks)
        }

    fun submitGrade(card: ReviewCard, ease: Int, timeTakenMs: Long): Boolean =
        guarded {
            require(card.scheduled) { "Fallback cards must not update Anki scheduling" }
            val values =
                ContentValues().apply {
                    put(AnkiContract.ReviewInfo.NOTE_ID, card.noteId)
                    put(AnkiContract.ReviewInfo.CARD_ORD, card.cardOrd)
                    put(AnkiContract.ReviewInfo.EASE, ease.coerceIn(1, 4))
                    put(AnkiContract.ReviewInfo.TIME_TAKEN, timeTakenMs.coerceAtLeast(0L))
                }
            resolver.update(AnkiContract.scheduleUri, values, null, null) > 0
        }

    private fun queryScheduledCard(deckId: Long): ReviewCard? {
        val projection =
            arrayOf(
                AnkiContract.ReviewInfo.NOTE_ID,
                AnkiContract.ReviewInfo.CARD_ORD,
                AnkiContract.ReviewInfo.BUTTON_COUNT,
                AnkiContract.ReviewInfo.NEXT_REVIEW_TIMES,
                AnkiContract.ReviewInfo.MEDIA_FILES,
            )
        resolver.query(
            AnkiContract.scheduleUri,
            projection,
            "limit=?, deckID=?",
            arrayOf("1", deckId.toString()),
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val noteId = cursor.getLong(cursor.getColumnIndexOrThrow(AnkiContract.ReviewInfo.NOTE_ID))
            val cardOrd = cursor.getInt(cursor.getColumnIndexOrThrow(AnkiContract.ReviewInfo.CARD_ORD))
            val buttonCount = cursor.getInt(cursor.getColumnIndexOrThrow(AnkiContract.ReviewInfo.BUTTON_COUNT))
            val nextTimes =
                JSONArray(cursor.getString(cursor.getColumnIndexOrThrow(AnkiContract.ReviewInfo.NEXT_REVIEW_TIMES)))
                    .toStringList()
            return loadCardByNote(noteId, cardOrd, buttonCount, nextTimes, scheduled = true)
        }
        return null
    }

    private fun queryRandomFallback(decks: List<DeckInfo>): ReviewCard? {
        val deckQuery =
            decks.joinToString(separator = " or ", prefix = "(", postfix = ")") {
                "deck:\"${it.name.replace("\"", "\\\"")}\""
            }
        val query = "$deckQuery -is:suspended -is:buried"
        resolver.query(AnkiContract.cardsUri, arrayOf(AnkiContract.Card.ID), query, null, null)?.use { cursor ->
            if (cursor.count == 0) return null
            cursor.moveToPosition(Random.nextInt(cursor.count))
            val cardId = cursor.getLong(cursor.getColumnIndexOrThrow(AnkiContract.Card.ID))
            return loadCardById(cardId)
        }
        return null
    }

    private fun loadCardByNote(
        noteId: Long,
        cardOrd: Int,
        buttonCount: Int,
        nextTimes: List<String>,
        scheduled: Boolean,
    ): ReviewCard? =
        queryCard(
            uri = AnkiContract.noteCardUri(noteId, cardOrd),
            buttonCount = buttonCount,
            nextTimes = nextTimes,
            scheduled = scheduled,
        )

    private fun loadCardById(cardId: Long): ReviewCard? =
        queryCard(
            uri = AnkiContract.cardUri(cardId),
            buttonCount = 0,
            nextTimes = emptyList(),
            scheduled = false,
        )

    private fun queryCard(
        uri: android.net.Uri,
        buttonCount: Int,
        nextTimes: List<String>,
        scheduled: Boolean,
    ): ReviewCard? {
        val projection =
            arrayOf(
                AnkiContract.Card.ID,
                AnkiContract.Card.NOTE_ID,
                AnkiContract.Card.CARD_ORD,
                AnkiContract.Card.QUESTION,
                AnkiContract.Card.ANSWER,
            )
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            return ReviewCard(
                cardId = cursor.getLong(cursor.getColumnIndexOrThrow(AnkiContract.Card.ID)),
                noteId = cursor.getLong(cursor.getColumnIndexOrThrow(AnkiContract.Card.NOTE_ID)),
                cardOrd = cursor.getInt(cursor.getColumnIndexOrThrow(AnkiContract.Card.CARD_ORD)),
                questionHtml = cursor.getString(cursor.getColumnIndexOrThrow(AnkiContract.Card.QUESTION)),
                answerHtml = cursor.getString(cursor.getColumnIndexOrThrow(AnkiContract.Card.ANSWER)),
                buttonCount = buttonCount,
                nextReviewTimes = nextTimes,
                scheduled = scheduled,
            )
        }
        return null
    }

    private fun weightedChoice(decks: List<DeckInfo>): DeckInfo {
        val total = decks.sumOf { it.dueCount.coerceAtLeast(1) }
        var pick = Random.nextInt(total)
        for (deck in decks) {
            pick -= deck.dueCount.coerceAtLeast(1)
            if (pick < 0) return deck
        }
        return decks.last()
    }

    private inline fun <T> guarded(block: () -> T): T {
        if (!isInstalled()) throw AnkiUnavailableException("AnkiDroid is not installed")
        if (!hasPermission()) throw AnkiUnavailableException("AnkiDroid permission is missing")
        return try {
            block()
        } catch (error: AnkiUnavailableException) {
            throw error
        } catch (error: Throwable) {
            throw AnkiUnavailableException(error.message ?: "AnkiDroid is unavailable", error)
        }
    }

    private fun JSONArray.toStringList(): List<String> =
        buildList {
            for (index in 0 until length()) add(optString(index))
        }
}

class AnkiUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Selecting an Anki parent deck already includes its descendants in scheduler and
 * browser queries. Drop explicitly selected descendants so their cards are not
 * double-weighted when both a parent and one of its subdecks were checked.
 */
internal fun collapseSelectedDeckBranches(decks: List<DeckInfo>): List<DeckInfo> {
    val selectedNames = decks.mapTo(mutableSetOf(), DeckInfo::name)
    return decks.filter { deck ->
        generateSequence(deck.name.substringBeforeLast("::", missingDelimiterValue = "")) { name ->
            name.substringBeforeLast("::", missingDelimiterValue = "").takeIf(String::isNotEmpty)
        }.filter(String::isNotEmpty).none(selectedNames::contains)
    }
}
