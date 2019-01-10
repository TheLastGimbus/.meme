package com.soszynski.mateusz.dotmeme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.ImageView
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_search.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.ByteArrayOutputStream


class SearchActivity : AppCompatActivity() {
    lateinit var realm: Realm

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        realm = Realm.getDefaultInstance()

        button_enter.setOnClickListener {
            val s = editText_search.text
            if (s.isNotEmpty()) {
                linearLayout_results.removeAllViews()

                val finalList = Memebase()
                    .search(realm, Meme::class.java, s.toString(), "rawText")
                    .map(Meme::filePath)

                doAsync {
                    for (meme in finalList) {
                        val image = ImageView(this@SearchActivity)
                        val bitmap = BitmapFactory.decodeFile(meme)
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream)

                        val byteArray = stream.toByteArray()
                        val compressedBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)

                        image.setImageBitmap(compressedBitmap)
                        uiThread {
                            linearLayout_results.addView(image)
                        }
                    }
                }
            }
        }
    }

    companion object {
        val TAG = "SearchActivity"
    }
}
