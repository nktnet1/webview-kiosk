package uk.nktnet.webviewkiosk.ui.screens

import android.content.Context
import androidx.annotation.StringRes
import java.text.Normalizer
import java.util.Locale
import uk.nktnet.webviewkiosk.config.Screen

internal enum class SettingsSearchResultKind {
    SETTING,
    INFORMATION,
    ACTION,
    PAGE,
}

internal data class SettingsSearchResult(
    @StringRes val titleRes: Int,
    val route: String,
    val kind: SettingsSearchResultKind,
    val targetId: String? = null,
    val settingKey: String? = null,
    val showKey: Boolean = false,
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
        val aliases: String,
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
                        text(page.aliasesRes),
                        "",
                    )
                )
            }
            for (setting in page.settings) {
                add(
                    Document(
                        SettingsSearchResult(
                            setting.titleRes,
                            page.route,
                            SettingsSearchResultKind.SETTING,
                            targetId = setting.key,
                            settingKey = setting.key,
                        ),
                        text(setting.titleRes),
                        normaliseSettingsSearch(setting.key),
                        heading,
                        text(setting.descriptionRes),
                        text(setting.aliasesRes),
                        setting.optionLabelRes.joinToString(" ") { text(it) },
                    )
                )
            }
            for (item in page.items) {
                add(
                    Document(
                        SettingsSearchResult(
                            item.titleRes,
                            page.route,
                            item.kind,
                            targetId =
                                settingsItemTargetId(
                                    context,
                                    item.kind,
                                    item.titleRes,
                                ),
                        ),
                        text(item.titleRes),
                        "",
                        heading,
                        text(item.descriptionRes),
                        "",
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
                listOf(doc.label, doc.key, doc.aliases, doc.options, doc.description)
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
                    normalised == doc.key || normalised == doc.label -> 1_000
                    doc.label.startsWith(normalised) -> 800
                    words.all { it in doc.label || it in doc.key } -> 700
                    words.all {
                        it in doc.label || it in doc.key || it in doc.heading
                    } && words.any { it in doc.label || it in doc.key } -> 600
                    words.all {
                        it in doc.aliases || it in doc.label || it in doc.heading
                    } -> 500
                    words.any { it in doc.options } -> 300
                    else -> 100
                }
            val showKey =
                doc.key.isNotEmpty() &&
                    (query.any { it == '_' || it == '.' } &&
                        words.any { it in doc.key } ||
                        words.any {
                            it in doc.key && it !in doc.label && it !in doc.heading
                        })
            doc.result.copy(showKey = showKey) to score
        }
        // Strongest group first, with stable registry order breaking ties. Keep
        // each breadcrumb group together and rank its rows by match quality.
        return matches
            .groupBy { it.first.route }
            .values
            .sortedByDescending { group -> group.maxOf { it.second } }
            .flatMap { group ->
                group.sortedByDescending { it.second }.map { it.first }
            }
    }
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
