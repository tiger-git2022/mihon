package eu.kanade.tachiyomi.ui.browse.source.globalsearch

internal fun List<String>.withSearchQuery(query: String): List<String> {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) return this

    return buildList {
        add(trimmedQuery)
        addAll(this@withSearchQuery.filterNot { it.equals(trimmedQuery, ignoreCase = true) })
    }
}
