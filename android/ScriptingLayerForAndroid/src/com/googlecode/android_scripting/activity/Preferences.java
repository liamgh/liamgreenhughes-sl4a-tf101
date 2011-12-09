/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.googlecode.android_scripting.activity;

import android.app.ActionBar;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.view.MenuItem;

import com.googlecode.android_scripting.R;
import com.googlecode.android_scripting.Version;

import java.util.List;

public class Preferences extends PreferenceActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ActionBar actionBar = getActionBar();
    actionBar.setTitle("Preferences");
    actionBar.setSubtitle("Scripting Layer for Android r" + Version.getVersion(this));
    actionBar.setDisplayHomeAsUpEnabled(true);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int itemId = item.getItemId();

    // action bar back
    if (itemId == android.R.id.home) {
      finish();
    }
    return true;
  }

  /**
   * Populate the activity with the top-level headers.
   */
  @Override
  public void onBuildHeaders(List<Header> target) {
    loadHeadersFromResource(R.xml.preference_headers, target);
  }

  public static class PreferencesGeneral extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      // Load the preferences from an XML resource
      addPreferencesFromResource(R.xml.preferences_general);
      // Analytics.trackActivity(this);
    }
  }

  public static class PreferencesTerminal extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      // Load the preferences from an XML resource
      addPreferencesFromResource(R.xml.preferences_terminal);
      // Analytics.trackActivity(this);
    }
  }
}
