package com.soszynski.mateusz.dotmeme.memebase

import android.content.Context
import android.os.FileObserver
import android.util.Log
import com.soszynski.mateusz.dotmeme.*
import io.realm.Realm
import io.realm.RealmConfiguration
import java.io.File
import java.util.*


/**
 * This class contains almost all things related to database of memes.
 * If you want to do any work with database - do it here.
 *
 * Some functions take long, and you need to wrap them in doAsync, some (like scanAllFolders handle
 * it themselves. Read their doc to find out.
 */

class Memebase {
    companion object {
        const val TAG = "Memebase class"

        fun handleRealmConfigs() {
            if (Realm.getDefaultConfiguration()?.schemaVersion!! < 1) {
                val config = RealmConfiguration.Builder()
                    .schemaVersion(1)
                    .migration(UniversalMigration())
                    .build()
                Realm.setDefaultConfiguration(config)
            }
            if (Realm.getDefaultConfiguration()?.schemaVersion!! < 2) {
                val config = RealmConfiguration.Builder()
                    .schemaVersion(2)
                    .migration(UniversalMigration())
                    .build()
                Realm.setDefaultConfiguration(config)
            }
            if (Realm.getDefaultConfiguration()?.schemaVersion!! < 3) {
                val config = RealmConfiguration.Builder()
                    .schemaVersion(3)
                    .migration(UniversalMigration())
                    .build()
                Realm.setDefaultConfiguration(config)
            }
            if (Realm.getDefaultConfiguration()?.schemaVersion!! < 4) {
                val config = RealmConfiguration.Builder()
                    .schemaVersion(4)
                    .migration(UniversalMigration())
                    .build()
                Realm.setDefaultConfiguration(config)
            }
        }
    }

    private lateinit var scanningFileObserver: FileObserver

    var isSyncing = false
    var isScanning = false
    var scanningCanceled = false

    private lateinit var ocr: MemeOcr

    /**
     * This function synchronizes folders index on database.
     *
     * @param realm
     * @param paths list of paths on device that contain photos.
     * @param officialPaths list of official paths that are almost guaranteed to contain photos
     *
     * @return new folders. This can be used to ask user whether to sync those new folders.
     */
    private fun syncFoldersIndex(
        realm: Realm,
        paths: List<String>,
        officialPaths: List<String>
    ): List<MemeFolder> {
        val newFolders = addNewFolders(realm, paths, officialPaths)
        deleteNotExistingFolders(realm, paths, officialPaths)
        return newFolders
    }

    /**
     * Discover new folders and add them to database.
     * You need to handle async yourself.
     *
     * @param realm
     * @param devicePaths list of paths on device that contain photos.
     * @param officialPaths list of official paths that are almost guaranteed to contain photos
     *
     * @return new folders. This can be used to ask user whether to sync those new folders.
     */
    private fun addNewFolders(
        realm: Realm,
        devicePaths: List<String>,
        officialPaths: List<String>
    ): List<MemeFolder> {
        val newFound: MutableList<MemeFolder> = mutableListOf()
        realm.executeTransaction { realm ->
            val onlyOfficials = devicePaths.isEmpty()
            for (path in if (onlyOfficials) officialPaths else devicePaths) {
                val folderFound = realm.where(MemeFolder::class.java)
                    .equalTo(MemeFolder.FOLDER_PATH, path).findFirst()

                if (folderFound == null) {
                    val folder = MemeFolder()
                    folder.folderPath = path
                    folder.isOfficial =
                        if (onlyOfficials) true
                        else officialPaths.contains(path)
                    realm.copyToRealm(folder)

                    newFound.add(folder)
                } else {
                    // If it was found, just check if it's official
                    folderFound.isOfficial =
                        if (onlyOfficials) true
                        else officialPaths.contains(path)
                }
            }
        }
        return newFound.toList()
    }

