package com.comicrack.a;

import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

/**
 * Simple integration test to verify comic file extension detection for common archive types.
 * Phase 3: Hilt DI Migration
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class ComicFileDetectionTest {
    
    @get:Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);
    
    @Before
    public void setUp() {
        hiltRule.inject();
    }

    @Test
    public void testIsComicBookFileExtensions() {
        // f.isComicBookFile uses case-insensitive suffix checks, so test a variety of extensions
        assertTrue(f.isComicBookFile("example.cbz"));
        assertTrue(f.isComicBookFile("example.cbr"));
        assertTrue(f.isComicBookFile("example.cbt"));
        assertTrue(f.isComicBookFile("example.cb7"));
        assertTrue(f.isComicBookFile("example.cbp"));
        assertTrue(f.isComicBookFile("example.zip"));
        assertTrue(f.isComicBookFile("example.rar"));

        // ComicRackManager uses simple endsWith checks - ensure it recognizes the new extensions too
        assertTrue(ComicRackManager.isComicBookFile("example.cbz"));
        assertTrue(ComicRackManager.isComicBookFile("example.cbr"));
        assertTrue(ComicRackManager.isComicBookFile("example.cbt"));
        assertTrue(ComicRackManager.isComicBookFile("example.cb7"));
        assertTrue(ComicRackManager.isComicBookFile("example.cbp"));
        assertTrue(ComicRackManager.isComicBookFile("example.zip"));
        assertTrue(ComicRackManager.isComicBookFile("example.rar"));
    }
}
