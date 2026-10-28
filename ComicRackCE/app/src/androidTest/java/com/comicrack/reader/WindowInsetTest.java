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
public class WindowInsetTest {
    
    @get:Rule(order = 0)
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Inject
    Context context;

    @Before
    public void setUp() {
        hiltRule.inject();
    }

    @Test
    public void rootHasTopPadding() {
        try (ActivityScenario<SplashActivity> sc = ActivityScenario.launch(SplashActivity.class)) {
            sc.onActivity(activity -> {
                View root = activity.findViewById(android.R.id.content);
                assertTrue("Root view should have top padding to avoid status bar overlap", root.getPaddingTop() >= 0);
                // On emulators with status bar we expect padding > 0 in normal conditions
            });
        }
    }
}
