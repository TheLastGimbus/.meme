package com.soszynski.mateusz.dotmeme.memebase

import android.util.Log
import com.google.firebase.perf.FirebasePerformance
import com.soszynski.mateusz.dotmeme.MemeFolder
import io.realm.Realm
import org.apache.commons.lang3.StringUtils
import java.io.File


class MemeSearch {
    companion object {
        val TAG = "MemeSearch"

        data class SearchOptions(
            val folders: List<String>,
            val images: Boolean = true,
            val videos: Boolean = false, // TODO
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

                fun getDefault(realm: Realm) =
                    SearchOptions(realm.where(MemeFolder::class.java).findAll().map { it.folderPath })
            }
        }
    }


    /**
     * Search for memes.
     *
     * @param realm [Realm]
     * @param query text to search.
     * @param options [SearchOptions] to rule what and how to search
     *
     * @return list of found memes and videos in string paths
     */
    fun search(
        realm: Realm,
        query: String,
        options: SearchOptions = SearchOptions(
            folders = realm.where(MemeFolder::class.java).findAll().map { it.folderPath }
        )
    ): List<String> {
        if (options.videos) {
            TODO("Videos are not yet implemented in most of the places")
        }

        val trace = FirebasePerformance.getInstance().newTrace("memebase_search")
        trace.start()

        val memeList = mutableListOf<Pair<Int, String>>()

        Log.i(TAG, "Begin of search, query: $query")

        val keywords =
            StringUtils.stripAccents(query).split(" ".toRegex()).dropLastWhile { it.isEmpty() }
        val folders = realm.where(MemeFolder::class.java)
            .`in`(MemeFolder.FOLDER_PATH, options.folders.toTypedArray())
            .findAll()
        for (folder in folders) {
            if (query == "*") {
                if (options.images)
                    memeList.addAll(folder.memes.map { Pair(0, it.filePath) })
                if (options.videos)
                    memeList.addAll(folder.videos.map { Pair(0, it.filePath) })
                continue
            }
            if (options.images) {
                for (meme in folder.memes) {
                    var pair = Pair(0, meme.filePath)
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
            // TODO: options.video
        }

        val comparator = when (options.sortType) {
            SearchOptions.SORT_NONE -> compareBy { it.first }
            SearchOptions.SORT_NEWEST_FIRST -> {
                compareBy<Pair<Int, String>> { it.first }.thenByDescending { File(it.second).lastModified() }
            }
            SearchOptions.SORT_OLDEST_FIRST -> {
                compareBy<Pair<Int, String>> { it.first }
                    .thenBy { File(it.second).lastModified() }
            }
            else -> compareBy<Pair<Int, String>> { it.first }
        }

        val finalList = memeList
            .sortedWith(comparator)
            .map { return@map it.second }

        trace.putMetric("memes_all_count", folders.sumBy { it.memes.count() }.toLong())
        trace.putMetric("memes_found_count", memeList.count().toLong())
        trace.stop()

        Log.i(TAG, "Memes found: ${trace.getLongMetric("memes_found_count")}")

        return finalList
    }
}