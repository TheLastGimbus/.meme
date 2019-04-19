package com.soszynski.mateusz.dotmeme

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.AttributeSet
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
import com.google.android.material.navigation.NavigationView
import com.squareup.picasso.Picasso
import io.doorbell.android.Doorbell
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread
import java.io.File


class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    companion object {
        const val TAG = "MainActivity"
    }

    private lateinit var realm: Realm

    private lateinit var prefChangeListener: SharedPreferences.OnSharedPreferenceChangeListener

    private var searchMode = false


    private fun getMemeRoll(result: (roll: List<File>) -> Unit) {
        doAsync {
            val realmAsync = Realm.getDefaultInstance() // so we don't mess up between threads
            val images = realmAsync
                .where(MemeFolder::class.java)
                .equalTo(MemeFolder.IS_SCANNABLE, true)
                .findAll()
                .map { it.memes }
                .flatten()

            val filesMutableList = mutableListOf<File>()
            for (meme in images) {
                filesMutableList.add(File(meme.filePath))
            }
            filesMutableList.sortByDescending { it.lastModified() }

            realmAsync.close()
            uiThread {
                result(filesMutableList.toList())
            }
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

    inner class ImageAdapter(val images: List<File>) : BaseAdapter() {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val imageView: SquareImageView
            if (convertView == null) {
                imageView = SquareImageView(this@MainActivity)
                imageView.layoutParams =
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                imageView = convertView as SquareImageView
            }

            Picasso.get()
                .load(images[position])
                .fit()
                .centerCrop()
                .into(imageView)

            return imageView
        }

        override fun getItem(position: Int): Any = images[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getCount(): Int = images.count()

    }

    private fun permission() {
        if (!PainKiller().hasStoragePermission(this)) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                1
            )
        }
    }

    private fun showKeyboard() {
        editText_search.requestFocus()
        val input = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        input.showSoftInput(editText_search, InputMethodManager.SHOW_FORCED)
    }

    private fun hideKeyboard() {
        val input = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        input.hideSoftInputFromWindow(editText_search.windowToken, 0)
        editText_search.clearFocus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        nav_view.setNavigationItemSelectedListener(this)


        realm = Realm.getDefaultInstance()
        val prefs = defaultSharedPreferences

        Notifications().createChannels(this)

        permission()


        // TODO: Search FAB works as search on keyboard when keyboard is shown
        fab_search.setOnClickListener {
            showKeyboard()
        }

        fab_go_up.setOnClickListener {
            gridView_meme_roll.smoothScrollToPositionFromTop(0, 0, 350)
        }

        getMemeRoll { memeRoll ->
            progressBar_loading.visibility = ProgressBar.GONE

            gridView_meme_roll.adapter = ImageAdapter(memeRoll)
        }
        gridView_meme_roll.setOnScrollChangeListener { _, _, _, _, _ ->
            if (gridView_meme_roll.firstVisiblePosition > 15) {
                fab_go_up.show()
            } else {
                fab_go_up.hide()
            }
        }
        gridView_meme_roll.setOnItemClickListener { parent, view, position, id ->
            val intent = Intent(this, BigImageActivity::class.java).apply {
                putExtra(
                    BigImageActivity.IMAGES_SRC_PATH_ARRAY,
                    (gridView_meme_roll.adapter as ImageAdapter)
                        .images
                        .map(File::getAbsolutePath)
                        .toTypedArray()
                )
                putExtra(BigImageActivity.START_IMAGE_INDEX, position)

            }
            startActivity(intent)
        }

        button_nav_bar.setOnClickListener {
            if (searchMode) {
                onBackPressed()
            } else {
                drawer_layout.openDrawer(GravityCompat.START)
            }
        }


        editText_search.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                searchMode = true
                button_nav_bar.setImageResource(R.drawable.ic_arrow_back_white_24dp)
                progressBar_loading.visibility = ProgressBar.VISIBLE
                gridView_meme_roll.adapter = ImageAdapter(emptyList())

                val query = editText_search.text.toString()
                if (query.isNotBlank()) {

                    val config = realm.configuration // Thread migration
                    doAsync {
                        val realm = Realm.getInstance(config) // Thread migration
                        val finalList = Memebase()
                            .search(realm, query)
                            .map(Meme::filePath)
                        uiThread {
                            gridView_meme_roll.adapter = ImageAdapter(finalList.map { File(it) })
                            progressBar_loading.visibility = ProgressBar.GONE
                        }
                    }
                }
            }
            return@setOnEditorActionListener true
        }
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else if (searchMode) {
            button_nav_bar.setImageResource(R.drawable.ic_dehaze_white_24dp)
            editText_search.text.clear()
            editText_search.clearFocus()
            gridView_meme_roll.adapter = ImageAdapter(emptyList())
            getMemeRoll { memeRoll ->
                gridView_meme_roll.adapter = ImageAdapter(memeRoll)
            }
            searchMode = false
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            R.id.nav_settings -> {

            }
            R.id.nav_feedback -> {
                Doorbell(
                    this,
                    10050,
                    "Fpg9YDZGtEgYzsbCHLRgErNms46Em6XiJpl0NtscLzH246DrKRLKwurjSEdbaDLP"
                ).show()  // please don't use my private key ;)
            }
            R.id.nav_dev_stuff -> {
                val intent = Intent(this, DevStuffActivity::class.java)
                startActivity(intent)
            }
        }

        drawer_layout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
    }
}
