package com.soszynski.mateusz.dotmeme

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.support.v4.content.ContextCompat
import android.util.Log
import java.io.File

/**
 * This is class where are all the annoying, boilerplate things.
 * But it isn't meant to handle permissions and async, so do that manually when you use it.
 */

class PainKiller {
    companion object {
        const val TAG = "PAINKILLER"
        val imageFileExtensions = listOf("jpg", "png", "jpeg", "jpe", "bmp")
    }

    // TODO: hiddenFolders flag
    /**
     * Requires READ_EXTERNAL_STORAGE permission.
     *
     * @return all folders with images found on device.
     */
    fun getAllFoldersWithImages(ctx: Context): List<File> {
        val dirs =
            searchForPhotoDirs(ctx, Environment.getExternalStorageDirectory()) +
                    searchForPhotoDirs(ctx, File("/storage"))
        var prettyDirs = ""
        for (dir in dirs) {
            prettyDirs += dir.absolutePath + "\n"
        }
        Log.i(TAG, "All folders with photos: \n$prettyDirs")

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
            return@listFiles (file.isFile && isFileImage(file))
        }.toList()
    }

    /**
     * Recursively searching for folders containing images.
     *
     * @return list of found folders.
     */
    private fun searchForPhotoDirs(ctx: Context, path: File): List<File> {
        val dirs = mutableListOf<File>()

        val childrenPaths = path.listFiles()
        if (childrenPaths.isNullOrEmpty()) {
            return dirs.toList()
        }

        for (file in childrenPaths) {
            if (file.isHidden) {
                continue
            }
            if (file.isDirectory) {
                dirs.addAll(searchForPhotoDirs(ctx, file)) // recursion
            } else if (file.isFile && isFileImage(file)) {
                dirs.add(path)
                break
            }
        }

        return dirs.toList()
    }

    private fun isFileImage(file: File): Boolean {
        return imageFileExtensions.contains(file.extension.toLowerCase())
    }


    /**
     * Requires READ_EXTERNAL_STORAGE permission.
     *
     * @return real path from given [Uri]
     */
    fun getRealPathFromUri(ctx: Context, uri: Uri): String {
        var filePath = ""
        val wholeID = DocumentsContract.getDocumentId(uri)
        val id = wholeID.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
        val column = arrayOf(MediaStore.Images.Media.DATA)
        val sel = MediaStore.Images.Media._ID + "=?"
        val cursor = ctx.contentResolver.query( // this requires permission
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            column, sel, arrayOf(id), null
        )
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
}