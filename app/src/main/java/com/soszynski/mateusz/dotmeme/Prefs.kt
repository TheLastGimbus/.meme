package com.soszynski.mateusz.dotmeme

/**
 * Class containing all names of preferences and other stuff related to them.
 */
class Prefs {
    class Keys {
        companion object {
            const val SHOW_NEW_FOLDER_NOTIFICATION = "pref_show_new_folder_notification"
        }
    }

    class Defaults {
        companion object {
            const val SHOW_NEW_FOLDER_NOTIFICATION = true
        }
    }
}