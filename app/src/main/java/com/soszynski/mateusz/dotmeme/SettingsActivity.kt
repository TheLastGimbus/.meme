package com.soszynski.mateusz.dotmeme

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_settings.*

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(activity_settings_toolbar)

        activity_settings_toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }
}
