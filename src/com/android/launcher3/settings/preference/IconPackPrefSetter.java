package com.android.launcher3.settings.preference;

import android.content.ComponentName;
import android.content.Context;


import com.android.launcher3.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Locale;

import com.android.launcher3.icons.pack.IconPackManager;
import com.android.launcher3.customization.IconDatabase;

public class IconPackPrefSetter implements ReloadingListPreference.OnReloadListener {
    private final Context mContext;
    private final ComponentName mFilter;

    public IconPackPrefSetter(Context context) {
        this(context, null);
    }

    public IconPackPrefSetter(Context context, ComponentName filter) {
        mContext = context;
        mFilter = filter;
    }

    @Override
    public Runnable listUpdater(ReloadingListPreference pref) {
        IconPackManager ipm = IconPackManager.get(mContext);
        Map<String, CharSequence> packList = ipm.getProviderNames();
        CharSequence[] keys = new String[packList.size() + (mFilter == null ? 1 : 2)];
        CharSequence[] values = new String[keys.length];
        int i = 0;

        if (mFilter != null) {
            keys[i] = mContext.getString(R.string.icon_pack_use_global);
            values[i++] = IconDatabase.VALUE_INHERIT;
        }

        // Keep inheriting the global pack distinct from explicitly using the stock icon.
        keys[i] = mContext.getResources().getString(R.string.icon_pack_default_label);
        values[i++] = IconDatabase.VALUE_DEFAULT;

        // List of available icon packs
        List<Map.Entry<String, CharSequence>> packs = new ArrayList<>(packList.entrySet());
        Collections.sort(packs, (o1, o2) ->
                normalizeTitle(o1.getValue()).compareTo(normalizeTitle(o2.getValue())));
        for (Map.Entry<String, CharSequence> entry : packs) {
            keys[i] = entry.getValue();
            values[i++] = entry.getKey();
        }

        return () -> pref.setEntriesWithValues(keys, values);
    }

    private String normalizeTitle(CharSequence title) {
        return title.toString().toLowerCase(Locale.ROOT);
    }
}
