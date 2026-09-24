package com.example.samsonic.ui.search

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.samsonic.model.SearchResults

/**
 * The Search tab's query and its results, for as long as the user stays in Search:
 * opening an artist or album from the results and coming back finds both as they
 * were (the Search page itself is rebuilt on back), with the results' cards there
 * for the art to shrink back into. Leaving for another tab [clear]s it.
 */
@Stable
class SearchSession {
    var query by mutableStateOf("")

    /** The results for [resultsQuery]; stay up while a newer query is still searching. */
    var results by mutableStateOf<SearchResults?>(null)
        private set

    /** The query [results] are for, so a return doesn't search the same query again. */
    var resultsQuery: String? = null
        private set

    internal fun show(query: String, results: SearchResults?) {
        this.results = results
        resultsQuery = query
    }

    fun clear() {
        query = ""
        results = null
        resultsQuery = null
    }
}
