package com.soszynski.mateusz.dotmeme

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import com.squareup.picasso.Picasso
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_search.*
import java.io.File


class SearchActivity : AppCompatActivity() {
    lateinit var realm: Realm
    private var memes: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        realm = Realm.getDefaultInstance()

        button_enter.setOnClickListener {
            val query = editText_search.text
            if (query.isNotEmpty()) {
                val finalList = Memebase()
                    .search(realm, query.toString())
                    .map(Meme::filePath)

                memes = finalList
                gridView_results.adapter = ImageAdapter()
            }
        }

        gridView_results.setOnItemClickListener { parent, view, position, id ->
            val intent = Intent(this, BigImageActivity::class.java).apply {
                putExtra(BigImageActivity.IMAGE_SRC_PATH, memes[id.toInt()])
            }
            startActivity(intent)
        }
    }

    inner class ImageAdapter : BaseAdapter() {
        override fun getCount(): Int = memes.size

        override fun getItem(position: Int): Any = memes[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val imageView: SquareImageView
            if (convertView == null) {
                // if it's not recycled, initialize some attributes
                imageView = SquareImageView(this@SearchActivity)
                imageView.layoutParams =
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                imageView = convertView as SquareImageView
            }

            Picasso.get()
                .load(File(memes[position]))
                .fit()
                .centerCrop()
                .into(imageView)

            return imageView
        }
    }

    inner class SquareImageView : ImageView {

        constructor(context: Context) : super(context)

        constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

        constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle)

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)

            val width = measuredWidth
            setMeasuredDimension(width, width)
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
    }

    companion object {
        const val TAG = "SearchActivity"
    }
}
