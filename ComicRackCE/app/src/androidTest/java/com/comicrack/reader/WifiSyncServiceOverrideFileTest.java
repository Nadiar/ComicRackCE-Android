package com.comicrack.reader;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.comicrack.reader.WifiSyncService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import javax.inject.Inject;

/**
 * Instrumentation tests for WifiSyncService override file handling.
 * Phase 3: Hilt DI Migration
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class WifiSyncServiceOverrideFileTest {
    
    @get:Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);
    
    @Inject
    Context context;
    
    @Before
    public void setUp() {
        hiltRule.inject();
    }

    @Test
    public void testOverrideFileSetsPort() throws Exception {
        java.io.File override = new java.io.File(context.getFilesDir(), "WIFI_SYNC_PORT_OVERRIDE");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(override)) {
            fos.write("0".getBytes());
        }
        Intent intent = new Intent(context, WifiSyncService.class);
        intent.putExtra("WifiEnabled", true);
        intent.putExtra("CurrentLibraryPath", "/sdcard/Comics");
        context.startService(intent);

        // wait for TEST_svc_listening_port_<port>
        java.io.File dir = context.getFilesDir();
        long start = System.currentTimeMillis();
        boolean seen = false;
        while (System.currentTimeMillis() - start < 5000) {
            java.io.File[] files = dir.listFiles();
            if (files != null) {
                for (java.io.File f : files) {
                    if (f.getName().startsWith("TEST_svc_listening_port_")) {
                        seen = true;
                        break;
                    }
                }
            }
            if (seen) break;
            Thread.sleep(200);
        }
        assertTrue("Service should have bound to port specified by override file", seen);
        context.stopService(intent);
        // cleanup
        override.delete();
    }
}
