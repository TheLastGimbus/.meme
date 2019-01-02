package com.soszynski.mateusz.dotmeme

import android.app.Application
import io.realm.Realm

class Dotmeme : Application() {
    override fun onCreate() {
        super.onCreate()

        Realm.init(this)
    }
}