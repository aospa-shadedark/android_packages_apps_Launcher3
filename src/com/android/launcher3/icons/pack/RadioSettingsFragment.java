/*
 * Copyright (C) 2020 Shift GmbH
 * Copyright (C) 2026 crDroid Android Project
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
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.android.settingslib.widget.SelectorWithWidgetPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import java.util.List;

public abstract class RadioSettingsFragment extends SettingsBasePreferenceFragment implements
        Preference.OnPreferenceClickListener {
    private SelectorWithWidgetPreference selectedPreference = null;
    private RadioHeaderPreference headerPref = null;

    protected abstract List<SelectorWithWidgetPreference> getPreferences(Context context);

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final PreferenceManager prefManager = getPreferenceManager();
        final Context context = prefManager.getContext();
        final PreferenceScreen screen = prefManager.createPreferenceScreen(context);

        headerPref = getHeader(context);
        if (headerPref != null) {
            screen.addPreference(headerPref);
        }

        loadPreferences(context, screen);
        setPreferenceScreen(screen);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference instanceof SelectorWithWidgetPreference) {
            if (preference == selectedPreference && selectedPreference.isChecked()) {
                return true;
            }

            if (selectedPreference != null) {
                selectedPreference.setChecked(false);
            }
            selectedPreference = (SelectorWithWidgetPreference) preference;
            selectedPreference.setChecked(true);
            onSelected(preference.getKey());
            return true;
        } else {
            return false;
        }
    }

    protected RadioHeaderPreference getHeader(Context context) {
        return null;
    }

    protected void onSelected(String key) {
        if (headerPref != null) {
            headerPref.onRadioElementSelected(key);
        }
    }

    protected void reloadPreferences() {
        final PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            return;
        }

        // Remove radio preferences (backwards so we don't mess up indices)
        final int numPreferences = screen.getPreferenceCount();
        for (int i = numPreferences - 1; i >= 0; i--) {
            final Preference p = screen.getPreference(i);
            if (p instanceof SelectorWithWidgetPreference) {
                screen.removePreference(p);
            }
        }

        // Add radio preferences
        final PreferenceManager prefManager = getPreferenceManager();
        final Context context = prefManager.getContext();
        loadPreferences(context, screen);
    }

    private void loadPreferences(Context context, PreferenceScreen screen) {
        selectedPreference = null;
        final List<SelectorWithWidgetPreference> prefs = getPreferences(context);
        for (final SelectorWithWidgetPreference p : prefs) {
            // The persisted selection supplied by getPreferences is the source of truth.
            if (p.isChecked()) {
                if (selectedPreference == null) {
                    selectedPreference = p;
                } else {
                    p.setChecked(false);
                }
            }
            p.setOnPreferenceClickListener(this);
            screen.addPreference(p);
        }

        if (selectedPreference == null && !prefs.isEmpty()) {
            // The selected pack was removed. Persist the default selection as well as its UI.
            selectedPreference = prefs.get(0);
            selectedPreference.setChecked(true);
            onSelected(selectedPreference.getKey());
        } else if (headerPref != null && selectedPreference != null) {
            headerPref.onRadioElementSelected(selectedPreference.getKey());
        }
    }
}
