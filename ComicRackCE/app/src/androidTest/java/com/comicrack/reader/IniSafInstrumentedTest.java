package com.comicrack.reader;

import android.content.Context;
import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.junit.Assert.assertTrue;

import com.comicrack.reader.DebugActionActivity;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import javax.inject.Inject;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class IniSafInstrumentedTest {
    
    @get:Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);
    
    @Inject
    Context context;
    
    @Before
    public void setUp() {
        hiltRule.inject();
    }
    
    @Test
    public void debugActivityCreatesMarkers() throws Exception {
        Intent intent = new Intent(context, DebugActionActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        // Wait briefly for activity to complete
        Thread.sleep(2000);
        File filesDir = context.getFilesDir();
        assertTrue(new File(filesDir, "TEST_direct_write_ok").exists());
        assertTrue(new File(filesDir, "TEST_debug_post_ini_ok").exists() || new File(filesDir, "TEST_debug_post_ini_empty").exists());
    }
}
