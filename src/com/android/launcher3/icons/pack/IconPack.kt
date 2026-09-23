/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.icons.pack

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageItemInfo
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log

/**
 * A single installed icon pack, and the icons it offers in place of the apps' own.
 *
 * Only the components a pack names are replaced. Packs also describe how to composite an icon for
 * the apps they do not name, which is left out on purpose: the result rarely matches their drawn
 * icons. Parsing is deferred to the first icon, as an appfilter can run to several megabytes.
 */
class IconPack(context: Context, val packageName: String) {
    private val resources: Resources? =
        try {
            context.packageManager.getResourcesForApplication(packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to load the icon pack $packageName", e)
            null
        }

    private val drawableByComponent: Map<ComponentName, String> by lazy {
        resources?.let { AppFilterParser.parse(it, packageName) } ?: emptyMap()
    }

    /** Whether this pack loaded and themes at least one component. */
    val isUsable: Boolean
        get() = resources != null && drawableByComponent.isNotEmpty()

    /**
     * Returns this pack's icon for the given component, or null when the pack does not theme it
     * and the app's own icon should be used instead.
     */
    fun getIcon(info: PackageItemInfo, density: Int): Drawable? {
        val resources = resources ?: return null
        val className = info.name ?: return null
        val component = ComponentName(info.packageName, className)
        val drawableName = drawableByComponent[component] ?: return null

        val id = resources.getIdentifier(drawableName, "drawable", packageName)
        if (id == 0) {
            return null
        }
        return try {
            resources.getDrawableForDensity(id, density, null)
        } catch (e: Resources.NotFoundException) {
            null
        }
    }

    companion object {
        const val TAG = "IconPack"
    }
}
