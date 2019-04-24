package com.soszynski.mateusz.dotmeme

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.github.paolorotolo.appintro.AppIntro
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.toast


class IntroActivity : AppIntro() {
    private val code = 100

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            code
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!PainKiller().hasStoragePermission(this)) {
            requestPermission()
        }

        showSkipButton(false)

        addSlide(Intro1Fragment())
        addSlide(Intro2Fragment())

    }

    // empty this, as i will do it in fragments myself :)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == code) {
            if (!(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                toast(getString(R.string.toast_permission_denied))
                Handler().postDelayed({
                    requestPermission()
                }, 1000)
            }
        }
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        defaultSharedPreferences.edit()
            .putBoolean(Prefs.Keys.FIRST_LAUNCH, false)
            .apply()
        setResult(Activity.RESULT_OK, intent)
        finish()
    }
}
