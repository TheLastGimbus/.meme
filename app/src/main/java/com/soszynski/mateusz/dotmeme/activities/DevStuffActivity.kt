package com.soszynski.mateusz.dotmeme.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.soszynski.mateusz.dotmeme.FullSyncWorker
import com.soszynski.mateusz.dotmeme.R
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_dev_stuff.*

/**
 * This is activity where i put all of the stuff for testing.
 * It will be deleted in the future.
 */

class DevStuffActivity : AppCompatActivity() {
    companion object {
        const val TAG = "DevStuffActivity"
    }

    private lateinit var realm: Realm

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dev_stuff)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        realm = Realm.getDefaultInstance()

        button_scan.setOnClickListener {
            textView_text.text = "Wait..."

            // TODO: Replace this with something else
            /*
            ImageProvider(this).getImage(ImageSource.GALLERY) { bitmap ->
                if (bitmap != null) {
                    imageView_meme.setImageBitmap(bitmap)

                    val fireImage = FirebaseVisionImage.fromBitmap(bitmap)
                    val ocr = FirebaseVision.getInstance().onDeviceTextRecognizer

                    ocr.processImage(fireImage)
                        .addOnSuccessListener { fireText ->
                            textView_text.text = fireText.text
                        }
                        .addOnFailureListener { e ->
                            textView_text.text = "ERROR"
                            e.printStackTrace()
                        }
                }

            }*/
        }

        button_settings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        button_sync.setOnClickListener {
            val request = OneTimeWorkRequestBuilder<FullSyncWorker>().build()
            WorkManager.getInstance(this).enqueue(request)
        }
    }
}
