package com.soszynski.mateusz.dotmeme

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.app.ActivityCompat
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.view.MenuItem
import com.ablanco.imageprovider.ImageProvider
import com.ablanco.imageprovider.ImageSource
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import io.doorbell.android.Doorbell
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.app_bar_main.*
import kotlinx.android.synthetic.main.content_main.*
import org.jetbrains.anko.defaultSharedPreferences


class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    companion object {
        const val TAG = "MainActivity"
    }

    private lateinit var realm: Realm

    private lateinit var prefChangeListener: SharedPreferences.OnSharedPreferenceChangeListener


    private fun permission() {
        if (!PainKiller().hasStoragePermission(this)) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(activity_main_toolbar)

        val toggle = ActionBarDrawerToggle(
            this,
            drawer_layout,
            activity_main_toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()

        nav_view.setNavigationItemSelectedListener(this)



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

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            R.id.nav_settings -> {

            }
            R.id.nav_feedback -> {
                Doorbell(
                    this,
                    10050,
                    "Fpg9YDZGtEgYzsbCHLRgErNms46Em6XiJpl0NtscLzH246DrKRLKwurjSEdbaDLP"
                ).show()  // please don't use my private key ;)
            }
        }

        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
    }
}
