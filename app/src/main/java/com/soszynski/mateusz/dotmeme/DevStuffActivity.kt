package com.soszynski.mateusz.dotmeme

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ablanco.imageprovider.ImageProvider
import com.ablanco.imageprovider.ImageSource
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_dev_stuff.*
import org.jetbrains.anko.defaultSharedPreferences

/**
 * This is activity where i put all of the stuff for testing.
 * It will be deleted in the future.
 */

class DevStuffActivity : AppCompatActivity() {
    companion object {
        const val TAG = "DevStuffActivity"
    }

    private lateinit var realm: Realm

    private lateinit var prefChangeListener: SharedPreferences.OnSharedPreferenceChangeListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dev_stuff)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        val prefs = defaultSharedPreferences
        realm = Realm.getDefaultInstance()

        switch_scanning_paused.isChecked = prefs.getBoolean(Prefs.PREF_SCANNING_PAUSED, false)
        switch_scanning_paused.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit()
                .putBoolean(Prefs.PREF_SCANNING_PAUSED, isChecked)
                .apply()
        }
        prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == Prefs.PREF_SCANNING_PAUSED) {
                switch_scanning_paused.isChecked = prefs.getBoolean(Prefs.PREF_SCANNING_PAUSED, false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)

        button_scan.setOnClickListener {
            textView_text.text = "Wait..."

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

            }
        }

        button_settings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        button_sync.setOnClickListener {
            val request = OneTimeWorkRequestBuilder<FullSyncWorker>().build()
            WorkManager.getInstance().enqueue(request)
        }
    }
}
