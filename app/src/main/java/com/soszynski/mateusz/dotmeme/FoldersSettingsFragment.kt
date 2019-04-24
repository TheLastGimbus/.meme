package com.soszynski.mateusz.dotmeme

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Space
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
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
        text.setTextColor(ContextCompat.getColor(ctx, R.color.fontDefault))

        val sw = Switch(ctx)
        sw.isChecked = folder.isScannable
        sw.setOnCheckedChangeListener { _, isChecked ->
            realm.executeTransaction { realm ->
                folder.isScannable = isChecked
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
        mainView.linearLayout_folders.removeAllViews()
        val memeFolders = realm.where(MemeFolder::class.java).findAll()

        for (folder in memeFolders) {
            val linLay = getSwitchView(ctx, folder)
            linLay.setPadding(25, 25, 25, 25)
            mainView.linearLayout_folders.addView(linLay)
        }
        mainView.linearLayout_folders.setPaddingRelative(20, 20, 20, 20)
    }

    override fun onChange(t: Realm) {
        // This pretty much means that we are in Intro
        if (context != null) {
            val ctx = context!!
            if (!ctx.defaultSharedPreferences.getBoolean(Prefs.Keys.SHOW_NEW_FOLDER_NOTIFICATION, true)) {
                loadSwitches(ctx)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

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
