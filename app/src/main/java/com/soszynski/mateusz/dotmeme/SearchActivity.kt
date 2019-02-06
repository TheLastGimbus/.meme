package com.soszynski.mateusz.dotmeme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.WindowManager
import android.widget.ImageView
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_search.*
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.ByteArrayOutputStream
import java.util.concurrent.Future


class SearchActivity : AppCompatActivity() {
    lateinit var realm: Realm
    private var renderImagesTask: Future<Unit>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        setSupportActionBar(activity_search_toolbar)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        activity_search_toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        realm = Realm.getDefaultInstance()

        button_enter.setOnClickListener {
            val s = editText_search.text
            if (s.isNotEmpty()) {
                linearLayout_results.removeAllViews()

                val finalList = Memebase()
                    .search(realm, s.toString())
                    .map(Meme::filePath)

                renderImagesTask = doAsync {
                    for (meme in finalList) {
                        if(renderImagesTask != null && renderImagesTask!!.isCancelled){
                            break
                        }
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

    override fun onDestroy() {
        super.onDestroy()
        renderImagesTask?.cancel(true)
    }

    companion object {
        const val TAG = "SearchActivity"
    }
}
