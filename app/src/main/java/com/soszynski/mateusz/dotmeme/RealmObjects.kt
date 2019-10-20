package com.soszynski.mateusz.dotmeme

import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.annotations.Required
import java.io.File

/**
 * [RealmObject] class containing one meme.
 *
 * @property filePath path to meme file on device.
 * @property rawText raw text from ocr, without processing or autocorrection.
 * @property labels tags or labels to memes. For example, dog, man, jacket, smile, snow etc.
 * @property isScanned [Boolean] whether meme was already scanned.
 * @property ocrVersion it's not any official version. If OCR gets better, we will just +1 this, and re-scan all old.
 */
open class Meme : RealmObject() {
    @PrimaryKey
    @Required
    var filePath: String = ""

    var rawText: String = ""
    var labels: String = ""
    var isScanned: Boolean = false

    var ocrVersion = 1

    companion object {
        const val FILE_PATH = "filePath"
        const val RAW_TEXT = "rawText"
        const val LABELS = "labels"
        const val IS_SCANNED = "isScanned"
        const val OCR_VERSION = "ocrVersion"
    }
}

/**
 * [RealmObject] class containing one folder with images - or memes.
 *
 * @property folderPath path to folder on device.
 * @property isScannable [Boolean] whether to scan [memes] inside this folder or not.
 * @property sdCard [Boolean] whether this folder is on SD card.
 * @property memes [RealmList] of memes inside this folder.
 */
open class MemeFolder : RealmObject() {
    @PrimaryKey
    @Required
    var folderPath: String = ""

    var isScannable: Boolean = false
    var sdCard: Boolean = false
    var memes: RealmList<Meme> = RealmList()

    companion object {
        const val FOLDER_PATH = "folderPath"
        const val IS_SCANNABLE = "isScannable"
        const val SD_CARD = "sdCard"
        const val MEMES = "memes"

        /**
         * Companion function to check if all [Meme]s inside given [MemeFolder] are scanned.
         * @param folder folder to check.
         */
        fun isFolderFullyScanned(folder: MemeFolder): Boolean {
            return folder.memes.where().equalTo(Meme.IS_SCANNED, false).count().toInt() == 0
        }
    }
}

open class MemeRoll : RealmObject() {
    @PrimaryKey
    var id = 1

    var roll: RealmList<String> = RealmList<String>()

    companion object {
        const val ROLL = "roll"
        const val ID = "id"

        fun cacheRoll(realm: Realm, roll: List<File>) {
            realm.executeTransaction {

                val realmRoll = realm.where(MemeRoll::class.java).findFirst()
                if (realmRoll == null) {
                    val newRealmRoll = MemeRoll()
                    newRealmRoll.roll.addAll(roll.map { it.absolutePath })
                    realm.copyToRealm(newRealmRoll)
                } else {
                    realmRoll.roll.clear()
                    realmRoll.roll.addAll(roll.map { it.absolutePath })
                }
            }
        }
    }
}
