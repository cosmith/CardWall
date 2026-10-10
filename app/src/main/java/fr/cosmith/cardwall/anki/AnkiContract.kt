package fr.cosmith.cardwall.anki

import android.net.Uri
import androidx.core.net.toUri

internal object AnkiContract {
    const val PACKAGE_NAME = "com.ichi2.anki"
    const val PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"
    private const val AUTHORITY = "com.ichi2.anki.flashcards"

    val decksUri: Uri = "content://$AUTHORITY/decks/".toUri()
    val scheduleUri: Uri = "content://$AUTHORITY/schedule/".toUri()
    val cardsUri: Uri = "content://$AUTHORITY/cards".toUri()

    object Deck {
        const val ID = "deck_id"
        const val NAME = "deck_name"
        const val COUNTS = "deck_count"
    }

    object ReviewInfo {
        const val NOTE_ID = "note_id"
        const val CARD_ORD = "ord"
        const val BUTTON_COUNT = "button_count"
        const val NEXT_REVIEW_TIMES = "next_review_times"
        const val MEDIA_FILES = "media_files"
        const val EASE = "answer_ease"
        const val TIME_TAKEN = "time_taken"
    }

    object Card {
        const val ID = "_id"
        const val NOTE_ID = "note_id"
        const val CARD_ORD = "ord"
        const val DECK_ID = "deck_id"
        const val QUESTION = "question"
        const val ANSWER = "answer"
        const val RAW_QUEUE = "queue"
    }

    fun noteCardUri(noteId: Long, cardOrd: Int): Uri =
        "content://$AUTHORITY".toUri()
            .buildUpon()
            .appendPath("notes")
            .appendPath(noteId.toString())
            .appendPath("cards")
            .appendPath(cardOrd.toString())
            .build()

    fun cardUri(cardId: Long): Uri = Uri.withAppendedPath(cardsUri, cardId.toString())
}
