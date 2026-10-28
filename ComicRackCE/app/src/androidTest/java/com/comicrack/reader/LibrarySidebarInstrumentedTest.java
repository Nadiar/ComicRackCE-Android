package com.comicrack.reader;

import android.content.Context;
import android.preference.PreferenceManager;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import javax.inject.Inject;

/**
 * Instrumentation tests for library sidebar visibility and pinning behavior.
 * Phase 3: Hilt DI Migration
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class LibrarySidebarInstrumentedTest {
    
    @get:Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);
    
    @Inject
    Context context;
    
    @Before
    public void setUp() {
        hiltRule.inject();
    }

    @Test
    public void sidebarVisibleAndToggle() {
        // Ensure prefs request sidebar visible by default for the activity under test
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("sidebar_visible", true).putBoolean("sidebar_pinned", false).commit();

        // Launch with default prefs (sidebar visible + not pinned)
        try (ActivityScenario<LibraryActivity> scenario = ActivityScenario.launch(LibraryActivity.class)) {
            scenario.onActivity(activity -> {
                View v = activity.findViewById(fs.libraryListFragmentContainer);
                // Skip test if the current layout variant intentionally omits the sidebar
                org.junit.Assume.assumeTrue("Sidebar not present in this layout variant", v != null && v instanceof com.com.common.view.Sidebar);

                com.com.common.view.Sidebar s = (com.com.common.view.Sidebar) v;
                // Initially visible per pref
                assertTrue("Sidebar should report visible", s.a());
                // Hide and check
                s.a(false, true);
                assertFalse("Sidebar should report not visible after hide", s.a());
                // Show and check
                s.a(true, true);
                assertTrue("Sidebar should report visible after show", s.a());

                // Check pinned behavior via preferences: set pinned = true and relaunch to validate layout rule
            });
        }

        // Now test pinned preference effect
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("sidebar_pinned", true).commit();
        try (ActivityScenario<LibraryActivity> scenario = ActivityScenario.launch(LibraryActivity.class)) {
            scenario.onActivity(activity -> {
                // If sidebar isn't present in this layout variant, skip
                View v = activity.findViewById(fs.libraryListFragmentContainer);
                org.junit.Assume.assumeTrue("Sidebar not present in this layout variant", v != null && v instanceof com.com.common.view.Sidebar);
                // The activity exposes method l() to check pinned layout rules
                assertTrue("Sidebar should be pinned when preference set", activity.l());
            });
        }

        // Reset pref
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean("sidebar_pinned", false).commit();
    }
}
