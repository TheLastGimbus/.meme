package com.soszynski.mateusz.dotmeme

/**
 * Class containing all names of preferences and other stuff related to them.
 */
class Prefs {
    class Keys {
        companion object {
            const val SHOW_NEW_FOLDER_NOTIFICATION = "pref_show_new_folder_notification"
            const val FIRST_LAUNCH = "pref_first_app_launch"
            const val ADD_WATERMARK = "add_watermark_on_share"
        }
    }

    class Defaults {
        companion object {
            const val SHOW_NEW_FOLDER_NOTIFICATION = true
            const val ADD_WATERMARK = false
        }
    }
}