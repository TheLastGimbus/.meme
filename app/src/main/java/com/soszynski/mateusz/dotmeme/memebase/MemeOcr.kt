package com.soszynski.mateusz.dotmeme.memebase

import android.graphics.Bitmap
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage

class MemeOcr {

    private val ocr = FirebaseVision.getInstance().onDeviceTextRecognizer

    @Throws(Exception::class)
    fun scanImage(bitmap: Bitmap): String {
        var text = ""

        val fireImage = FirebaseVisionImage.fromBitmap(bitmap)
        val process = ocr.processImage(fireImage)
            .addOnSuccessListener { text = it.text }
            .addOnFailureListener { throw it }
        while (!process.isComplete);
        return text
    }
}