    /**
     * Delete folders that are still in database, but where deleted on device.
     * You need to handle async yourself.
     *
     * @param realm
     * @param devicePaths list of paths on device that contain photos.
     */
    private fun deleteNotExistingFolders(
        realm: Realm,
        devicePaths: List<String>,
        officialPaths: List<String>
    ) {
        realm.executeTransaction { realm ->
            val onlyOfficials = devicePaths.isEmpty()

            val result = if (onlyOfficials)
                realm.where(MemeFolder::class.java)
                    .equalTo(MemeFolder.IS_OFFICIAL, true)
                    .not().`in`(MemeFolder.FOLDER_PATH, officialPaths.toTypedArray()).findAll()
            else
                realm.where(MemeFolder::class.java)
                    .not().`in`(MemeFolder.FOLDER_PATH, devicePaths.toTypedArray())
                    .not().`in`(MemeFolder.FOLDER_PATH, officialPaths.toTypedArray())
                    .findAll()

            if (result.count() > 0) {
                Log.i(
                    TAG,
                    "Deleting folders from database, because they were not found on device: \n$result"
                )
                result.deleteAllFromRealm()
            }
        }
    }

    /**
     *  This only updates index in database. Does't scan any image
     *
     *  This function takes long.
     *  You need to handle async yourself.
     *
     *  @param realm
     *  @param folder takes [MemeFolder] to sync [Meme]s inside it.
     *  @return new [Meme]s that were added to database index.
     */
    private fun syncFolder(
        realm: Realm,
        folder: MemeFolder
    ): Pair<List<Meme>, List<MemeVideo>> {
        realm.executeTransaction {
            folder.lastSync = Calendar.getInstance().time
        }

        val imagesInFolderList = PainKiller().getAllImagesInFolder(File(folder.folderPath))
        val videosInFolderList = PainKiller().getAllVideosInFolder(File(folder.folderPath))

        deleteNotExistingMemesFromFolder(realm, folder, imagesInFolderList)
        val newMemes = addNewMemesToFolder(realm, folder, imagesInFolderList)

        deleteNotExistingVideosFromFolder(realm, folder, videosInFolderList)
        val newVideos = addNewVideosToFolder(realm, folder, videosInFolderList)

        return Pair(newMemes, newVideos)
    }

    /**
     * Adds new [Meme]s found on device to given [MemeFolder]
     *
     * This function takes long.
     * You need to handle async yourself.
     *
     * @param realm
     * @param folder [MemeFolder] where new memes will be added.
     * @param filesList list of image files inside [folder]. It is separate for better optimization,
     *        since getting list of 5000 images from folder can take some time.
     * @return new [Meme]s that were added to database index.
     */
    private fun addNewMemesToFolder(
        realm: Realm,
        folder: MemeFolder,
        filesList: List<File> // for better optimization
    ): List<Meme> {
        val newMemes = mutableListOf<Meme>()

        realm.executeTransaction { realm ->
            for (image in filesList) {
                // If Meme with certain path doesn't exist yet
                if (folder.memes.where().equalTo(
                        Meme.FILE_PATH,
                        image.absolutePath
                    ).findAll().count() == 0
                ) {
                    val meme = Meme()
                    meme.filePath = image.absolutePath
                    folder.memes.add(meme)
                    newMemes.add(meme)
                }
            }
        }
        return newMemes
    }

    /**
     * Deletes [Meme]s from database that weren't found on device.
     *
     * You need to handle async yourself.
     *
     * @param realm
     * @param folder [MemeFolder] where memes will be deleted.
     * @param filesList list of image files inside [folder]. It is separate for better optimization,
     *        since getting list of 5000 images from folder can take some time.
     */
    private fun deleteNotExistingMemesFromFolder(
        realm: Realm,
        folder: MemeFolder,
        filesList: List<File> // for better optimization
    ) {
        realm.executeTransaction { realm ->
            val result =
                folder.memes.where()
                    .not()
                    .`in`(
                        Meme.FILE_PATH,
                        filesList.map(File::getAbsolutePath).toTypedArray()
                    )
                    .findAll()

            result.deleteAllFromRealm()
        }
    }

    /**
     * Adds new [MemeVideo]s found on device to given [MemeFolder]
     *
     * This function takes long.
     * You need to handle async yourself.
     *
     * @param realm
     * @param folder [MemeFolder] where new memes will be added.
     * @param filesList list of image files inside [folder]. It is separate for better optimization,
     *        since getting list of 5000 images from folder can take some time.
     * @return new [MemeVideo]s that were added to database index.
     */
    private fun addNewVideosToFolder(
        realm: Realm,
        folder: MemeFolder,
        filesList: List<File> // for better optimization
    ): List<MemeVideo> {
        val newMemes = mutableListOf<MemeVideo>()

        realm.executeTransaction { realm ->
            for (video in filesList) {
                // If Meme with certain path doesn't exist yet
                if (folder.videos.where().equalTo(
                        MemeVideo.FILE_PATH,
                        video.absolutePath
                    ).findAll().count() == 0
                ) {
                    val memeVideo = MemeVideo()
                    memeVideo.filePath = video.absolutePath
                    folder.videos.add(memeVideo)
                    newMemes.add(memeVideo)
                }
            }
        }
        return newMemes
    }

