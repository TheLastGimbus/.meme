package com.soszynski.mateusz.dotmeme.memebase

import android.util.Log
import com.google.firebase.perf.FirebasePerformance
import com.soszynski.mateusz.dotmeme.Meme
import com.soszynski.mateusz.dotmeme.MemeFolder
import io.realm.Realm
import org.apache.commons.lang3.StringUtils


class MemeSearch {
    companion object {
        val TAG = "MemeSearch"

        data class SearchOptions(
            val folders: List<MemeFolder>,
            val images: Boolean = true,
            val videos: Boolean = true,
            val sortType: Int = SORT_NONE
        ) {
            companion object {
                /**
                 * Sorts only by search score.
                 * Fastest, and all you need 95% of times.
                 */
                const val SORT_NONE = 0
                /**
                 * Sorts by search score and newest (with score being first priority, then date)
                 * Can slow down search a bit.
                 */
                const val SORT_NEWEST_FIRST = 1
                /**
                 * Sorts by search score and oldest (with score being first priority, then date)
                 * Can slow down search a bit.
                 */
                const val SORT_OLDEST_FIRST = 2
            }
        }
    }


    /**
     * Search for memes.
     *
     * @param realm [Realm]
     * @param query text to search.
     * @param folders [MemeFolder]s to scan. Default value is all of them.
     *
     * @return list of found [Meme]s
     */
    fun search(
        realm: Realm,
        query: String,
        folders: List<MemeFolder> = realm.where(MemeFolder::class.java).findAll().toList()
    ): List<Meme> {
        val trace = FirebasePerformance.getInstance().newTrace("memebase_search")
        trace.start()

        val memeList = mutableListOf<Pair<Int, Meme>>()

        Log.i(TAG, "Begin of search, query: $query")

        val keywords =
            StringUtils.stripAccents(query).split(" ".toRegex()).dropLastWhile { it.isEmpty() }
        for (folder in folders) {
            for (meme in folder.memes) {
                var pair = Pair(0, meme)
                val strippedText = StringUtils.stripAccents(meme.rawText)
                for (keyword in keywords) {
                    if (strippedText.contains(keyword, true)) {
                        pair = pair.copy(first = pair.first + 1)
                    }
                }

                if (pair.first > 0) {
                    memeList.add(pair)
                }
            }
        }

        val finalList = memeList
            .sortedByDescending { it.first }
            .map { return@map it.second }

        trace.putMetric("memes_all_count", folders.sumBy { it.memes.count() }.toLong())
        trace.putMetric("memes_found_count", memeList.count().toLong())
        trace.stop()

        Log.i(TAG, "Memes found: ${trace.getLongMetric("memes_found_count")}")

        return finalList
    }
}