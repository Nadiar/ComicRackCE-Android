package com.comicrack.reader;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

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
 * Instrumentation tests for WifiSyncService port binding.
 * Phase 3: Hilt DI Migration
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class WifiSyncServiceBindingTest {
    
    @Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);
    
    @Inject
    Context context;
    
    @Before
    public void setUp() {
        hiltRule.inject();
    }

    @Test
    public void testEphemeralPortBinding() throws Exception {
        Intent intent = new Intent(context, WifiSyncService.class);
        intent.putExtra("WifiEnabled", true);
        intent.putExtra("CurrentLibraryPath", "/sdcard/Comics");
        // Request ephemeral port
        intent.putExtra("WifiSyncPort", 0);
        context.startService(intent);
        // wait for TEST_svc_listening_port_<port> or TEST_svc_ready
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
        assertTrue("Service should have bound to an ephemeral port and written TEST_svc_listening_port_<port>", seen);
        context.stopService(intent);
    }

    @Test
    public void testPortInUseFallback() throws Exception {
        android.content.Context context = ApplicationProvider.getApplicationContext();
        java.net.ServerSocket s = null;
        try {
            // Bind the default port to simulate conflict
            try {
                s = new java.net.ServerSocket(7614);
            } catch (java.net.BindException be) {
                // If we can't bind the port in this environment, skip the test to avoid flakiness
                org.junit.Assume.assumeTrue("Port 7614 already in use in this environment; skipping test", false);
                return;
            }

            Intent intent = new Intent(context, WifiSyncService.class);
            intent.putExtra("WifiEnabled", true);
            intent.putExtra("CurrentLibraryPath", "/sdcard/Comics");
            context.startService(intent);
            java.io.File dir = context.getFilesDir();
            long start = System.currentTimeMillis();
            boolean sawFailed = false;
            boolean sawReady = false;
            while (System.currentTimeMillis() - start < 7000) {
                java.io.File bf = new java.io.File(dir, "TEST_svc_bind_failed");
                java.io.File rf = new java.io.File(dir, "TEST_svc_ready");
                if (bf.exists()) sawFailed = true;
                if (rf.exists()) sawReady = true;
                if (sawFailed && sawReady) break;
                Thread.sleep(200);
            }
            assertTrue("Service should write TEST_svc_bind_failed when port is in use", sawFailed);
            assertTrue("Service should still reach READY via fallback", sawReady);
            context.stopService(intent);
        } finally {
            if (s != null) try { s.close(); } catch (Throwable ignore) {}
        }
    }
}