    /**
     * Deletes [MemeVideo]s from database that weren't found on device.
     *
     * You need to handle async yourself.
     *
     * @param realm
     * @param folder [MemeFolder] where memes will be deleted.
     * @param filesList list of image files inside [folder]. It is separate for better optimization.
     */
    private fun deleteNotExistingVideosFromFolder(
        realm: Realm,
        folder: MemeFolder,
        filesList: List<File> // for better optimization
    ) {
        realm.executeTransaction { realm ->
            val result =
                folder.videos.where()
                    .not()
                    .`in`(
                        MemeVideo.FILE_PATH,
                        filesList.map(File::getAbsolutePath).toTypedArray()
                    )
                    .findAll()

            result.deleteAllFromRealm()
        }
    }

    /**
     * Synchronizes whole index. All [MemeFolder]s and all [Meme]s inside them.
     *
     * This function takes long.
     * You need to handle async yourself.
     *
     * @param realm [Realm]
     * @param ctx [Context]
     * @param syncFoldersIndex [Boolean] whether to sync folders index or not
     * @param syncUnofficialFoldersIndex [Boolean] whether to include unofficial
     *        folders in folders index sync
     */
    fun syncAllFolders(
        realm: Realm,
        ctx: Context,
        syncFoldersIndex: Boolean = true,
        syncUnofficialFoldersIndex: Boolean = true
    ): List<MemeFolder> {
        if (!PainKiller().hasStoragePermission(ctx)) {
            Log.w(TAG, "Can't sync: no storage permission!")
            return emptyList()
        }

        isSyncing = true

        var newFolders = emptyList<MemeFolder>()


        if (syncFoldersIndex) {
            newFolders = if (syncUnofficialFoldersIndex) {
                val foldersList = PainKiller().getAllFoldersWithImagesOrVideos(ctx)
                    .map(File::getAbsolutePath)
                syncFoldersIndex(
                    realm,
                    foldersList,
                    PainKiller().getOfficialFoldersWithImages().map(File::getAbsolutePath)
                )
            } else {
                syncFoldersIndex(
                    realm,
                    emptyList(),
                    PainKiller().getOfficialFoldersWithImages().map(File::getAbsolutePath)
                )
            }
        }

        val foldersToSync = realm.where(MemeFolder::class.java)
            .equalTo(MemeFolder.IS_SCANNABLE, true).findAll()
            // Only folders that have changed after last sync will be synced again.
            // This will skyrocket sync speed if user has some big-ass folder that is changed rarely
            .filter {
                Date(File(it.folderPath).lastModified())
                    .after(it.lastSync)
            }

        syncAllFoldersRecursive(realm, foldersToSync)

        cacheRoll(realm)

        isSyncing = false
        return newFolders
    }

    /**
     * Recursive part of [syncAllFolders].
     *
     * This function takes long.
     * You need to handle async yourself.
     *
     * @param realm [Realm]
     * @param foldersToSync list of [MemeFolder]s that are left for syncing.
     */
    private fun syncAllFoldersRecursive(
        realm: Realm,
        foldersToSync: List<MemeFolder>
    ) {
        if (foldersToSync.isEmpty()) {
            return
        }

        val folder = foldersToSync.first()
        syncFolder(realm, folder)
        Log.i(TAG, "Finished syncing folder ${File(folder.folderPath).name}")
        syncAllFoldersRecursive(realm, foldersToSync.drop(1))
    }

