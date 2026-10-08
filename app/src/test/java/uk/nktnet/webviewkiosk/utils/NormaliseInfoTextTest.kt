package uk.nktnet.webviewkiosk.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class NormaliseInfoTextTest {
    @Test
    fun joinsWrappedProseButPreservesParagraphs() {
        assertEquals(
            "First line continues.\n\nNext paragraph.",
            normaliseInfoText("First line\ncontinues.\n\nNext paragraph."),
        )
    }

    @Test
    fun preservesListAndIndentedLineBoundaries() {
        val text = "Intro\n- First\n- Second\n1. One\n2. Two\n  indented"
        assertEquals(text, normaliseInfoText(text))
    }

    @Test
    fun expandsTabsWithoutRemovingIndentedNewlines() {
        assertEquals("Intro\n    detail", normaliseInfoText("Intro\n\tdetail"))
    }
}
