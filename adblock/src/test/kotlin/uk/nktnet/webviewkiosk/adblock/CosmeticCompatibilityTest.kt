package uk.nktnet.webviewkiosk.adblock

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CosmeticCompatibilityTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun nestedTextConditionsKeepTheirChildSelectorAndCacheRepresentation() {
        val text =
            "site.example#?#.card:has(> .label:has-text(/^(Sponsored|Sponsorisé)\$/))\n" +
                "site.example#?#a:has(p:has-text(Advert)):not(.content)"
        val file = File(folder.root, "rules.bin")
        val original = KotlinFilterEngine(file)
        original.replace(listOf(text))
        val restored = KotlinFilterEngine(file)
        restored.replace(listOf(text))
        assertEquals(0, restored.unsupportedRuleCount())
        val procedures = restored.cosmeticProcedures("https://site.example/")
        assertEquals(
            original.cosmeticProcedures("https://site.example/"),
            procedures,
        )
        assertEquals(".card", procedures[0].selector)
        assertEquals(":scope > .label", procedures[0].textSelector)
        assertEquals(true, procedures[0].textPattern)
        assertEquals("a:not(.content)", procedures[1].selector)
        assertEquals("p", procedures[1].textSelector)
        assertEquals("Advert", procedures[1].text)
    }

    @Test
    fun quotedAttributeValuesAllowSemicolonsWithoutAdmittingCssStatements() {
        val engine = KotlinFilterEngine()
        engine.replace(
            listOf(
                "site.example##body[style=\"opacity: 0;\"]" +
                    ":style(opacity: 1 !important;)"
            )
        )
        assertEquals(0, engine.unsupportedRuleCount())
        val procedure = engine.cosmeticProcedures("https://site.example/").single()
        assertEquals("body[style=\"opacity: 0;\"]", procedure.selector)
        assertNull(procedure.textSelector)
        assertEquals("opacity: 1 !important;", procedure.style)
    }

    @Test
    fun unsupportedTextChainsAndUnsafePatternsRemainWholeRuleSkips() {
        val engine = KotlinFilterEngine()
        engine.replace(
            listOf(
                "site.example#?#.heading:has-text(Ads) + ul\n" +
                    "site.example#?#.card:has(.label:has-text(/(a+)+\$/))\n" +
                    "site.example#?#.card:has-text(Ads):has-text(Other)"
            )
        )
        assertEquals(3, engine.unsupportedRuleCount())
    }
}