    /**
     * Scans whole [folder] recursively.
     * Make sure to sync [folder] before calling this.
     *
     * @param realm [Realm]
     * @param folder [MemeFolder] to scan.
     * @param progress callback when progress was made. First val contains number of all [Meme]s in [folder],
     *                 second is number of [Meme]s that are already scanned.
     * @param finished callback when whole scanning is finished. It's also called when [scanningCanceled] is true.
     */
    fun scanFolder(
        realm: Realm,
        ctx: Context,
        folder: MemeFolder,
        progress: (all: Int, scanned: Int) -> Unit,
        finished: () -> Unit // TODO: Do something with this
    ) {
        isScanning = true
        if (!::ocr.isInitialized) {
            ocr = MemeOcr(ctx)
        }
        val folderPath = folder.folderPath

        var changeInFolder = false
        scanningFileObserver = object : FileObserver(folderPath) {
            // keep in mind that this is different thread, we can't access realms there
            override fun onEvent(event: Int, file: String?) {
                val wantedEvents = intArrayOf(
                    CREATE,
                    CLOSE_WRITE,
                    DELETE,
                    DELETE_SELF,
                    MODIFY,
                    MOVED_FROM,
                    MOVED_TO,
                    MOVE_SELF
                )
                if (wantedEvents.contains(event)) {
                    Log.d(TAG, "File observer, change in $folderPath/$file")
                    changeInFolder = true
                }
            }
        }
        scanningFileObserver.startWatching()


        val notScannedMemes = folder.memes.where()
            .equalTo(Meme.IS_SCANNED, false).findAll()
        if (notScannedMemes.count() > 0) {
            val meme = notScannedMemes.first()!!

            try {
                val text = ocr.scanImage(ctx, File(meme.filePath))

                if (scanningCanceled) {
                    isScanning = false
                    return
                }
                try {
                    realm.executeTransaction {
                        meme.rawText = text
                        meme.isScanned = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Probably something wrong with current index if error occurred
                syncFolder(realm, folder)
            }

            if (scanningCanceled) {
                scanningFileObserver.stopWatching()
                isScanning = false
                finished()
                return
            }
            val all = folder.memes.count()
            val scanned =
                folder.memes.where().equalTo(Meme.IS_SCANNED, true).findAll().count()
            progress(all, scanned)

            if (changeInFolder) {
                syncFolder(realm, folder)
            }

            scanningFileObserver.stopWatching()
            // This is theoretically recursion, but there is nothing big to be left in stack
            scanFolder(realm, ctx, folder, progress, finished)


        } else {
            isScanning = false
            finished()
        }

    }

    /**
     * Scans all [MemeFolder]s with IS_SCANNABLE flag.
     *
     * @param realm [Realm]
     * @param progress callback when progress was made. It contains folder which is scanned right now,
     *                  total number of images in it and number of currently scanned images.
     * @param finished callback when everything was finished.
     */
    fun scanAllFolders(
        realm: Realm,
        ctx: Context,
        progress: (folder: MemeFolder, all: Int, scanned: Int) -> Unit,
        finished: () -> Unit
    ) {
        if (!PainKiller().hasStoragePermission(ctx)) {
            Log.w(TAG, "Can't scan: no storage permission!")
            finished()
            return
        }

        if (scanningCanceled) {
            finished()
            return
        }

        var folderToScan: MemeFolder? = null
        for (folder in realm.where(MemeFolder::class.java)
            .equalTo(MemeFolder.IS_SCANNABLE, true)
            .findAll()
        ) {
            if (!MemeFolder.isFolderFullyScanned(folder)) {
                folderToScan = folder
                break
            }
        }
        if (folderToScan == null) {
            finished()
            return
        }

        val folderName = File(folderToScan.folderPath).name

        scanFolder(
            realm, ctx, folderToScan,
            { max, scanned ->
                // progress
                Log.i(TAG, "Scanning folder $folderName, progress: $scanned, max: $max")
                progress(folderToScan, max, scanned)
            },
            {
                // finished
                Log.i(TAG, "Finished scanning folder $folderName")
                scanAllFolders(realm, ctx, progress, finished)
            }
        )

    }

    fun getMemeRoll(realm: Realm): List<File> {
        val filesList = realm
            .where(MemeFolder::class.java)
            .equalTo(MemeFolder.IS_SCANNABLE, true)
            .findAll()
            .map { it.memes }
            .flatten()
            .map { File(it.filePath) }
            .sortedByDescending { it.lastModified() }

        return filesList
    }

    fun getCachedMemeRoll(realm: Realm): List<File> {
        var cachedRoll = realm.where(MemeRoll::class.java).findFirst()
        if (cachedRoll == null) {
            MemeRoll.cacheRoll(realm, getMemeRoll(realm))
            cachedRoll = realm.where(MemeRoll::class.java).findFirst()!!
        }
        if (cachedRoll.roll.count() == 0) {

        }
        return cachedRoll.roll.map { File(it) }
    }

    fun cacheRoll(realm: Realm) {
        MemeRoll.cacheRoll(realm, getMemeRoll(realm))
    }

}