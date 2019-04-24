package com.soszynski.mateusz.dotmeme


import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.realm.Realm
import kotlinx.android.synthetic.main.fragment_intro1.*
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread

class Intro1Fragment : Fragment() {

    private fun doSync(ctx: Context) {
        val realm = Realm.getDefaultInstance()
        val prefs = ctx.defaultSharedPreferences

        // Mute notifications in the first scan
        prefs.edit()
            .putBoolean(Prefs.Keys.SHOW_NEW_FOLDER_NOTIFICATION, false)
            .apply()

        Memebase().syncAllFolders(realm, ctx) {
            prefs.edit()
                .putBoolean(Prefs.Keys.SHOW_NEW_FOLDER_NOTIFICATION, true)
                .apply()

            progressBar.visibility = View.INVISIBLE
            imageView_check.visibility = View.VISIBLE
            textView_done.visibility = View.VISIBLE

            realm.close()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        doAsync {
            while (true) {
                if (activity != null) {
                    val ctx = activity!!.applicationContext
                    if (PainKiller().hasStoragePermission(ctx)) {
                        uiThread {
                            doSync(ctx)
                        }
                        break
                    }
                }
                Thread.sleep(50)
            }
        }

        return inflater.inflate(R.layout.fragment_intro1, container, false)
    }
}
