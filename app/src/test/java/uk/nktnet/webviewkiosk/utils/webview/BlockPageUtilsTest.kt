package uk.nktnet.webviewkiosk.utils.webview

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import uk.nktnet.webviewkiosk.config.Constants
import uk.nktnet.webviewkiosk.config.UserSettings
import uk.nktnet.webviewkiosk.utils.webview.html.BlockCause

@RunWith(RobolectricTestRunner::class)
class BlockPageUtilsTest {
    private lateinit var settings: UserSettings

    @Before
    fun setUp() {
        settings = UserSettings(RuntimeEnvironment.getApplication())
        settings.allowLocalFiles = false
    }

    @Test
    fun whitelistMatchesOverrideBlacklistMatches() {
        val blacklist = listOf("example\\.com")
        val whitelist = listOf("/allowed(?:/|$)")

        assertTrue(isBlockedUrl("https://example.com/denied", blacklist, whitelist))
        assertFalse(
            isBlockedUrl("https://example.com/allowed/page", blacklist, whitelist)
        )
        assertFalse(isBlockedUrl("https://other.example/", blacklist, whitelist))
        assertFalse(isBlockedUrl("https://example.com/", emptyList(), emptyList()))
    }

    @Test
    fun expensiveNestedRegexDoesNotBlockUrlNavigation() {
        // The Android ICU/Java backtracking engine can stall on this non-match.
        val url = "https://example.com/" + "a".repeat(6000) + "!"
        val expensive = "(a+)+$"

        val start = System.nanoTime()
        assertFalse(isBlockedUrl(url, listOf(expensive), emptyList()))
        assertTrue(
            "Regex should complete well inside the WebView ANR threshold",
            System.nanoTime() - start <
                java.util.concurrent.TimeUnit.SECONDS.toNanos(2),
        )
        assertTrue(isBlockedUrl(url, listOf(".*"), listOf(expensive)))
    }

    @Test
    fun unsupportedRegexSyntaxFailsClosedWithoutNativeFallback() {
        val url = "https://example.com/"
        assertEquals(
            BlockCause.URL_REGEX_ERROR,
            getBlockInfo(url, listOf("(?<=https://)example"), emptyList(), settings)
                .second,
        )
        assertEquals(
            BlockCause.URL_REGEX_ERROR,
            getBlockInfo(
                    url,
                    listOf("example"),
                    listOf("(?<=https://)example"),
                    settings,
                )
                .second,
        )
        assertFalse(isBlockedUrl(url, listOf("example"), listOf("https://example")))
    }

    @Test
    fun whitelistWithoutBlacklistSkipsRegexEvaluation() {
        val start = System.nanoTime()
        assertFalse(
            isBlockedUrl(
                "https://example.com/" + "a".repeat(6000) + "!",
                emptyList(),
                listOf("(a+)+$"),
            )
        )
        assertTrue(
            System.nanoTime() - start <
                java.util.concurrent.TimeUnit.SECONDS.toNanos(2)
        )
    }

    @Test
    fun invalidRegexHasDistinctBlockPageCause() {
        val url = "https://example.com/"
        for ((blacklist, whitelist) in
            listOf(
                listOf("(") to emptyList(),
                listOf("example") to listOf("("),
            )) {
            val cause = urlRegexBlockCause(url, blacklist, whitelist)
            assertEquals(BlockCause.URL_REGEX_ERROR, cause)
            assertTrue(
                blockedPageHtml(settings, url, cause!!)
                    .contains("<p>${BlockCause.URL_REGEX_ERROR.label}</p>")
            )
        }
    }

    @Test
    fun whitelistMatchOverridesInvalidBlacklistRule() {
        assertNull(
            urlRegexBlockCause(
                "https://example.com/allowed",
                listOf("("),
                listOf("/allowed"),
            )
        )
    }

    @Test
    fun re2MatchesWithPartialMatchingAndInlineFlags() {
        val blacklist = listOf("(?i)example\\.com")
        assertEquals(
            BlockCause.BLACKLIST,
            getBlockInfo(
                    "https://EXAMPLE.com/page",
                    blacklist,
                    emptyList(),
                    settings,
                )
                .second,
        )
        assertNull(
            getBlockInfo("https://other.invalid", blacklist, emptyList(), settings)
                .second
        )
    }

    @Test
    fun expensiveCountedRepetitionsFailClosedBeforeCompilation() {
        for (pattern in listOf("(a{1000}){1000}", "(a{1000}|b{1000}){1000}")) {
            assertFalse(UrlRegexComplexity.isWithinBudget(pattern))
            assertEquals(
                BlockCause.URL_REGEX_ERROR,
                getBlockInfo(
                        "https://example.com",
                        listOf(pattern),
                        emptyList(),
                        settings,
                    )
                    .second,
            )
        }
        assertTrue(UrlRegexComplexity.isWithinBudget("(ab{3}){4}"))
        assertFalse(
            UrlRegexComplexity.isWithinBudget("(".repeat(65) + "a" + ")".repeat(65))
        )
    }

    @Test
    fun safeLargePatternsAndUrlsAreNotRejectedByArbitraryLengthLimits() {
        val pattern = "a".repeat(4200)
        val url = "https://example.com/" + "a".repeat(70_000) + "/allowed"
        assertEquals(
            BlockCause.BLACKLIST,
            getBlockInfo(
                    "https://example.com/$pattern",
                    listOf(pattern),
                    emptyList(),
                    settings,
                )
                .second,
        )
        assertNull(
            getBlockInfo(url, listOf("/denied$"), emptyList(), settings).second
        )
        assertEquals(
            BlockCause.BLACKLIST,
            getBlockInfo(url, listOf("/allowed$"), emptyList(), settings).second,
        )
    }

