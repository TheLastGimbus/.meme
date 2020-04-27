package com.soszynski.mateusz.dotmeme.memebase

import android.content.Context
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

class MemeOcr(ctx: Context) {
    companion object {
        private const val DEFAULT_LANGUAGE = "eng_fast"
    }

    private val tess = TessBaseAPI()

    init {
        val file = setupFiles(ctx, DEFAULT_LANGUAGE)
        tess.init(
            file.parentFile.parent,
            DEFAULT_LANGUAGE,
            TessBaseAPI.OEM_LSTM_ONLY
        )
    }

    private fun setupFiles(ctx: Context, lang: String): File {
        val tessdataDir = File(ctx.noBackupFilesDir, "tessdata/")
        if (!tessdataDir.isDirectory) {
            tessdataDir.mkdirs()
        }
        val traineddata = "$lang.traineddata"
        val targetFile = File(tessdataDir, traineddata)
        val oldFile = File(tessdataDir, "eng_best.traineddata")
        if (oldFile.exists()) {
            oldFile.delete()
        }
        if (!targetFile.exists()) {
            ctx.assets.open("traineddata/$traineddata")
                .copyTo(targetFile.outputStream())
        }
        return targetFile
    }

    @Throws(Exception::class)
    fun scanImage(ctx: Context, file: File): String {
        var text = ""
        tess.setImage(file)
        text = tess.getUTF8Text() // DO NOT change this to kotlin-style !
        return text
    }
}