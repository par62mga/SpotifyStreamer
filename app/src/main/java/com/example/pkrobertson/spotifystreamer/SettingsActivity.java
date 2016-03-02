/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.example.pkrobertson.spotifystreamer;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.view.Menu;

import java.util.Locale;

/**
 * SettingActivity -- used to handle settings for country code and notifications
 *
 * TODO: Since my theme is based on Theme.AppCompat, the preference screen does not get an action
 *      bar. Could implement this as a PreferenceFragment to address this issue
 */
public class SettingsActivity extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener {

    /**
     * Attaches a listener so the summary is always updated with the preference value.
     * Also fires the listener once, to initialize the summary (so it shows up before the value
     * is changed.)
     */
    private void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(this);

        // Trigger the listener immediately with the preference's
        // current value.
        onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Add 'general' preferences, defined in the XML file
        addPreferencesFromResource(R.xml.pref_general);

        // For all preferences, attach an OnPreferenceChangeListener so the UI summary can be
        // updated when the preference changes.
        bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_country_key)));
        // bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_notification_key)));
    }

    // This handles making sure the selected country appears in the preference summary
    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        String stringValue = value.toString();

        // validate user entry against ISO country codes
        String[] countryCodes = Locale.getISOCountries();
        for (String countryCode : countryCodes) {
            if ( stringValue.compareTo(countryCode) == 0 ) {
                if (preference instanceof EditTextPreference) {
                    preference.setSummary(stringValue);
                }
                return true;
            }
        }

        // country code was not found in the list, alert user
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle (R.string.pref_country_label);
        builder.setMessage(R.string.error_country_code);
        builder.setPositiveButton (android.R.string.ok, null);
        builder.show ();
        return false;
    }

    // this code is from Sunshine to address an issue in Jelly Bean
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public Intent getParentActivityIntent() {
        return super.getParentActivityIntent().addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

}