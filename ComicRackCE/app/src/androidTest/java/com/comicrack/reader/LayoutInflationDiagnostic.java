package com.comicrack.reader;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class LayoutInflationDiagnostic {

    private static final int[] LAYOUTS = new int[] {
            R.layout.about,
            R.layout.reader,
            R.layout.library,
            R.layout.comic_list,
            /* test-only layouts are resolved at runtime */
            R.layout.comic_rating,
            R.layout.comic_widget,
            R.layout.comic_widget_stack,
            R.layout.comic_details,
            R.layout.comic_details_fragment,
            R.layout.rename_ecomic,
            R.layout.splash_screen,
            R.layout.page_thumbnail,
            R.layout.comic_goto_page,
            R.layout.ok_cancel_strip,
            R.layout.super_menu_item,
            R.layout.super_menu_item_singleline,
            R.layout.migration_banner,
            R.layout.reader_bottom_bar,
            R.layout.comic_edit_details,
            R.layout.comic_list_fragment,
            R.layout.comic_list_thumbnail,
            R.layout.comic_list_thumbnail_compact,
            R.layout.comic_list_thumbnail_tile,
            R.layout.folder_browser_entry,
            R.layout.reader_color_control,
            R.layout.alertdialog_smalltext,
            R.layout.library_list_fragment,
            R.layout.comic_bookmarks_dialog,
            R.layout.library_list_thumbnail,
            R.layout.preference_with_button
    };

    @Test
    public void runDiagnosticAndWriteReport() {
        Context ctx = ApplicationProvider.getApplicationContext();
        LayoutInflater inflater = LayoutInflater.from(ctx);
        List<String> results = new ArrayList<>();
        StringBuilder verbose = new StringBuilder();
        for (int id : LAYOUTS) {
            String resName = ctx.getResources().getResourceEntryName(id);
            final int layoutId = id;
            final String name = resName;
            final java.util.concurrent.atomic.AtomicReference<Throwable> errorRef = new java.util.concurrent.atomic.AtomicReference<>();
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    try {
                        View v = inflater.inflate(layoutId, null);
                    } catch (Throwable e) {
                        errorRef.set(e);
                    }
                }
            });
            Throwable err = errorRef.get();
            if (err == null) {
                results.add(name + " : OK");
            } else {
                results.add(name + " : FAIL - " + err.getClass().getSimpleName() + " - " + err.getMessage());
                android.util.Log.e("LayoutDiag", "Inflation failed for " + name, err);
                // Append stack trace to verbose buffer
                java.io.StringWriter sw = new java.io.StringWriter();
                java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                err.printStackTrace(pw);
                verbose.append("--- ").append(name).append(" ---\n");
                verbose.append(sw.toString()).append("\n\n");
            }
        }

        // Attempt to run test-only layouts if available in the androidTest package
        String[] testNames = new String[]{"test_reader", "test_reader_apk", "test_reader_minimal"};
        for (String tname : testNames) {
            int tid = ctx.getResources().getIdentifier(tname, "layout", "com.comicrack.viewer.test");
            if (tid != 0) {
                final String name = tname;
                final int layoutId = tid;
                final java.util.concurrent.atomic.AtomicReference<Throwable> errorRef = new java.util.concurrent.atomic.AtomicReference<>();
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            View v = inflater.inflate(layoutId, null);
                        } catch (Throwable e) {
                            errorRef.set(e);
                        }
                    }
                });
                Throwable err = errorRef.get();
                if (err == null) results.add(name + " : OK"); else {
                    results.add(name + " : FAIL - " + err.getClass().getSimpleName() + " - " + err.getMessage());
                    java.io.StringWriter sw = new java.io.StringWriter();
                    java.io.PrintWriter pw = new java.io.PrintWriter(sw);
                    err.printStackTrace(pw);
                    verbose.append("--- ").append(name).append(" ---\n");
                    verbose.append(sw.toString()).append("\n\n");
                }
            }
        }
        // Log results and write a report to external files dir (more likely to be present under test harness)
        for (String r : results) {
            android.util.Log.i("LayoutDiag", r);
            System.out.println(r);
        }
        try {
            File external = ctx.getExternalFilesDir(null);
            if (external != null) {
                File out = new File(external, "layout_inflation_report.txt");
                try (PrintWriter pw = new PrintWriter(new FileOutputStream(out, false))) {
                    for (String r : results) pw.println(r);
                }
                if (verbose.length() > 0) {
                    File outV = new File(external, "layout_inflation_report_verbose.txt");
                    try (PrintWriter pwv = new PrintWriter(new FileOutputStream(outV, false))) {
                        pwv.print(verbose.toString());
                    }
                }
            }
        } catch (Throwable t) {
            android.util.Log.w("LayoutDiag", "Failed to write report", t);
        }
    }
}
