package com.soszynski.mateusz.dotmeme.memebase

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

class MemeOcr {
    companion object {
        private const val DEFAULT_LANGUAGE = "eng_best"
    }

    private fun setup(ctx: Context, lang: String): File {
        val tessdataDir = File(ctx.noBackupFilesDir, "tessdata/")
        if (!tessdataDir.isDirectory) {
            tessdataDir.mkdirs()
        }
        val traineddata = lang + ".traineddata"
        val targetFile = File(tessdataDir, traineddata)
        if (!targetFile.exists()) {
            ctx.assets.open("traineddata/" + traineddata)
                .copyTo(targetFile.outputStream())
        }
        return targetFile
    }

    @Throws(Exception::class)
    fun scanImage(ctx: Context, bitmap: Bitmap): String {
        var text = ""

        val tess = TessBaseAPI()
        tess.init(
            setup(ctx, DEFAULT_LANGUAGE).parentFile.parent,
            DEFAULT_LANGUAGE,
            TessBaseAPI.OEM_LSTM_ONLY
        )
        tess.setImage(bitmap)
        text = tess.utF8Text // DO NOT change this to kotlin-style !
        tess.end()
        return text
    }
}