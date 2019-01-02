package com.soszynski.mateusz.dotmeme

import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class Meme : RealmObject() {
    @PrimaryKey
    var filePath: String = ""

    var rawText: String = ""
    var labels: String = ""

    // This isn't any official version.
    // If OCR gets better, we will +1 this and re-scan all older ones.
    var ocrVersion = 1
}

open class MemeFolder : RealmObject() {
    @PrimaryKey
    var folderPath: String = ""

    var isScanable: Boolean = false
    var sdCard: Boolean = false
    var memes: RealmList<Meme> = RealmList()
}