    @Test
    fun configurationScopedCacheDoesNotThrashAt257Rules() {
        val blacklist = (0..256).map { "^https://denied.example/$it$" }
        val rules = UrlRegexMatcher.forRules(blacklist, emptyList())
        for (pattern in blacklist) {
            assertEquals(
                UrlRegexMatchResult.NO_MATCH,
                rules.match(pattern, "https://other.example"),
            )
        }
        assertEquals(257, rules.cachedRuleCount)
        assertTrue(
            rules === UrlRegexMatcher.forRules(blacklist.toList(), emptyList())
        )
        for (pattern in blacklist) {
            assertEquals(
                UrlRegexMatchResult.NO_MATCH,
                rules.match(pattern, "https://other.example"),
            )
        }
        assertEquals(257, rules.cachedRuleCount)
        assertFalse(
            rules === UrlRegexMatcher.forRules(blacklist.dropLast(1), emptyList())
        )
    }

    @Test
    fun appliesTheLocalFilePolicyToFileUrlsAndAssetLoaderLinks() {
        val urls =
            listOf(
                "file:///sdcard/page.html",
                "https://appassets.androidplatform.net/web-content-files/page.html",
            )
        for (url in urls) {
            assertEquals(
                url,
                BlockCause.LOCAL_FILE,
                getBlockInfo(url, emptyList(), emptyList(), settings).second,
            )
            settings.allowLocalFiles = true
            assertNull(
                url,
                getBlockInfo(url, emptyList(), emptyList(), settings).second,
            )
            settings.allowLocalFiles = false
        }
    }

    @Test
    fun unwrapsMixedPdfAndBlockPagesWithoutChangingTheSourceUrl() {
        val source = "https://example.com/report.pdf?token=a%2Bb&name=one+two#page=3"

        assertEquals(source, resolve(block(pdf(block(source)))))
    }

    @Test
    fun keepsADeniedDecodedTargetForTheCallersBlockAction() {
        val source = "https://denied.example/report.pdf"

        val blacklist = listOf("^https://denied\\.example(?:/|$)")
        assertEquals(source, resolve(block(pdf(source)), blacklist = blacklist))
        assertEquals(
            BlockCause.BLACKLIST,
            getBlockInfo(source, blacklist, emptyList(), settings).second,
        )
    }

    @Test
    fun cannotBypassTheLocalFilePolicyByWrappingAFileUrl() {
        val source = "file:///sdcard/private.html"

        assertEquals(source, resolve(pdf(block(source))))
        assertEquals(
            BlockCause.LOCAL_FILE,
            getBlockInfo(source, emptyList(), emptyList(), settings).second,
        )
    }

    @Test
    fun leavesABlacklistedBlockWrapperForNormalBlocking() {
        val wrapper = block("https://example.com")

        assertEquals(
            wrapper,
            resolve(wrapper, blacklist = listOf("^webviewkiosk://block")),
        )
    }

    @Test
    fun alsoBlocksWrappersWhenTheBlacklistMatchesTheirEncodedQuery() {
        val wrapper = block(pdf("https://denied.example/report.pdf"))

        assertEquals(
            wrapper,
            resolve(wrapper, blacklist = listOf("denied\\.example")),
        )
    }

    @Test
    fun rejectsWrappersWithMissingOrBlankTargets() {
        for (url in
            listOf(
                "webviewkiosk://block",
                "webviewkiosk://block?url=",
                block(" "),
                pdf(""),
                pdf(" "),
            )) {
            assertNull(url, resolve(url))
        }
    }

    @Test
    fun boundsTheCombinedWrapperDepth() {
        val source = "https://example.com/report.pdf"
        val tenWrappers =
            (1..10).fold(source) { url, index ->
                if (index % 2 == 0) {
                    pdf(url)
                } else {
                    block(url)
                }
            }

        assertEquals(source, resolve(tenWrappers))
        assertNull(resolve(block(tenWrappers)))
    }

    @Test
    fun recognizesOnlyTheExactInternalPdfViewerOriginAndPath() {
        val query = "?wk_pdf_url=https%3A%2F%2Fexample.com%2Freport.pdf"
        assertTrue(
            isPdfViewerUrl(Uri.parse("${Constants.PDF_JS_ASSETS_DUMMY_URL}$query"))
        )
        assertTrue(
            isPdfViewerUrl(
                Uri.parse("${Constants.PDF_JS_ASSETS_DUMMY_URL}:443/$query")
            )
        )
        val impostors =
            listOf(
                "http://pdf-dummy.webviewkiosk.nktnet.uk$query",
                "https://pdf-dummy.webviewkiosk.nktnet.uk:8443/$query",
                "https://user@pdf-dummy.webviewkiosk.nktnet.uk/$query",
                "https://pdf-dummy.webviewkiosk.nktnet.uk/other$query",
                "https://pdf-dummy.webviewkiosk.nktnet.uk.evil.example/$query",
                Constants.PDF_JS_ASSETS_DUMMY_URL,
            )
        for (url in impostors) {
            assertFalse(url, isPdfViewerUrl(Uri.parse(url)))
            assertEquals(url, resolve(url))
        }
    }

    private fun resolve(url: String, blacklist: List<String> = emptyList()) =
        resolveBlockPageUrl(url, blacklist, emptyList(), settings)

    private fun block(url: String) =
        "${Constants.APP_SCHEME}://block?url=${Uri.encode(url)}"

    private fun pdf(url: String) =
        "${Constants.PDF_JS_ASSETS_DUMMY_URL}/?wk_pdf_url=${Uri.encode(url)}"
}
