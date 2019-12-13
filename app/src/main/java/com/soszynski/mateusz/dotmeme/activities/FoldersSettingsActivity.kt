package com.soszynski.mateusz.dotmeme.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.soszynski.mateusz.dotmeme.R
import kotlinx.android.synthetic.main.activity_folders_settings.*

class FoldersSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folders_settings)

        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }
}
