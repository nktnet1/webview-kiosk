package uk.nktnet.webviewkiosk.adblock

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun mediaQueriesCompileToStylesheetsAndSurviveTheCache() {
        val text =
            "site.example##.ad:matches-media((min-width: 800px))\n" +
                "site.example##pre:matches-media(print):style(white-space: pre-line)\n" +
                "site.example##.ad:matches-media(all){}body{color:red}\n" +
                "site.example##.ad:matches-media(screen):has-text(Ads)"
        val file = File(folder.root, "media.bin")
        val original = KotlinFilterEngine(file)
        original.replace(listOf(text))
        assertEquals(2, original.unsupportedRuleCount())
        val restored = KotlinFilterEngine(file)
        restored.replace(listOf(text))
        val procedures = restored.cosmeticProcedures("https://site.example/")
        assertEquals(
            original.cosmeticProcedures("https://site.example/"),
            procedures,
        )
        assertEquals(2, procedures.size)
        assertEquals("(min-width: 800px)", procedures[0].media)
        assertEquals("display: none !important;", procedures[0].style)
        assertEquals("print", procedures[1].media)
        assertEquals("white-space: pre-line", procedures[1].style)
    }

    @Test
    fun textChainsAreSupportedAndUnsafePatternsRemainWholeRuleSkips() {
        val engine = KotlinFilterEngine()
        engine.replace(
            listOf(
                "site.example#?#.heading:has-text(Ads) + ul\n" +
                    "site.example#?#.card:has(.label:has-text(/(a+)+\$/))\n" +
                    "site.example#?#.card:has-text(Ads):has-text(Other)"
            )
        )
        assertEquals(1, engine.unsupportedRuleCount())
        assertEquals(2, engine.cosmeticProcedures("https://site.example/").size)
    }

    @Test
    fun scopedXPathShadowAndTemplateChainsSurviveTheCache() {
        val text =
            "site.example##.card:xpath(.//span[@data-kind='ad'])\n" +
                "site.example##.marker:xpath(../div[contains(text(), 'Sponsored')])\n" +
                "site.example##.host:shadow(.ad):has-text(Sponsored)\n" +
                "site.example##.card:has(template:content(.label):has-text(Sponsored))\n" +
                "site.example##template:content(.ad):remove()\n" +
                "site.example##.card:xpath(*/div[@count > 1 and contains(., '/sale')])"
        val file = File(folder.root, "scoped.bin")
        val original = KotlinFilterEngine(file)
        original.replace(listOf(text))
        assertEquals(0, original.unsupportedRuleCount())
        val restored = KotlinFilterEngine(file)
        restored.replace(listOf(text))
        val procedures = restored.cosmeticProcedures("https://site.example/")
        assertEquals(6, procedures.size)
        assertEquals(
            original.cosmeticProcedures("https://site.example/"),
            procedures,
        )
        assertEquals(".card", procedures[0].selector)
        assertTrue(procedures[0].operations!!.contains("xpath"))
        assertTrue(procedures[2].operations!!.contains("shadow"))
        assertTrue(procedures[3].operations!!.contains("content"))
    }

    @Test
    fun documentWideXPathAndArbitraryPropertiesStayUnsupported() {
        val engine = KotlinFilterEngine()
        engine.replace(
            listOf(
                "site.example##:xpath(//div)\n" +
                    "site.example##.card:xpath(//div)\n" +
                    "site.example##.card:xpath(.//div | //aside)\n" +
                    "site.example##.card:xpath(.//div[count(//aside) > 0])\n" +
                    "site.example##.card:xpath(id('advert'))\n" +
                    "site.example##.card:xpath(.//div[@ad * /root])\n" +
                    "site.example##.card:xpath(.//div[@ad and /root])\n" +
                    "site.example##.card:matches-prop(content.advert)\n" +
                    "site.example##.host:shadow(.ad:has-text(Sponsored))\n" +
                    "site.example##template:content(.ad):content()\n" +
                    "site.example##template:content(.ad)\n" +
                    "site.example##template:content(.ad):style(color: red)"
            )
        )
        assertEquals(12, engine.unsupportedRuleCount())
        assertEquals(
            emptyList<CosmeticProcedure>(),
            engine.cosmeticProcedures("https://site.example/"),
        )
    }
}
