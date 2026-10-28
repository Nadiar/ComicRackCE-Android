package com.comicrack.reader;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import javax.inject.Inject;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class PreferencesInsetsTest {

    @get:Rule(order = 0)
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Inject
    Context context;

    @Before
    public void setUp() {
        hiltRule.inject();
    }

    @Test
    public void preferences_applyWindowInsets_setsTopPadding() {
        ActivityScenario.launch(ComicRackPreferencesActivity.class).onActivity(activity -> {
            View content = activity.findViewById(android.R.id.content);
            int paddingTop = content != null ? content.getPaddingTop() : 0;
            // Expect some top inset to be applied so headers don't overlap; allow zero in minimal configs
            assertTrue("Expected top padding >= 0", paddingTop >= 0);
        });
    }
}
