package com.soszynski.mateusz.dotmeme.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.github.chrisbanes.photoview.PhotoView
import com.soszynski.mateusz.dotmeme.Meme
import com.soszynski.mateusz.dotmeme.PainKiller
import com.soszynski.mateusz.dotmeme.Prefs
import com.soszynski.mateusz.dotmeme.R
import com.soszynski.mateusz.dotmeme.memebase.Memebase
import com.watermark.androidwm_light.WatermarkBuilder
import com.watermark.androidwm_light.bean.WatermarkText
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_big_image.*
import org.jetbrains.anko.defaultSharedPreferences
import java.io.File


class BigImageActivity : AppCompatActivity() {
    lateinit var realm: Realm
    private var fullscreen = false

    inner class ImageAdapter(val imagesPathsList: List<String>) : PagerAdapter() {

        override fun isViewFromObject(p0: View, p1: Any): Boolean {
            return p0 == p1
        }

        override fun getCount(): Int {
            return imagesPathsList.count()
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val imageView = PhotoView(this@BigImageActivity)
            imageView.layoutParams =
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

            imageView.setOnPhotoTapListener { view, x, y ->
                setFullscreen(!fullscreen)
            }


            Glide.with(this@BigImageActivity)
                .load(File(imagesPathsList[position]))
                .error(R.drawable.ic_error_outline_gray_24dp)
                .transition(DrawableTransitionOptions.withCrossFade(75))
                .into(imageView)

            container.addView(imageView)

            return imageView
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            container.removeView(`object` as View)
        }
    }

    private fun setFullscreen(setFull: Boolean) {
        if (setFull) {
            var goodCall = true
            val endListener = object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    super.onAnimationEnd(animation)
                    // because the listener keeps listening - even if we want to show it back
                    if (goodCall) {
                        nav_bar_top.visibility = View.GONE
                        nav_bar_bottom.visibility = View.GONE
                        goodCall = false
                    }
                }
            }

            nav_bar_top.animate()
                .alpha(0.0f)
                .setListener(endListener)

            nav_bar_bottom.animate()
                .alpha(0.0f)
                .setListener(endListener)
        } else {
            nav_bar_top.visibility = View.VISIBLE
            nav_bar_bottom.visibility = View.VISIBLE
            nav_bar_top.animate()
                .alpha(1.0f)
            nav_bar_bottom.animate()
                .alpha(1.0f)
        }

        fullscreen = setFull
    }

    private fun logMeme(meme: Meme) {
        val niceText = meme.rawText.replace("\n", "\n    ")
        Log.i(
            TAG, "Meme data: \n" +
                    "Path: ${meme.filePath} \n" +
                    "Ocr ver.: ${meme.ocrVersion} \n" +
                    "Text: \n" +
                    "    $niceText \n" +
                    "Labels: ${meme.labels}\n"
        )
    }

    private fun shareMeme(memeFile: File, watermark: Boolean = false) {

        val file = if (watermark) {
            val tmpDir = PainKiller().clearTemporary(this)
            val bitmap = BitmapFactory.decodeFile(memeFile.absolutePath)
            WatermarkBuilder.create(
                this,
                bitmap
            )
                .loadWatermarkText(
                    WatermarkText(getString(R.string.watermark_text))
                        .setTextAlpha(175)
                        .setPositionY(0.85)
                        .setTextSize(10.0)
                        .setBackgroundColor(Color.WHITE)
                        .setTextColor(Color.BLACK)
                )
                .watermark
                .saveToLocalPng(tmpDir.resolve("img").absolutePath)

            PainKiller().getTemporaryFirstFile(this)!!

        } else {
            memeFile
        }

        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "image/${file.extension}"
        val uri = FileProvider.getUriForFile(
            this,
            applicationContext.packageName + ".provider",
            file
        )
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        startActivity(Intent.createChooser(intent, getString(R.string.share_meme)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Memebase.handleRealmConfigs()
        realm = Realm.getDefaultInstance()
        setContentView(R.layout.activity_big_image)
        button_back.setOnClickListener {
            onBackPressed()
        }


        val memesPathsArray = intent.getStringArrayExtra(IMAGES_SRC_PATH_ARRAY)
        val imagesAdapter = ImageAdapter(memesPathsArray.toList())
        viewPager.adapter = imagesAdapter
        viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(p0: Int) {}

            override fun onPageScrolled(p0: Int, p1: Float, p2: Int) {}

            override fun onPageSelected(p0: Int) {
                val meme =
                    realm.where(Meme::class.java).equalTo(Meme.FILE_PATH, memesPathsArray[p0])
                        .findFirst()
                meme?.let { logMeme(it) }
            }

        })
        viewPager.currentItem = intent.getIntExtra(START_IMAGE_INDEX, 0)

        realm.where(Meme::class.java)
            .equalTo(Meme.FILE_PATH, memesPathsArray[viewPager.currentItem])
            .findFirst()?.let { logMeme(it) }


        button_share.setOnClickListener {
            shareMeme(
                File(memesPathsArray[viewPager.currentItem]),
                defaultSharedPreferences.getBoolean(
                    Prefs.Keys.ADD_WATERMARK,
                    Prefs.Defaults.ADD_WATERMARK
                )
            )
        }
        button_share.setOnLongClickListener {
            shareMeme(File(memesPathsArray[viewPager.currentItem]), false)
            true
        }
    }


    companion object {
        const val TAG = "BigImageActivity"
        const val START_IMAGE_INDEX = "param.start_image_index"
        const val IMAGES_SRC_PATH_ARRAY = "param.images_src_path_array"
    }
}
