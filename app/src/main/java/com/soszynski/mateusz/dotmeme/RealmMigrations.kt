package com.soszynski.mateusz.dotmeme

import android.util.Log
import io.realm.DynamicRealm
import io.realm.RealmMigration

class UniversalMigration : RealmMigration {
    companion object {
        const val TAG = "UniversalMigration"
    }

    override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {
        Log.i(
            TAG,
            "Universal migration running, " +
                    "old version: $oldVersion, new version: $newVersion"
        )
        val schema = realm.schema

        if (oldVersion < 1) {
            val objSchema = schema.createWithPrimaryKeyField(
                "MemeRoll",
                MemeRoll.ID,
                Integer::class.java
            )
            objSchema.setRequired(MemeRoll.ID, true)
            objSchema.addRealmListField(MemeRoll.ROLL, String::class.java)
        }
        if (oldVersion < 2) {
            val folderSchema = schema.get("MemeFolder")!!
            folderSchema.addField(MemeFolder.IS_OFFICIAL, Boolean::class.java)
        }
    }
}
