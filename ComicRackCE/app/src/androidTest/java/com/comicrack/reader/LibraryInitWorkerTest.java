package com.comicrack.reader;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Data;

import com.comicrack.reader.LibraryInitWorker;
import com.comicrack.reader.ComicRackApplication;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import javax.inject.Inject;

/**
 * Instrumentation tests for LibraryInitWorker background task scheduling.
 * Phase 3: Hilt DI Migration
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class LibraryInitWorkerTest {
    
    @get:Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);
    
    @Inject
    Context context;
    
    @Before
    public void setUp() {
        hiltRule.inject();
    }

    @Test
    public void workerTriggersAppLibraryInit() throws Exception {
        ComicRackApplication app = (ComicRackApplication) context;

        // Enqueue worker with current library path (may be empty)
        String cur = android.preference.PreferenceManager.getDefaultSharedPreferences(app).getString("current_library_path", "");
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(LibraryInitWorker.class)
                .setInputData(new Data.Builder().putString(LibraryInitWorker.KEY_LIBRARY_PATH, cur).build())
                .build();
        androidx.work.Operation op = WorkManager.getInstance(app).enqueue(req);
        // Wait for the work to be scheduled
        op.getResult().get();

        // Wait for worker to finish (poll WorkInfo) up to 20 seconds
        androidx.work.WorkInfo.State state = null;
        long deadline = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < deadline) {
            androidx.work.WorkInfo info = WorkManager.getInstance(app).getWorkInfoById(req.getId()).get();
            state = info != null ? info.getState() : null;
            if (state == androidx.work.WorkInfo.State.SUCCEEDED || state == androidx.work.WorkInfo.State.FAILED) break;
            Thread.sleep(250);
        }

        // Confirm worker executed by looking for its test flag or work state success
        java.io.File completed = new java.io.File(app.getFilesDir(), "TEST_init_completed");
        boolean workerRan = (state == androidx.work.WorkInfo.State.SUCCEEDED) || completed.exists();

        // Then wait up to 5s for the app-level init marker or for the app to report library initialized
        java.io.File marker = new java.io.File(app.getFilesDir(), "TEST_library_init_done");
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 5000) {
            if (marker.exists()) break;
            if (app.d()) break;
            Thread.sleep(200);
        }

        assertTrue("Worker should run and trigger app-level init (or at least run)", workerRan);
        assertTrue("Library should be initialized or marker should exist", marker.exists() || app.d());
    }
}
