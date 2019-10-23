package com.soszynski.mateusz.dotmeme

import android.util.Log
import androidx.room.migration.Migration
import io.realm.DynamicRealm
import io.realm.RealmMigration


class RollMigration : RealmMigration {
    override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {
        Log.i(TAG, "Roll migration running, old version: $oldVersion, new version: $newVersion")
        val schema = realm.schema

        if (oldVersion.toInt() == 0) {
            val objSchema = schema.createWithPrimaryKeyField(
                "MemeRoll",
                MemeRoll.ID,
                Integer::class.java
            )
            objSchema.setRequired(MemeRoll.ID, true)
            objSchema.addRealmListField(MemeRoll.ROLL, String::class.java)
        }
    }

    override fun hashCode(): Int {
        return 37 // don't know why 37, it's from stackoverflow
    }

    override fun equals(other: Any?): Boolean {
        return other is Migration
    }

    companion object {
        const val TAG = "Realm RollMigration"
    }

}