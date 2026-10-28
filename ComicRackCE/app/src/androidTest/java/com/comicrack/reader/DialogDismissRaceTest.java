package com.comicrack.reader;

import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.core.app.ActivityScenario;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class DialogDismissRaceTest {

    @get:Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Before
    public void setUp() {
        hiltRule.inject();
    }

    @Test
    public void noCrashWhenActivityFinishesWhileAsyncTaskDismissesDialog() throws Exception {
        android.content.Intent intent = new android.content.Intent(androidx.test.core.app.ApplicationProvider.getApplicationContext(), TestIpActivity.class);
        ActivityScenario<TestIpActivity> scenario = ActivityScenario.launch(intent);
        final CountDownLatch latch = new CountDownLatch(1);

        scenario.onActivity(activity -> {
            try {
                // Reflectively create and execute the app's ci AsyncTask to exercise dialog dismissal path
                Class<?> ciClass = Class.forName("com.com.comicrack.viewer.LibraryRefreshTask");
                Constructor<?> ctor = ciClass.getDeclaredConstructors()[0];
                ctor.setAccessible(true);
                Object ci = ctor.newInstance(activity.getApplication(), activity);
                Method execute = ciClass.getMethod("execute", Object[].class);
                // Schedule finishing the activity shortly after task starts to induce race
                activity.runOnUiThread(() -> {
                    // start the task
                    try {
                        execute.invoke(ci, new Object[] { (Object) new Integer[0] });
                    } catch (Exception e) {
                        fail("Failed to execute ci: " + e);
                    }
                    activity.getWindow().getDecorView().postDelayed(() -> {
                        activity.finish();
                    }, 250);
                });

                // Wait up to 5s for task to finish (async tasks should complete quickly in tests)
                activity.getWindow().getDecorView().postDelayed(() -> latch.countDown(), 2000);
            } catch (Throwable t) {
                fail("Exception preparing ci: " + t);
            }
        });

        if (!latch.await(6, TimeUnit.SECONDS)) {
            fail("Timed out waiting for task to finish");
        }
    }
}
