/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.icons.pack

import android.graphics.BitmapShader
import android.graphics.Color
import android.graphics.Shader.TileMode.CLAMP
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import com.android.launcher3.icons.BaseIconFactory
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.BitmapRenderer
import com.android.launcher3.icons.ClockDrawableWrapper.ClockAnimationInfo
import com.android.launcher3.icons.ClockDrawableWrapper.Companion.INVALID_VALUE
import com.android.launcher3.icons.ClockDrawableWrapper.Companion.TICK_MS
import java.util.Calendar
import java.util.concurrent.TimeUnit.MINUTES

/** Adaptive icon wrapper for animated icon-pack clocks. */
internal class IconPackClockDrawable
private constructor(base: AdaptiveIconDrawable, private val animationInfo: ClockAnimationInfo) :
    AdaptiveIconDrawable(base.background, base.foreground), BitmapInfo.Extender {

    override fun getMonochrome(): Drawable? {
        val mono =
            (animationInfo.baseDrawableState.newDrawable().mutate() as? AdaptiveIconDrawable)
                ?.monochrome
        if (mono is LayerDrawable) animationInfo.applyTime(Calendar.getInstance(), mono)
        return mono
    }

    override fun getUpdatedBitmapInfo(info: BitmapInfo, factory: BaseIconFactory): BitmapInfo {
        val size = factory.iconBitmapSize
        val backgroundBitmap =
            BitmapRenderer.createHardwareBitmap(size, size) { canvas ->
                val drawable = AdaptiveIconDrawable(background.constantState!!.newDrawable(), null)
                drawable.setBounds(0, 0, size, size)
                canvas.drawColor(Color.BLACK)
                drawable.background?.draw(canvas)
            }
        return info.copy(
            delegateFactory =
                animationInfo.copy(
                    themeFgColor = Color.TRANSPARENT,
                    shader = BitmapShader(backgroundBitmap, CLAMP, CLAMP),
                )
        )
    }

    override fun drawForPersistence() {
        val layers = foreground as LayerDrawable
        for (index in
            intArrayOf(
                animationInfo.hourLayerIndex,
                animationInfo.minuteLayerIndex,
                animationInfo.secondLayerIndex,
            )) {
            if (index != INVALID_VALUE) layers.getDrawable(index).level = 0
        }
    }

    companion object {
        @JvmStatic
        fun forIcon(
            icon: Drawable,
            hourLayerIndex: Int,
            minuteLayerIndex: Int,
            secondLayerIndex: Int,
            defaultHour: Int,
            defaultMinute: Int,
            defaultSecond: Int,
        ): IconPackClockDrawable? {
            val drawable = icon.mutate() as? AdaptiveIconDrawable ?: return null
            val layers = drawable.foreground as? LayerDrawable ?: return null
            fun validIndex(index: Int) =
                if (index in 0 until layers.numberOfLayers) index else INVALID_VALUE

            var animationInfo =
                ClockAnimationInfo(
                    hourLayerIndex = validIndex(hourLayerIndex),
                    minuteLayerIndex = validIndex(minuteLayerIndex),
                    secondLayerIndex = validIndex(secondLayerIndex),
                    defaultHour = Math.floorMod(defaultHour, 12),
                    defaultMinute = Math.floorMod(defaultMinute, 60),
                    defaultSecond = Math.floorMod(defaultSecond, 60),
                    baseDrawableState = drawable.constantState ?: return null,
                )

            // Hide the second hand when the delegate only updates once per minute.
            if (TICK_MS >= MINUTES.toMillis(1) && animationInfo.secondLayerIndex != INVALID_VALUE) {
                layers.setDrawable(animationInfo.secondLayerIndex, null)
                animationInfo = animationInfo.copy(secondLayerIndex = INVALID_VALUE)
            }
            animationInfo.applyTime(Calendar.getInstance(), layers)
            return IconPackClockDrawable(drawable, animationInfo)
        }
    }
}
