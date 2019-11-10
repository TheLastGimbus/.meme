package com.soszynski.mateusz.dotmeme

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.firebase.perf.FirebasePerformance
import java.io.File


/**
 * This is class where are all the annoying, boilerplate things.
 * But it isn't meant to handle permissions and async, so do that manually when you use it.
 */

class PainKiller {
    companion object {
        const val TAG = "PAINKILLER"
        val imageFileExtensions = listOf("jpg", "png", "jpeg", "jpe", "bmp")
        val videoFileExtensions = listOf("mp4", "avi", "mov")
    }


    /**
     * @return official folders that are almost guaranteed to contain images,
     * but they aren't everything
     */
    // Don't even ask me why this function is so full of try-catch'es
    fun getOfficialFoldersWithImages(): List<File> {
        val officialFoldersList = mutableListOf<File>()
        val typesToAdd = mutableListOf<String>()
        try {
            typesToAdd.add(Environment.DIRECTORY_PICTURES)
            typesToAdd.add(Environment.DIRECTORY_DOWNLOADS)
            typesToAdd.add("Screenshots") // Screenshots are retarded, need to type them manually
        } catch (e: Exception) {
            e.printStackTrace()
        }

        for (type in typesToAdd) {
            try {
                officialFoldersList.add(Environment.getExternalStoragePublicDirectory(type))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        try {
            officialFoldersList.addAll(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    .listFiles { dir, _ -> dir.isDirectory }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        Log.i(TAG, "Official dirs: ${officialFoldersList.toTypedArray().contentDeepToString()}")
        return officialFoldersList.toList()
    }

    /**
     * Requires READ_EXTERNAL_STORAGE permission.
     *
     * @return all folders with images found on device.
     */
    @Deprecated("Use getAllFoldersWithImagesOrVideos")
    fun getAllFoldersWithImages(ctx: Context, includeHidden: Boolean = false): List<File> {
        val trace = FirebasePerformance.getInstance()
            .newTrace("painkiller_get_all_folders_with_images")
        trace.start()
        val dirs =
            searchForPhotoDirs(ctx, Environment.getExternalStorageDirectory()) +
                    searchForPhotoDirs(ctx, File("/storage"))
        var prettyDirs = ""
        for (dir in dirs) {
            prettyDirs += dir.absolutePath + "\n"
        }
        Log.i(TAG, "All folders with photos: \n$prettyDirs")

        trace.stop()

        return dirs.toList()
    }

    fun getAllFoldersWithImagesOrVideos(ctx: Context, includeHidden: Boolean = false): List<File> {
        val trace = FirebasePerformance.getInstance()
            .newTrace("painkiller_get_all_folders_with_images")
        trace.start()
        val dirs =
            (searchForPhotoOrVideoDirs(
                ctx,
                Environment.getExternalStorageDirectory(),
                includeHidden
            ) + searchForPhotoOrVideoDirs(
                ctx,
                File("/storage"),
                includeHidden
            )).distinct()
        var prettyDirs = ""
        for (dir in dirs) {
            prettyDirs += dir.absolutePath + "\n"
        }
        Log.i(TAG, "All folders with photos or videos: \n$prettyDirs")

        trace.stop()

        return dirs.toList()
    }

    /**
     * @return all images inside given [folder].
     */
    fun getAllImagesInFolder(folder: File): List<File> {
        if (!folder.isDirectory) {
            return emptyList()
        }

        return folder.listFiles { file ->
            // Idk, is it 1024 or 1000, but let's just keep it 1024
            return@listFiles (
                    file.isFile &&
                            isFileImage(file) &&
                            file.canRead() &&
                            (file.length() / 1024) > 4
                    )
        }.toList()
    }

    fun getAllVideosInFolder(folder: File): List<File> {
        if (!folder.isDirectory) {
            return emptyList()
        }

        return folder.listFiles { file ->
            // Idk, is it 1024 or 1000, but let's just keep it 1024
            return@listFiles (
                    file.isFile &&
                            isFileVideo(file) &&
                            file.canRead() &&
                            (file.length() / 1024) > 4
                    )
        }.toList()
    }

    fun getAllImagesOrVideosInFolder(folder: File): List<File> {
        if (!folder.isDirectory) {
            return emptyList()
        }

        return folder.listFiles { file ->
            // Idk, is it 1024 or 1000, but let's just keep it 1024
            return@listFiles (
                    file.isFile &&
                            (isFileImage(file) || isFileVideo(file)) &&
                            file.canRead() &&
                            (file.length() / 1024) > 4
                    )
        }.toList()
    }

    /**
     * Recursively searching for folders containing images.
     *
     * @return list of found folders.
     */
    @Deprecated("Use searchForPhotoOrVideoDirs")
    private fun searchForPhotoDirs(
        ctx: Context,
        path: File,
        includeHidden: Boolean = false
    ): List<File> {
        val dirs = mutableListOf<File>()

        val childrenPaths = path.listFiles()
        if (childrenPaths.isNullOrEmpty()) {
            return dirs.toList()
        }

        for (file in childrenPaths) {
            if (file.isHidden && !includeHidden) {
                continue
            }
            if (file.isDirectory) {
                dirs.addAll(searchForPhotoDirs(ctx, file, includeHidden)) // recursion
            } else if (
                file.isFile &&
                !dirs.contains(path) &&
                isFileImage(file) &&
                (includeHidden || !File(file.parent, ".nomedia").exists())
            ) {
                dirs.add(path)
            }
        }

        return dirs.toList()
    }

    private fun searchForPhotoOrVideoDirs(
        ctx: Context,
        path: File,
        includeHidden: Boolean = false
    ): List<File> {
        val dirs = mutableListOf<File>()

        val childrenPaths = path.listFiles()
        if (childrenPaths.isNullOrEmpty()) {
            return dirs.toList()
        }

        for (file in childrenPaths) {
            if (file.isHidden && !includeHidden) {
                continue
            }
            if (file.isDirectory) {
                dirs.addAll(searchForPhotoDirs(ctx, file, includeHidden)) // recursion
            } else if (
                file.isFile &&
                !dirs.contains(path) &&
                (isFileVideo(file) || isFileImage(file)) &&
                (includeHidden || !File(file.parent, ".nomedia").exists())
            ) {
                dirs.add(path)
            }
        }

        return dirs.toList()
    }

    private fun isFileImage(file: File): Boolean {
        return imageFileExtensions.contains(file.extension)
    }

    private fun isFileVideo(file: File): Boolean {
        return videoFileExtensions.contains(file.extension)
    }


    /**
     * Requires READ_EXTERNAL_STORAGE permission.
     *
     * @return real path from given [Uri]
     */
    @RequiresApi(Build.VERSION_CODES.KITKAT)
    fun getRealPathFromUri(ctx: Context, uri: Uri): String? {
        var filePath = ""
        val wholeID = DocumentsContract.getDocumentId(uri)
        val id = wholeID.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
        val column = arrayOf(MediaStore.Images.Media.DATA)
        val sel = MediaStore.Images.Media._ID + "=?"
        val cursor = ctx.contentResolver.query( // this requires permission
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            column, sel, arrayOf(id), null
        )
        if (cursor == null) {
            return null
        }
        val columnIndex = cursor.getColumnIndex(column[0])
        if (cursor.moveToFirst()) {
            filePath = cursor.getString(columnIndex)
        }
        cursor.close()
        return filePath
    }


    fun hasStoragePermission(ctx: Context): Boolean {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun isPowerSaverOn(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val powerManager = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isPowerSaveMode
        }
        return false
    }
}