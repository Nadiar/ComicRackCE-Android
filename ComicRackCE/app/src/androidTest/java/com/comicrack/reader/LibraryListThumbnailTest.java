package com.comicrack.reader;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;
import javax.inject.Inject;

/**
 * Instrumentation tests for library list thumbnail layout inflation.
 * Phase 3: Hilt DI Migration
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class LibraryListThumbnailTest {
    
    @get:Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);
    
    @Inject
    Context context;
    
    @Before
    public void setUp() {
        hiltRule.inject();
    }

    @Test
    public void inflateThumbnailAndContainsRequiredViews() {
        LayoutInflater inflater = LayoutInflater.from(context);
        View v = inflater.inflate(R.layout.library_list_thumbnail, null);
        assertNotNull("thumb1 should exist", v.findViewById(R.id.libraryListThumb1));
        assertNotNull("caption should exist", v.findViewById(R.id.libraryListThumbnailCaption));
        assertNotNull("description should exist", v.findViewById(R.id.libraryListThumbnailDescription));
    }
}
