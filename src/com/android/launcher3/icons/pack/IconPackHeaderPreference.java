/*
 * Copyright (C) 2020 Shift GmbH
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

package com.android.launcher3.icons.pack;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;

import com.android.launcher3.LauncherAppState;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.THREAD_POOL_EXECUTOR;
import android.view.View;
import android.widget.ImageView;
import android.util.AttributeSet;

import androidx.core.content.res.TypedArrayUtils;
import androidx.preference.PreferenceViewHolder;

import com.android.launcher3.R;
import com.android.launcher3.icons.IconProvider;


public class IconPackHeaderPreference extends RadioHeaderPreference {
    private static final int PREVIEW_ICON_NUM = 8;
    // This value has been selected as an average of usual "device profile-computed" values
    private static final int PREVIEW_ICON_DPI = 500;

    private final Context context;
    private ImageView[] icons = null;
    private int mPreviewGeneration;

    public IconPackHeaderPreference(Context context) {
        this(context, null);
    }

    public IconPackHeaderPreference(Context context, AttributeSet attrs) {
        this(context, attrs, TypedArrayUtils.getAttr(context,
                androidx.preference.R.attr.preferenceStyle,
                android.R.attr.preferenceStyle));
    }

    public IconPackHeaderPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.context = context;

        setLayoutResource(R.layout.preference_widget_icons_preview);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        final ImageView[] imageViews = {
            (ImageView) holder.findViewById(R.id.pref_icon_a),
            (ImageView) holder.findViewById(R.id.pref_icon_b),
            (ImageView) holder.findViewById(R.id.pref_icon_c),
            (ImageView) holder.findViewById(R.id.pref_icon_d),
            (ImageView) holder.findViewById(R.id.pref_icon_e),
            (ImageView) holder.findViewById(R.id.pref_icon_f),
            (ImageView) holder.findViewById(R.id.pref_icon_g),
            (ImageView) holder.findViewById(R.id.pref_icon_h)
        };
        this.icons = imageViews;
        onRadioElementSelected(null);
    }

    @Override
    public void onDetached() {
        this.icons = null;
        super.onDetached();
    }

    @Override
    public void onRadioElementSelected(String key) {
        if (icons == null || icons.length == 0) {
            return;
        }

        final ImageView[] previewViews = icons;
        final int generation = ++mPreviewGeneration;
        final IconProvider iconProvider = LauncherAppState.getInstance(context).getIconProvider();
        final PackageManager pm = context.getPackageManager();
        THREAD_POOL_EXECUTOR.execute(() -> {
            List<ResolveInfo> apps = pm.queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
            apps.sort(new ResolveInfo.DisplayNameComparator(pm));
            List<Drawable> previews = new ArrayList<>();
            for (int i = 0; i < Math.min(PREVIEW_ICON_NUM, apps.size()); i++) {
                previews.add(iconProvider.getIcon(apps.get(i).activityInfo, PREVIEW_ICON_DPI));
            }
            MAIN_EXECUTOR.execute(() -> {
                if (icons != previewViews || generation != mPreviewGeneration) return;
                for (int i = 0; i < previewViews.length; i++) {
                    previewViews[i].setImageDrawable(i < previews.size() ? previews.get(i) : null);
                }
            });
        });
    }
}
