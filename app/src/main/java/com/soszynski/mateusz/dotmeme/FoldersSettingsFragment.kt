package com.soszynski.mateusz.dotmeme

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Space
import android.widget.Switch
import android.widget.TextView
import io.realm.Realm
import kotlinx.android.synthetic.main.fragment_folders_settings.view.*
import java.io.File

class FoldersSettingsFragment : Fragment() {
    companion object {
        const val TAG = "FoldersSettingsFragment"
    }

    lateinit var realm: Realm

    private fun getSwitchView(folder: MemeFolder): LinearLayout {
        val text = TextView(context)
        text.text = File(folder.folderPath).name
        text.textSize = 16f
        text.setTextColor(ContextCompat.getColor(context!!, R.color.fontDefault))

        val sw = Switch(context)
        sw.isChecked = folder.isScannable
        sw.setOnCheckedChangeListener { _, isChecked ->
            realm.executeTransaction { realm ->
                folder.isScannable = isChecked
            }
        }

        val space = Space(context)
        space.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT,
            1f
        )

        val linLay = LinearLayout(context)
        linLay.addView(text)
        linLay.addView(space)
        linLay.addView(sw)

        return linLay
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        realm = Realm.getDefaultInstance()

        val view = inflater.inflate(R.layout.fragment_folders_settings, container, false)

        val memeFolders = realm.where(MemeFolder::class.java).findAll()

        for (folder in memeFolders) {
            val linLay = getSwitchView(folder)
            linLay.setPadding(25, 25, 25, 25)
            view.linearLayout_folders.addView(linLay)
        }
        view.linearLayout_folders.setPaddingRelative(20, 20, 20, 20)

        return view
    }

}
