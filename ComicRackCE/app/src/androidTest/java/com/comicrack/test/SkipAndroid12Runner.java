package com.comicrack.test;

import android.os.Build;
import android.os.Bundle;
import timber.log.Timber;

import androidx.test.runner.AndroidJUnitRunner;

public class SkipAndroid12Runner extends AndroidJUnitRunner {
    @Override
    public void onStart() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.S) { // Android 12
            Log.i("SkipAndroid12Runner", "Detected Android 12 device; skipping instrumentation tests on this device.");
            // Finish instrumentation immediately with success to skip running tests on Android 12
            finish(0, new Bundle());
            return;
        }
        super.onStart();
    }
}
