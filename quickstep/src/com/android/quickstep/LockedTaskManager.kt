package com.android.quickstep

import android.content.Context
import android.provider.Settings

class LockedTaskManager private constructor(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        "locked_recents", Context.MODE_PRIVATE
    )

    init {
        // Retain choices from older builds, then keep launcher-owned state in launcher storage.
        if (!preferences.contains(RECENTS_LOCKED_TASKS)) {
            val previous = Settings.System.getString(context.contentResolver, RECENTS_LOCKED_TASKS)
            preferences.edit().putStringSet(RECENTS_LOCKED_TASKS,
                previous?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()).apply()
        }
    }

    fun getLockedPackages(): Set<String> =
        preferences.getStringSet(RECENTS_LOCKED_TASKS, emptySet())!!.toSet()

    fun isPackageLocked(packageName: String): Boolean = packageName in getLockedPackages()

    @Synchronized
    fun setPackageLocked(packageName: String, locked: Boolean) {
        val tasks = getLockedPackages().toMutableSet()
        if (locked) tasks.add(packageName) else tasks.remove(packageName)
        preferences.edit().putStringSet(RECENTS_LOCKED_TASKS, tasks).apply()
    }

    companion object {
        private const val RECENTS_LOCKED_TASKS = "recents_locked_tasks"

        @JvmStatic
        private var sInstance: LockedTaskManager? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): LockedTaskManager {
            if (sInstance == null) {
                sInstance = LockedTaskManager(context.applicationContext)
            }
            return sInstance!!
        }
    }
}
