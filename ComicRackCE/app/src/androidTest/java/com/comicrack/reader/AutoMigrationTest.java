package com.comicrack.reader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.preference.PreferenceManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.comicrack.reader.ComicRackApplication;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileWriter;
import java.util.Properties;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import javax.inject.Inject;

/**
 * Instrumentation tests for legacy path auto-migration.
 * Phase 3: Hilt DI Migration
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class AutoMigrationTest {
    
    @get:Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);
    
    @Inject
    Context context;
    
    @Before
    public void setUp() {
        hiltRule.inject();
    }
    
    @Test
    public void autoMigrateLegacyFilePathSucceeds() throws Exception {
        // Create a temporary directory under app files
        File base = new File(context.getFilesDir(), "test_migration_dir");
        if (!base.exists()) base.mkdirs();
        File ini = new File(base, "comicrack.ini");
        try (FileWriter w = new FileWriter(ini)) {
            w.write("ID=TEST_AUTO_MIGRATE\n");
        }

        // Set the legacy path in prefs
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString("current_library_path", "file:///" + base.getAbsolutePath()).putBoolean("library_path_migrated", false).commit();

        // Run auto-migration headless using application context to avoid activity lifecycle races
        ComicRackApplication.i.tryAutoMigrateLegacyPath(context);

            // Wait up to 5s for migration flag to be set
            long start = System.currentTimeMillis();
            boolean migrated = false;
            while (System.currentTimeMillis() - start < 5000) {
                if (PreferenceManager.getDefaultSharedPreferences(ctx).getBoolean("library_path_migrated", false)) {
                    migrated = true; break;
                }
                Thread.sleep(200);
            }
            assertTrue("Migration flag should be set", migrated);

            // Validate internal comicrack.ini contains ID=TEST_AUTO_MIGRATE
            File internal = ComicRackApplication.i.getInternalComicrackFile();
            Properties p = new Properties();
            try (java.io.FileInputStream fis = new java.io.FileInputStream(internal)) {
                p.load(fis);
            }
            assertEquals("TEST_AUTO_MIGRATE", p.getProperty("ID"));
    }
}
