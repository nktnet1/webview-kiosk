package uk.nktnet.webviewkiosk.ui.screens

import android.content.Context
import androidx.annotation.StringRes
import java.text.Normalizer
import java.util.Locale
import uk.nktnet.webviewkiosk.config.Screen

/** Break text-relevance ties in favour of editable settings. */
internal enum class SettingsSearchResultKind(val searchPriority: Int) {
    SETTING(4),
    INFORMATION(1),
    ACTION(3),
    PAGE(2),
}

internal data class SettingsSearchResult(
    @StringRes val titleRes: Int,
    val route: String,
    val kind: SettingsSearchResultKind,
    val targetId: String? = null,
    val settingKey: String? = null,
)

/** Resource names are stable across languages and resource-ID reassignments. */
internal fun settingsItemTargetId(
    context: Context,
    kind: SettingsSearchResultKind,
    @StringRes titleRes: Int,
): String =
    "${kind.name.lowercase(Locale.ROOT)}:${context.resources.getResourceEntryName(titleRes)}"

/** Treat spaces, dots, underscores, hyphens, and accents consistently. */
internal fun normaliseSettingsSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

/**
 * Localised, immutable documents contain only static resources and persisted key
 * names. The UI recreates this index on configuration changes, not on each keypress.
 */
internal class SettingsSearchIndex(context: Context) {
    private data class Document(
        val result: SettingsSearchResult,
        val label: String,
        val key: String,
        val heading: String,
        val description: String,
        val aliases: List<String>,
        val options: String,
    )

    private val documents = buildList {
        fun text(@StringRes res: Int?): String =
            res?.let {
                    // Help can contain format placeholders. The static template is
                    // searchable; interpolated device/user data never enters the
                    // index.
                    normaliseSettingsSearch(
                        context
                            .getString(it)
                            .replace(Regex("%\\d*\\$?[a-zA-Z]"), " ")
                    )
                }
                .orEmpty()

        // Comma-separated aliases are independent search phrases, not one label.
        fun aliases(@StringRes res: Int?): List<String> =
            res?.let {
                    context
                        .getString(it)
                        .split(',')
                        .map(::normaliseSettingsSearch)
                        .filter { alias -> alias.isNotEmpty() }
                }
                .orEmpty()

        for (page in settingsPages) {
            val heading = normaliseSettingsSearch(page.breadcrumb(context))
            if (page.screen != Screen.Settings) {
                add(
                    Document(
                        SettingsSearchResult(
                            page.titleRes,
                            page.route,
                            SettingsSearchResultKind.PAGE,
                        ),
                        text(page.titleRes),
                        "",
                        heading,
                        text(page.descriptionRes),
                        aliases(page.aliasesRes),
                        "",
                    )
                )
            }
            for ((titleRes, key, descriptionRes, optionLabelRes, aliasesRes) in
                page.settings) {
                add(
                    Document(
                        SettingsSearchResult(
                            titleRes,
                            page.route,
                            SettingsSearchResultKind.SETTING,
                            targetId = key,
                            settingKey = key,
                        ),
                        text(titleRes),
                        normaliseSettingsSearch(key),
                        heading,
                        text(descriptionRes),
                        aliases(aliasesRes),
                        optionLabelRes.joinToString(" ") { text(it) },
                    )
                )
            }
            for ((titleRes, kind, descriptionRes) in page.items) {
                add(
                    Document(
                        SettingsSearchResult(
                            titleRes,
                            page.route,
                            kind,
                            targetId =
                                settingsItemTargetId(
                                    context,
                                    kind,
                                    titleRes,
                                ),
                        ),
                        text(titleRes),
                        "",
                        heading,
                        text(descriptionRes),
                        emptyList(),
                        "",
                    )
                )
            }
        }
    }

    fun search(query: String): List<SettingsSearchResult> {
        val normalised = normaliseSettingsSearch(query)
        if (normalised.isEmpty()) {
            return emptyList()
        }
        val words = normalised.split(' ').distinct()
        val matches = documents.mapNotNull { doc ->
            val direct =
                listOf(doc.label, doc.key, doc.options, doc.description) +
                    doc.aliases
            // Ancestors supply context, but must not return every child for a
            // section-only query. Page descriptions match pages, not unrelated rows.
            if (
                !words.all { word -> (direct + doc.heading).any { word in it } } ||
                    !words.any { word -> direct.any { word in it } }
            ) {
                return@mapNotNull null
            }
            val score =
                when {
                    normalised == doc.key ||
                        normalised == doc.label ||
                        normalised in doc.aliases -> 1_000
                    doc.label.startsWith(normalised) ||
                        doc.aliases.any { it.startsWith(normalised) } -> 800
                    words.all { it in doc.label || it in doc.key } -> 700
                    words.all {
                        it in doc.label || it in doc.key || it in doc.heading
                    } && words.any { it in doc.label || it in doc.key } -> 600
                    words.all { word ->
                        word in doc.label ||
                            word in doc.heading ||
                            doc.aliases.any { word in it }
                    } -> 500
                    words.any { it in doc.options } -> 300
                    else -> 100
                }
            doc.result to score
        }
        return rankSettingsSearchMatches(matches)
    }
}

/**
 * Rank breadcrumb sections by their strongest hit, then sort rows within each
 * section.
 */
internal fun rankSettingsSearchMatches(
    matches: List<Pair<SettingsSearchResult, Int>>
): List<SettingsSearchResult> {
    // Relevance is primary: an exact information title or page alias beats a
    // setting mentioned only in help text. Type priority breaks equal scores.
    val byRank =
        compareByDescending<Pair<SettingsSearchResult, Int>> { it.second }
            .thenByDescending { it.first.kind.searchPriority }
    // Stable sorting preserves registry order for ties. Keep each breadcrumb
    // section together even if one of its weaker rows trails another section.
    return matches
        .groupBy { it.first.route }
        .values
        .map { it.sortedWith(byRank) }
        .sortedWith(
            compareByDescending<List<Pair<SettingsSearchResult, Int>>> {
                    it.first().second
                }
                .thenByDescending { it.first().first.kind.searchPriority }
        )
        .flatMap { group -> group.map { it.first } }
}

internal fun searchSettings(
    context: Context,
    query: String,
): List<SettingsSearchResult> = SettingsSearchIndex(context).search(query)

internal fun groupSettingsSearchResults(
    results: List<SettingsSearchResult>
): List<Pair<SettingsPage, List<SettingsSearchResult>>> =
    results
        .groupBy { it.route }
        .map { (route, matches) ->
            settingsPages.single { it.route == route } to matches
        }
