package com.soszynski.mateusz.dotmeme

import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.annotations.Required

open class Meme : RealmObject() {
    @PrimaryKey
    @Required
    var filePath: String = ""

    var rawText: String = ""
    var labels: String = ""
    var isScanned: Boolean = false

    // This isn't any official version.
    // If OCR gets better, we will +1 this and re-scan all older ones.
    var ocrVersion = 1

    companion object {
        val FILE_PATH = "filePath"
        val RAW_TEXT = "rawText"
        val LABELS = "labels"
        val IS_SCANNED = "isScanned"
        val OCR_VERSION = "ocrVersion"
    }
}

open class MemeFolder : RealmObject() {
    @PrimaryKey
    @Required
    var folderPath: String = ""

    var isScannable: Boolean = false
    var sdCard: Boolean = false
    var memes: RealmList<Meme> = RealmList()

    companion object {
        val FOLDER_PATH = "folderPath"
        val IS_SCANNABLE = "isScannable"
        val SD_CARD = "sdCard"
        val MEMES = "memes"
    }
}
