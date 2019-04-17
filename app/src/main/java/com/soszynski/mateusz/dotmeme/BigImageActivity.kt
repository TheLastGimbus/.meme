package com.soszynski.mateusz.dotmeme

import android.content.Intent
import android.os.Bundle
import android.support.v4.content.FileProvider
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.squareup.picasso.Picasso
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_big_image.*
import org.apache.commons.lang3.StringUtils
import java.io.File

class BigImageActivity : AppCompatActivity() {
    lateinit var realm: Realm

    inner class ImageAdapter(val imagesPathsList: List<String>) : PagerAdapter() {

        override fun isViewFromObject(p0: View, p1: Any): Boolean {
            return p0 == p1
        }

        override fun getCount(): Int {
            return imagesPathsList.count()
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val imageView = ImageView(this@BigImageActivity)
            imageView.layoutParams =
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

            Picasso.get()
                .load(File(imagesPathsList[position]))
                .error(R.drawable.ic_error_outline_gray_24dp)
                .into(imageView)

            container.addView(imageView)

            return imageView
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            container.removeView(`object` as View)
        }
    }


    private fun logMeme(meme: Meme) {
        val niceText = meme.rawText.replace("\n", "\n    ")
        Log.i(
            TAG, "Meme data: \n" +
                    "Path: ${meme.filePath} \n" +
                    "Ocr ver.: ${meme.ocrVersion} \n" +
                    "Text: \n" +
                    "    $niceText \n" +
                    "Castrated text: \n" +
                    "    ${StringUtils.stripAccents(niceText)}\n" +
                    "Labels: ${meme.labels}"
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        realm = Realm.getDefaultInstance()
        setContentView(R.layout.activity_big_image)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)


        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }


        val memesPathsArray = intent.getStringArrayExtra(IMAGES_SRC_PATH_ARRAY)
        val imagesAdapter = ImageAdapter(memesPathsArray.toList())
        viewPager.adapter = imagesAdapter
        viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(p0: Int) {}

            override fun onPageScrolled(p0: Int, p1: Float, p2: Int) {}

            override fun onPageSelected(p0: Int) {
                val meme = realm.where(Meme::class.java).equalTo(Meme.FILE_PATH, memesPathsArray[p0]).findFirst()
                meme?.let { logMeme(it) }
            }

        })
        viewPager.currentItem = intent.getIntExtra(START_IMAGE_INDEX, 0)

        realm.where(Meme::class.java).equalTo(Meme.FILE_PATH, memesPathsArray[viewPager.currentItem])
            .findFirst()?.let { logMeme(it) }


        button_share.setOnClickListener {
            val file = File(memesPathsArray[viewPager.currentItem])
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
    }


    companion object {
        const val TAG = "BigImageActivity"
        const val START_IMAGE_INDEX = "param.start_image_index"
        const val IMAGES_SRC_PATH_ARRAY = "param.images_src_path_array"
    }
}
