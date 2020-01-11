package com.android.launcher3.customization;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.UserHandle;

import java.util.Map;
import java.util.TreeMap;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.R;
import com.android.launcher3.util.ComponentKey;

public class IconDatabase {

    private static final String PREF_FILE_NAME = BuildConfig.APPLICATION_ID + ".ICON_DATABASE";
    public static final String KEY_ICON_PACK = "pref_icon_pack";
    public static final String VALUE_DEFAULT = "";
    // Only used by the per-app preference UI; never stored as a package name.
    public static final String VALUE_INHERIT = "@global";
    private static final String DRAWABLE_SEPARATOR = "::";

    public static String getGlobal(Context context) {
        return LauncherPrefs.getPrefs(context).getString(KEY_ICON_PACK, VALUE_DEFAULT);
    }

    public static String getGlobalLabel(Context context) {
        if (context == null) return "Default";
        final String defaultLabel = context.getString(R.string.icon_pack_default_label);
        final String pkgName = getGlobal(context);
        if (VALUE_DEFAULT.equals(pkgName)) {
            return defaultLabel;
        }

        final PackageManager pm = context.getPackageManager();
        try {
            final ApplicationInfo ai = pm.getApplicationInfo(pkgName, 0);
            return pm.getApplicationLabel(ai).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return defaultLabel;
        }
    }

    public static void setGlobal(Context context, String value) {
        LauncherPrefs.getPrefs(context).edit().putString(KEY_ICON_PACK, value).apply();
    }

    public static void resetGlobal(Context context) {
        LauncherPrefs.getPrefs(context).edit().remove(KEY_ICON_PACK).apply();
    }

    public static String getByComponent(Context context, ComponentKey key) {
        String raw = getIconPackPrefs(context).getString(key.toString(), getGlobal(context));
        int idx = raw.indexOf(DRAWABLE_SEPARATOR);
        return idx == -1 ? raw : raw.substring(0, idx);
    }

    public static void setForComponent(Context context, ComponentKey key, String value) {
        getIconPackPrefs(context).edit().putString(key.toString(), value).apply();
    }

    public static boolean hasOverrideForComponent(Context context, ComponentKey key) {
        return getIconPackPrefs(context).contains(key.toString());
    }

    /** Includes explicit drawable choices so persisted icon caches detect per-app changes. */
    public static Map<String, String> getOverridesForPackage(
            Context context, String packageName, UserHandle user) {
        Map<String, String> result = new TreeMap<>();
        String prefix = packageName + "/";
        String suffix = "#" + user.hashCode();
        getIconPackPrefs(context).getAll().forEach((key, value) -> {
            if (key.startsWith(prefix) && key.endsWith(suffix) && value instanceof String) {
                result.put(key, (String) value);
            }
        });
        return result;
    }

    public static String getPackPackage(String value) {
        int index = value.indexOf(DRAWABLE_SEPARATOR);
        return index < 0 ? value : value.substring(0, index);
    }
    
    public static void setExplicitIconForComponent(
            Context context, ComponentKey key, String packPackage, String drawableName) {
        getIconPackPrefs(context).edit()
                .putString(key.toString(), packPackage + DRAWABLE_SEPARATOR + drawableName)
                .apply();
    }

    public static String getExplicitDrawableForComponent(Context context, ComponentKey key) {
        String raw = getIconPackPrefs(context).getString(key.toString(), null);
        if (raw == null) return null;
        int idx = raw.indexOf(DRAWABLE_SEPARATOR);
        return idx == -1 ? null : raw.substring(idx + DRAWABLE_SEPARATOR.length());
    }

    public static void resetForComponent(Context context, ComponentKey key) {
        getIconPackPrefs(context).edit().remove(key.toString()).apply();
    }

    private static SharedPreferences getIconPackPrefs(Context context) {
        return context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE);
    }
}
