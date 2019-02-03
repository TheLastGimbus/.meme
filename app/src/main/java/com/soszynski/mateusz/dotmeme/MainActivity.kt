package com.soszynski.mateusz.dotmeme

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import com.ablanco.imageprovider.ImageProvider
import com.ablanco.imageprovider.ImageSource
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.defaultSharedPreferences

class MainActivity : AppCompatActivity() {
    companion object {
        val TAG = "MainActivity"
    }

    private lateinit var realm: Realm

    private lateinit var prefChangeListener: SharedPreferences.OnSharedPreferenceChangeListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        realm = Realm.getDefaultInstance()

        val prefs = defaultSharedPreferences

        Notifications().createChannels(this)

        permission()

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

        button_search.setOnClickListener {
            startActivity(Intent(this, SearchActivity::class.java))
        }

        button_sync.setOnClickListener {
            MemeManagerIntentService.startActionSyncAll(this)
        }

    }

    fun permission() {
        if (!PainKiller().hasStoragePermission(this)) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
    }
}
