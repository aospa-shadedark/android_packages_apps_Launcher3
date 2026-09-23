/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.icons.pack

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import javax.inject.Inject

/** An installed icon pack, as offered to the user. */
data class IconPackInfo(val packageName: String, val label: String)

/**
 * Tracks which icon pack the user selected, and which ones are installed to choose from.
 *
 * Icon packs are ordinary apps that declare a theme intent; there is no system registry of them, so
 * the list is resolved from the package manager each time it is asked for rather than cached. The
 * selected pack is held open, because it is read for every icon the launcher loads.
 */
@LauncherAppSingleton
class IconPackManager
@Inject
constructor(@ApplicationContext private val context: Context, private val prefs: LauncherPrefs) {
    private var pack: IconPack? = null
    private var loadedPackageName: String? = null

    /** Every installed icon pack, sorted by label. */
    fun getAvailablePacks(): List<IconPackInfo> {
        val packageManager = context.packageManager
        return THEME_ACTIONS.flatMap { action ->
                packageManager.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA)
            }
            // A pack declares several of the theme intents, so it resolves more than once.
            .distinctBy { it.activityInfo.packageName }
            .map {
                IconPackInfo(
                    it.activityInfo.packageName,
                    it.activityInfo.applicationInfo.loadLabel(packageManager).toString(),
                )
            }
            .sortedBy { it.label }
    }

    /**
     * The selected pack's package, or an empty string when apps keep their own icons.
     *
     * Setting this records the choice; the cached icons are only replaced on the next model reload.
     */
    var selectedPack: String
        get() = prefs.get(LauncherPrefs.ICON_PACK)
        set(value) {
            if (value == selectedPack) {
                return
            }
            prefs.put(LauncherPrefs.ICON_PACK, value)
            pack = null
            loadedPackageName = null
        }

    /** The selected pack, or null when none is selected or the selected one themes nothing. */
    fun getPack(): IconPack? {
        val selected = selectedPack
        if (selected.isEmpty()) {
            return null
        }
        if (loadedPackageName != selected) {
            loadedPackageName = selected
            pack = IconPack(context, selected).takeIf { it.isUsable }
        }
        return pack
    }

    companion object {
        private val THEME_ACTIONS = listOf("com.novalauncher.THEME", "org.adw.launcher.THEMES")
    }
}
