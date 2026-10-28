package com.comicrack.reader;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.comicrack.reader.ComicRackApplication;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import javax.inject.Inject;

/**
 * Instrumentation tests for library prompt and availability checking.
 * Phase 3: Hilt DI Migration
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class LibraryPromptTest {
    
    @get:Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);
    
    @Inject
    Context context;
    
    @Before
    public void setUp() {
        hiltRule.inject();
        android.preference.PreferenceManager.getDefaultSharedPreferences(context).edit().putString("current_library_path",
                "content://com.android.externalstorage.documents/tree/primary%3ANonexistent").commit();
    }

    @Test
    public void checkLibraryAvailabilityReturnsNeedGrant() throws Exception {
        ComicRackApplication app = (ComicRackApplication) context;
        ComicRackApplication.LibraryStatus status = app.checkLibraryAvailability(context);
        assertEquals(ComicRackApplication.LibraryStatus.NEED_GRANT, status);
    }
}
