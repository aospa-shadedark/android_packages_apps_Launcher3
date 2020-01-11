package com.android.launcher3.icons.pack;

import android.graphics.drawable.Drawable;


public interface IconResolver {
    boolean isCalendar();

    /**
     * Resolves an external icon for a given density.
     * @param iconDpi Positive integer. If it is non-positive the full scale drawable is returned.
     * @param fallback Method to load the drawable when resolving using the override fails.
     * @return Loaded drawable, or fallback drawable when resolving fails.
     */
    Drawable getIcon(int iconDpi, DefaultDrawableProvider fallback);

    interface DefaultDrawableProvider {
        Drawable get();
    }
}
