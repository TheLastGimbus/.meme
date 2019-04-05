package com.soszynski.mateusz.dotmeme

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.app.ActivityCompat
import android.support.v4.view.GravityCompat
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.util.AttributeSet
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import com.squareup.picasso.Picasso
import io.doorbell.android.Doorbell
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.defaultSharedPreferences
import java.io.File


class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    companion object {
        const val TAG = "MainActivity"
    }

    private lateinit var realm: Realm

    private lateinit var prefChangeListener: SharedPreferences.OnSharedPreferenceChangeListener


    fun getMemeRoll(): List<File> {
        val images = realm.where(Meme::class.java).findAll()
        val filesMutableList = mutableListOf<File>()
        for (meme in images) {
            filesMutableList.add(File(meme.filePath))
        }
        filesMutableList.sortByDescending { it.lastModified() }

        return filesMutableList.toList()
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

    inner class ImageAdapter(private val images: List<File>) : BaseAdapter() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(activity_main_toolbar)
        val toggle = ActionBarDrawerToggle(
            this,
            drawer_layout,
            activity_main_toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()
        nav_view.setNavigationItemSelectedListener(this)


        realm = Realm.getDefaultInstance()
        val prefs = defaultSharedPreferences

        Notifications().createChannels(this)

        permission()


        val memeRoll = getMemeRoll()
        gridView_meme_roll.adapter = ImageAdapter(memeRoll)
        gridView_meme_roll.setOnItemClickListener { parent, view, position, id ->

            val intent = Intent(this, BigImageActivity::class.java).apply {
                putExtra(BigImageActivity.IMAGE_SRC_PATH, memeRoll[id.toInt()].absolutePath)
            }
            startActivity(intent)
        }
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
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
