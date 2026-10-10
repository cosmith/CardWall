package fr.cosmith.cardwall.anki

import fr.cosmith.cardwall.model.DeckInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class DeckSelectionTest {
    @Test
    fun `selected parent removes selected descendants`() {
        val selected =
            listOf(
                deck(1, "Languages"),
                deck(2, "Languages::French"),
                deck(3, "Languages::French::Verbs"),
                deck(4, "Science::Biology"),
            )

        assertEquals(
            listOf(1L, 4L),
            collapseSelectedDeckBranches(selected).map(DeckInfo::id),
        )
    }

    @Test
    fun `sibling branches remain independently selected`() {
        val selected =
            listOf(
                deck(2, "Languages::French"),
                deck(5, "Languages::Spanish"),
            )

        assertEquals(
            listOf(2L, 5L),
            collapseSelectedDeckBranches(selected).map(DeckInfo::id),
        )
    }

    private fun deck(id: Long, name: String) =
        DeckInfo(id, name, learningCount = 1, reviewCount = 2, newCount = 3)
}
