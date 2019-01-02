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

/*
 * This is class where I put all the annoying, boilerplate things.
 * But it isn't meant to handle permissions or things like that, so do that manually when you use it.
 */

class PainKiller {
    val TAG = "PAINKILLER"

    // Requires READ_EXTERNAL_STORAGE permission.
    fun getAllFoldersWithImages(context: Context): List<File> {
        val dirs =
            searchForPhotoDirs(context, Environment.getExternalStorageDirectory()) +
                    searchForPhotoDirs(context, File("/storage"))
        var prettyDirs = ""
        for (dir in dirs) {
            prettyDirs += dir.absolutePath + "\n"
        }
        Log.i(TAG, "All folders with photos: \n$prettyDirs")

        return dirs.toList()
    }

    private fun searchForPhotoDirs(context: Context, path: File): List<File> {
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
                dirs.addAll(searchForPhotoDirs(context, file)) // recursion
            } else if (file.isFile && isFileImage(file)) {
                dirs.add(path)
                break
            }
        }

        return dirs.toList()
    }

    private fun isFileImage(file: File): Boolean {
        val okFileExtensions = arrayOf("jpg", "png", "jpeg", "raw", "bmp")

        for (extension in okFileExtensions) {
            if (file.name.toLowerCase().endsWith(extension)) {
                return true
            }
        }
        return false
    }


    // I don't fucking know what does it do.
    // It's copied from internet. And it works. That's all I care about.
    // Btw it requires READ_EXTERNAL_STORAGE permission.
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