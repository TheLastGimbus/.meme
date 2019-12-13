package com.soszynski.mateusz.dotmeme.fragments

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Space
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.soszynski.mateusz.dotmeme.*
import com.soszynski.mateusz.dotmeme.memebase.Memebase
import io.realm.Realm
import io.realm.RealmChangeListener
import kotlinx.android.synthetic.main.fragment_folders_settings.view.*
import org.jetbrains.anko.defaultSharedPreferences
import java.io.File

class FoldersSettingsFragment : Fragment(), RealmChangeListener<Realm> {

    companion object {
        const val TAG = "FoldersSettingsFragment"
    }

    lateinit var realm: Realm
    lateinit var mainView: View

    private fun getSwitchView(ctx: Context, folder: MemeFolder): LinearLayout {
        val text = TextView(ctx)
        text.text = File(folder.folderPath).name
        text.textSize = 16f
        text.setTextColor(
            ContextCompat.getColor(
                ctx,
                R.color.fontDefault
            )
        )
        text.isSingleLine = true
        text.ellipsize = TextUtils.TruncateAt.MIDDLE
        // TODO: Fix long text covering switch

        val sw = Switch(ctx)
        sw.isChecked = folder.isScannable
        sw.setOnCheckedChangeListener { _, isChecked ->
            // This dialog will be displayed when user needs to wait for
            // switching folder on/off taking SO DAMN LONG
            val dialogWaitBuilder = AlertDialog.Builder(ctx)
                .setTitle(ctx.getString(R.string.wait))
                .setMessage(ctx.getString(R.string.this_wont_take_long))
                .setCancelable(false)
            var dialogWait: AlertDialog?

            if (isChecked) {
                dialogWait = dialogWaitBuilder.show()

                // I don't know why this ONE SIMPLE OPERATION
                // LITERALLY SWITCHING 1 to 0
                // needs to be async
                val folderPath = folder.folderPath
                realm.executeTransactionAsync(
                    { realm ->
                        val folder = realm.where(MemeFolder::class.java)
                            .equalTo(MemeFolder.FOLDER_PATH, folderPath)
                            .findFirst()!!
                        folder.isScannable = isChecked
                    }, { dialogWait?.dismiss() }, { it.printStackTrace() }
                )

            } else {
                val scannedCount = folder.memes.where()
                    .equalTo(Meme.IS_SCANNED, true)
                    .count()

                if (scannedCount > FullSyncWorker.FOREGROUND_SCAN_PENDING_TRESHHOLD) {
                    AlertDialog.Builder(ctx)
                        .setTitle(getString(R.string.settings_folders_fragment_are_you_sure_title))
                        .setMessage(
                            getString(R.string.settings_folders_fragment_are_you_sure_description)
                                .replace(
                                    "[count]",
                                    scannedCount.toString()
                                )
                        )
                        .setPositiveButton(
                            getString(R.string.settings_folders_fragment_are_you_sure_response_yes)
                        ) { dialog, which ->

                            dialogWait = dialogWaitBuilder.show()

                            // I don't know why this ONE SIMPLE OPERATION
                            // LITERALLY SWITCHING 1 to 0
                            // needs to be async
                            val folderPath = folder.folderPath
                            realm.executeTransactionAsync(
                                { realm ->
                                    val folder = realm.where(MemeFolder::class.java)
                                        .equalTo(MemeFolder.FOLDER_PATH, folderPath)
                                        .findFirst()!!
                                    folder.isScannable = isChecked
                                }, { dialogWait?.dismiss() }, { it.printStackTrace() }
                            )
                        }
                        .setNegativeButton(
                            getString(R.string.settings_folders_fragment_are_you_sure_response_no)
                        ) { _, _ ->
                            sw.isChecked = true
                        }
                        .setCancelable(false)
                        .show()
                } else {
                    dialogWait = dialogWaitBuilder.show()

                    // I don't know why this ONE SIMPLE OPERATION
                    // LITERALLY SWITCHING 1 to 0
                    // needs to be async
                    val folderPath = folder.folderPath
                    realm.executeTransactionAsync(
                        { realm ->
                            val folder = realm.where(MemeFolder::class.java)
                                .equalTo(MemeFolder.FOLDER_PATH, folderPath)
                                .findFirst()!!
                            folder.isScannable = isChecked
                        }, { dialogWait?.dismiss() }, { it.printStackTrace() }
                    )
                }
            }
        }

        val space = Space(ctx)
        space.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT,
            1f
        )

        val linLay = LinearLayout(ctx)
        linLay.addView(text)
        linLay.addView(space)
        linLay.addView(sw)

        return linLay
    }

    private fun loadSwitches(ctx: Context) {
        mainView.linearLayout_folders_official.removeAllViews()
        mainView.linearLayout_folders_unofficial.removeAllViews()
        val memeFolders = realm.where(MemeFolder::class.java).findAll()

        for (folder in memeFolders) {
            val linLay = getSwitchView(ctx, folder)
            linLay.setPadding(0, 25, 0, 25)
            if (folder.isOfficial) {
                mainView.linearLayout_folders_official.addView(linLay)
            } else {
                mainView.linearLayout_folders_unofficial.addView(linLay)
            }

        }
    }

    override fun onChange(t: Realm) {
        // This pretty much means that we are in Intro
        if (context != null) {
            val ctx = context!!
            if (!ctx.defaultSharedPreferences.getBoolean(
                    Prefs.Keys.SHOW_NEW_FOLDER_NOTIFICATION,
                    true
                )
            ) {
                loadSwitches(ctx)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Memebase.handleRealmConfigs()
        realm = Realm.getDefaultInstance()
        realm.addChangeListener(this)

        mainView = inflater.inflate(R.layout.fragment_folders_settings, container, false)
        if (context != null) {
            loadSwitches(context!!)
        }
        return mainView
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
    }

}
