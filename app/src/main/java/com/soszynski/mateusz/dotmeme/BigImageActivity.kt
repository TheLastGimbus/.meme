package com.soszynski.mateusz.dotmeme

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_big_image.*
import java.io.File

class BigImageActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_big_image)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)


        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        val srcStr = intent.getStringExtra(IMAGE_SRC_PATH)
        if (srcStr.isNullOrEmpty() == false) {
            val file = File(srcStr)
            Picasso.get()
                .load(file)
                .error(R.drawable.ic_error_outline_gray_24dp)
                .into(imageView_big_meme)
        }
    }


    companion object {
        const val TAG = "BigImageActivity"
        const val IMAGE_SRC_PATH = "param.image_src_path"
    }
}
