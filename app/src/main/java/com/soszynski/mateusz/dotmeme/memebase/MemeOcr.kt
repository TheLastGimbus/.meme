package com.soszynski.mateusz.dotmeme.memebase

import android.graphics.Bitmap
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage

class MemeOcr {

    private val ocr = FirebaseVision.getInstance().onDeviceTextRecognizer

    @Throws(Exception::class)
    fun scanImage(bitmap: Bitmap): String {
        var finished = false
        var text = ""

        val fireImage = FirebaseVisionImage.fromBitmap(bitmap)
        ocr.processImage(fireImage)
            .addOnSuccessListener { text = it.text }
            .addOnFailureListener { throw it }
            .continueWith { finished = true }
        while (!finished);
        return text
    }
}