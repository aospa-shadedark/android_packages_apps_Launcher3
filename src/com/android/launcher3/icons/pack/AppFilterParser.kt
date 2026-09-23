/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.launcher3.icons.pack

import android.content.ComponentName
import android.content.res.Resources
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

/**
 * Reads the component to drawable mapping an icon pack declares in its appfilter.
 *
 * The format has no specification: it is whatever the ADW and Nova launchers accepted, so entries
 * read `<item component="ComponentInfo{package/class}" drawable="name"/>` and the file ships either
 * as a compiled xml resource or as a raw asset, depending on the tool that built the pack.
 */
object AppFilterParser {
    private const val TAG = "AppFilterParser"

    private const val APP_FILTER = "appfilter"
    private const val TAG_ITEM = "item"
    private const val ATTR_COMPONENT = "component"
    private const val ATTR_DRAWABLE = "drawable"
    private const val COMPONENT_PREFIX = "ComponentInfo{"

    /**
     * Returns the drawable name the pack declares for each component it themes, or an empty map
     * when the pack ships no readable appfilter.
     */
    fun parse(resources: Resources, packageName: String): Map<ComponentName, String> =
        try {
            val id = resources.getIdentifier(APP_FILTER, "xml", packageName)
            if (id != 0) {
                resources.getXml(id).use { readItems(it) }
            } else {
                resources.assets.open("$APP_FILTER.xml").use { stream ->
                    readItems(Xml.newPullParser().apply { setInput(stream, null) })
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to read the appfilter of $packageName", e)
            emptyMap()
        }

    private fun readItems(parser: XmlPullParser): Map<ComponentName, String> {
        val drawableByComponent = mutableMapOf<ComponentName, String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG || parser.name != TAG_ITEM) {
                continue
            }
            val component = parser.getAttributeValue(null, ATTR_COMPONENT)?.toComponentName()
            val drawable = parser.getAttributeValue(null, ATTR_DRAWABLE)
            if (component != null && drawable != null) {
                // A component can be named more than once, so the first entry wins.
                drawableByComponent.putIfAbsent(component, drawable)
            }
        }
        return drawableByComponent
    }

    /** Parses the `ComponentInfo{package/class}` form the appfilter writes components in. */
    private fun String.toComponentName(): ComponentName? {
        val body = substringAfter(COMPONENT_PREFIX, "").substringBeforeLast('}', "")
        val separator = body.indexOf('/')
        if (separator <= 0 || separator == body.length - 1) {
            return null
        }
        val packageName = body.substring(0, separator)
        val className = body.substring(separator + 1)
        // A class written as ".Name" is relative to the component's own package.
        return ComponentName(
            packageName,
            if (className.startsWith(".")) packageName + className else className,
        )
    }
}
