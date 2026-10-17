package uk.nktnet.webviewkiosk.adblock

import org.junit.Assert.assertEquals
import org.junit.Test

class FilterListInspectionTest {
    @Test
    fun reasonsCountWholeSkippedRulesAndExcludeCommentsAndInactiveBranches() {
        val text =
            listOf(
                    "! ignored comment with \$popup",
                    "!#if cap_html_filtering",
                    "site.example##^script:has-text(ignore)",
                    "!#endif",
                    "||ad.example^",
                    "site.example##.ad",
                    "site.*##+js(set, marker, false)",
                    "||popup.example^\$popup,third-party",
                    "@@||popup.example^\$popunder",
                    "||ad.example^\$csp=script-src 'none'",
                    "site.example##^script:has-text(tracker)",
                    "site.example##.heading:has-text(Ad) + ul",
                    "/site[0-9]/##+js(set, marker, false)",
                    "site.example##+js(json-edit, ..ads)",
                    "site.example##+js(aost, marker, /(?=ad)/)",
                    "site.example##+js(trusted-set, marker, true)",
                    "||ad.example^\$unknown-option",
                    "a".repeat(8193),
                    "||",
                )
                .joinToString("\n")
        val counts = inspectFilterList(text)
        assertEquals(1, counts.networkRules)
        assertEquals(1, counts.cosmeticRules)
        assertEquals(1, counts.scriptletRules)
        assertEquals(11, counts.unsupportedRules)
        assertEquals(1, counts.invalidRules)
        assertEquals(12, counts.skipReasons.values.sum())
        assertEquals(2, counts.skipReasons[FilterSkipReason.POPUP])
        for (reason in
            FilterSkipReason.entries.filterNot {
                it in setOf(FilterSkipReason.POPUP, FilterSkipReason.OTHER)
            }) {
            assertEquals(reason.name, 1, counts.skipReasons[reason])
        }
    }

    @Test
    fun trustAndSupportedRulesDoNotLeaveStaleSkipReasons() {
        val text = "site.example##+js(trusted-set, marker, true)"
        assertEquals(
            mapOf(FilterSkipReason.SCRIPTLET_TRUST to 1),
            inspectFilterList(text).skipReasons,
        )
        assertEquals(
            emptyMap<FilterSkipReason, Int>(),
            inspectFilterList(text, trusted = true).skipReasons,
        )
    }
}
