package com.soszynski.mateusz.dotmeme.activities

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.FileObserver
import android.os.Handler
import android.util.AttributeSet
import android.util.Log
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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.navigation.NavigationView
import com.soszynski.mateusz.dotmeme.*
import com.soszynski.mateusz.dotmeme.memebase.Memebase
import com.soszynski.mateusz.dotmeme.services.FullMemeSyncService
import io.doorbell.android.Doorbell
import io.realm.ObjectChangeSet
import io.realm.Realm
import io.realm.RealmObjectChangeListener
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import org.jetbrains.anko.uiThread
import java.io.File
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener,
    RealmObjectChangeListener<MemeFolder> {
    companion object {
        const val TAG = "MainActivity"
        const val INTRO_ACTIVITY_REQUEST_CODE = 15
    }

    private val memebase = Memebase()
    private lateinit var realm: Realm
    private lateinit var memeFolders: List<MemeFolder>

    private lateinit var prefChangeListener: SharedPreferences.OnSharedPreferenceChangeListener
    private var fileObservers = mutableListOf<FileObserver>()
    private var syncScheduled = false

    private var lastMemeRoll = emptyList<File>()

    private var updateVisibleMemeRollRunning = false
    private var updateVisibleMemeRollScheduled = false

    private var searchMode = false


    private fun getMemeRoll(fromCache: Boolean = false, result: (roll: List<File>) -> Unit) {
        doAsync {
            val realmAsync = Realm.getDefaultInstance() // so we don't mess up between threads
            val filesList =
                if (fromCache) memebase.getCachedMemeRoll(realmAsync)
                else memebase.getMemeRoll(realmAsync)

            realmAsync.close()
            uiThread {
                result(filesList)
            }
        }
    }

    private fun updateVisibleMemeRoll(fromCache: Boolean = false, finished: () -> Unit = {}) {
        if (updateVisibleMemeRollRunning) {
            updateVisibleMemeRollScheduled = true
            return
        }
        Log.i(TAG, "Actually running update")
        updateVisibleMemeRollRunning = true

        updateFoldersList()
        getMemeRoll(fromCache) { memeRoll ->
            if (!searchMode && memeRoll != lastMemeRoll) {
                lastMemeRoll = memeRoll
                gridView_meme_roll.adapter = ImageAdapter(memeRoll)
            }
            updateVisibleMemeRollRunning = false

            if (updateVisibleMemeRollScheduled) {
                updateVisibleMemeRollScheduled = false
                updateVisibleMemeRoll(fromCache, finished)
            } else {
                finished()
            }
        }
    }

    private fun syncAndUpdateRoll(finished: () -> Unit = {}) {
        if (!memebase.isSyncing) {
            doAsync {
                val realm = Realm.getDefaultInstance()
                memebase.syncAllFolders(realm, this@MainActivity, false)
                uiThread {
                    if (syncScheduled) {
                        syncScheduled = false
                        syncAndUpdateRoll(finished)
                    } else {
                        updateVisibleMemeRoll {
                            finished()
                        }
                    }
                }
                realm.close()
            }
        } else {
            syncScheduled = true
        }
    }

    private fun updateFoldersList() {
        val results = realm.where(MemeFolder::class.java).findAllAsync()
        results.addChangeListener { t, changeSet ->
            memeFolders = results.toList()
            addRealmFoldersChangeListeners()
            setFileObservers()

            results.removeAllChangeListeners()
        }
    }

    private fun addRealmFoldersChangeListeners() {
        for (folder in memeFolders) {
            folder.removeAllChangeListeners()
            folder.addChangeListener(this)
        }
    }

    private fun setFileObservers() {
        fileObservers.forEach { it.stopWatching() }
        fileObservers.clear()

        val folders = memeFolders.filter { it.isScannable }
        for (folder in folders) {
            fileObservers.add(
                object : FileObserver(folder.folderPath) {
                    override fun onEvent(event: Int, path: String?) {
                        val wantedEvents = intArrayOf(
                            CREATE,
                            CLOSE_WRITE,
                            DELETE,
                            DELETE_SELF,
                            MODIFY,
                            MOVED_FROM,
                            MOVED_TO,
                            MOVE_SELF
                        )
                        if (wantedEvents.contains(event)) {
                            syncAndUpdateRoll()
                        }
                    }
                }.apply { startWatching() }
            )
        }
    }

    inner class SquareImageView : ImageView {

        constructor(context: Context) : super(context)

        constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

        constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
            context,
            attrs,
            defStyle
        )

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
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                imageView = convertView as SquareImageView
            }

            Glide.with(this@MainActivity)
                .load(images[position])
                .error(R.drawable.ic_error_outline_gray_24dp)
                .transition(DrawableTransitionOptions.withCrossFade())
                .diskCacheStrategy(DiskCacheStrategy.NONE)
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

    override fun onChange(t: MemeFolder, changeSet: ObjectChangeSet?) {
        if (changeSet != null && changeSet.isFieldChanged(MemeFolder.IS_SCANNABLE)) {
            Log.i(TAG, "Sync and update im MainActivity due to change in folder settings")
            syncAndUpdateRoll()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        nav_view.setNavigationItemSelectedListener(this)


        Memebase.handleRealmConfigs()
        realm = Realm.getDefaultInstance()
        val prefs = defaultSharedPreferences
        if (prefs.getBoolean(Prefs.Keys.FIRST_LAUNCH, true)) {
            startActivityForResult(
                Intent(this, IntroActivity::class.java),
                INTRO_ACTIVITY_REQUEST_CODE
            )
        } else {
            permission()
        }

        Notifs.createChannels(this)


        fab_search.setOnClickListener {
            showKeyboard()
        }

        fab_go_up.setOnClickListener {
            gridView_meme_roll.smoothScrollToPositionFromTop(0, 0, 350)
        }

        // get current index
        progressBar_loading.visibility = ProgressBar.VISIBLE
        updateVisibleMemeRoll(true) {
            progressBar_loading.visibility = ProgressBar.GONE
        }

        // Quickly update index for sure. This is not visible if there was no change.
        syncAndUpdateRoll {
            // And after this quick refresh, start foreground service
            // to get all new memes scanned quickly
            FullMemeSyncService.start(this)
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            gridView_meme_roll.setOnScrollChangeListener { _, _, _, _, _ ->
                if (gridView_meme_roll.firstVisiblePosition > 15) {
                    fab_go_up.show()
                } else {
                    fab_go_up.hide()
                }
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

                val query = editText_search.text.toString()
                if (query.isNotBlank()) {
                    searchMode = true
                    button_nav_bar.setImageResource(R.drawable.ic_arrow_back_white_24dp)
                    progressBar_loading.visibility = ProgressBar.VISIBLE
                    gridView_meme_roll.adapter = ImageAdapter(emptyList())


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


        if (!FullSyncWorker.isScheduled()) {
            val repRequest = PeriodicWorkRequestBuilder<FullSyncWorker>(
                15,
                TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance().enqueueUniquePeriodicWork(
                FullSyncWorker.UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                repRequest
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Thanks to improvement with lastSync solution, we can do this super-often,
        // because it pretty much won't do anything if there was no change in folder.
        syncAndUpdateRoll()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == INTRO_ACTIVITY_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                FullMemeSyncService.start(this)
                progressBar_loading.visibility = ProgressBar.VISIBLE
                syncAndUpdateRoll {
                    progressBar_loading.visibility = ProgressBar.GONE
                }
            }
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == 1) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_DENIED)) {
                toast("App won't work without this :(")
                Handler().postDelayed({
                    permission()
                }, 1000)
            }
        }
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else if (searchMode) {
            button_nav_bar.setImageResource(R.drawable.ic_dehaze_white_24dp)
            editText_search.text.clear()
            editText_search.clearFocus()
            gridView_meme_roll.adapter = ImageAdapter(lastMemeRoll)
            searchMode = false
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            R.id.nav_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            R.id.nav_feedback -> {
                val door = Doorbell(
                    this,
                    10050,
                    "Fpg9YDZGtEgYzsbCHLRgErNms46Em6XiJpl0NtscLzH246DrKRLKwurjSEdbaDLP"
                ) // please don't use my private key ;)
                door.messageField.setHint(R.string.doorbell_field_hint_message)
                door.emailField.setHint(R.string.doorbell_field_hint_email)
                door.show()
            }
            R.id.nav_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
            }
            R.id.nav_force_sync -> {
                FullMemeSyncService.start(this)
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
