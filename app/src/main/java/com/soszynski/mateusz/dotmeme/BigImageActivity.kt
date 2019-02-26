package com.soszynski.mateusz.dotmeme

import android.content.Intent
import android.os.Bundle
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.squareup.picasso.Picasso
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_big_image.*
import org.apache.commons.lang3.StringUtils
import java.io.File

class BigImageActivity : AppCompatActivity() {
    lateinit var realm: Realm

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        realm = Realm.getDefaultInstance()
        setContentView(R.layout.activity_big_image)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)


        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }


        val srcStr = intent.getStringExtra(IMAGE_SRC_PATH)
        if (!srcStr.isNullOrEmpty()) {
            val file = File(srcStr)
            Picasso.get()
                .load(file)
                .error(R.drawable.ic_error_outline_gray_24dp)
                .into(imageView_big_meme)


            button_share.setOnClickListener {
                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "image/${file.extension}"
                val uri = FileProvider.getUriForFile(
                    this,
                    applicationContext.packageName + ".provider",
                    file
                )
                intent.putExtra(Intent.EXTRA_STREAM, uri)
                startActivity(Intent.createChooser(intent, "Share meme"))
            }


            val meme = realm.where(Meme::class.java).equalTo(Meme.FILE_PATH, srcStr).findFirst()
            val niceText = meme?.rawText?.replace("\n", "\n    ")
            Log.i(
                TAG, "Meme data: \n" +
                        "Path: ${meme?.filePath} \n" +
                        "Ocr ver.: ${meme?.ocrVersion} \n" +
                        "Text: \n" +
                        "    $niceText \n" +
                        "Castrated text: \n" +
                        "    ${StringUtils.stripAccents(niceText)}" +
                        "Labels: ${meme?.labels}"
            )
        }
    }


    companion object {
        const val TAG = "BigImageActivity"
        const val IMAGE_SRC_PATH = "param.image_src_path"
    }
}
