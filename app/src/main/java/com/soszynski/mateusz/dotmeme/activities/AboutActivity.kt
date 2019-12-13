package com.soszynski.mateusz.dotmeme.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.soszynski.mateusz.dotmeme.R
import kotlinx.android.synthetic.main.activity_about.*

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }
}
