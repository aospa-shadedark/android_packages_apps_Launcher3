package com.android.launcher3.icons.pack;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;

import java.util.Calendar;

public class IconResolverExternal implements IconResolver {
    private final PackageManager mPm;
    private final ApplicationInfo mPackInfo;
    private final int mDrawableId;
    private final String mCalendarPrefix;
    private final IconPack.Clock mClockData;

    IconResolverExternal(PackageManager pm, ApplicationInfo packInfo, int drawableId,
                         String calendarPrefix, IconPack.Clock clockData) {
        mPm = pm;
        mPackInfo = packInfo;
        mDrawableId = drawableId;
        mCalendarPrefix = calendarPrefix;
        mClockData = clockData;
    }

    public boolean isCalendar() {
        return mCalendarPrefix != null;
    }

    public Drawable getIcon(int iconDpi, DefaultDrawableProvider fallback) {
        try {
            Resources res = mPm.getResourcesForApplication(mPackInfo);
            int id = mDrawableId;
            if (isCalendar()) {
                String name = mCalendarPrefix + Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
                int today = res.getIdentifier(name, "drawable", mPackInfo.packageName);
                if (today == 0) today = res.getIdentifier(name, "mipmap", mPackInfo.packageName);
                if (today != 0) id = today;
            }
            Drawable icon = res.getDrawableForDensity(id, iconDpi, null);
            if (icon != null && mClockData != null) {
                Drawable clock = IconPackClockDrawable.forIcon(icon,
                        mClockData.hourLayerIndex, mClockData.minuteLayerIndex,
                        mClockData.secondLayerIndex, mClockData.defaultHour,
                        mClockData.defaultMinute, mClockData.defaultSecond);
                if (clock != null) return clock;
            }
            return icon;
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            // The pack may have been removed or updated while this resolver was in flight.
            return null;
        }
    }
}
