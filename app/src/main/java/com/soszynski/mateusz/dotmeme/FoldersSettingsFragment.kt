package com.soszynski.mateusz.dotmeme

import android.os.Bundle
import android.support.v4.app.Fragment
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
        val TAG = "FoldersSettingsFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_folders_settings, container, false)
        val realm = Realm.getDefaultInstance()
        val memeFolders = realm.where(MemeFolder::class.java).findAll()

        if (context != null) {
            for (folder in memeFolders) {
                val text = TextView(context)
                text.text = File(folder.folderPath).name

                val sw = Switch(context)
                sw.isChecked = folder.isScanable
                sw.setOnCheckedChangeListener { buttonView, isChecked ->
                    realm.executeTransaction { realm ->
                        folder.isScanable = isChecked
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
                view.linearLayout_folders.addView(linLay)
            }
        }

        return view
    }

}
