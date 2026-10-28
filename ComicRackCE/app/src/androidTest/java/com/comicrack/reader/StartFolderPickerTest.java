package com.comicrack.reader;

import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.intent.Intents.intended;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.espresso.intent.Intents;

import com.comicrack.reader.ComicRackApplication;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import javax.inject.Inject;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class StartFolderPickerTest {
    
    @get:Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);
    
    @Inject
    Context context;
    
    @Before
    public void setUp() {
        hiltRule.inject();
    }
    
    @Test
    public void startFolderPickerStartsOpenDocumentTree() throws Exception {
        ComicRackApplication app = (ComicRackApplication) context;

        try (ActivityScenario<SplashActivity> sc = ActivityScenario.launch(SplashActivity.class)) {
            Intents.init();
            try {
                sc.onActivity(activity -> {
                    app.startFolderPicker(activity);
                });
                intended(hasAction(Intent.ACTION_OPEN_DOCUMENT_TREE));
            } finally {
                Intents.release();
            }
        }
    }
}
