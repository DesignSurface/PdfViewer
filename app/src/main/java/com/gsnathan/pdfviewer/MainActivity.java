/*
 * MIT License
 *
 * Copyright (c) 2018 Gokul Swaminathan
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.gsnathan.pdfviewer;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Environment;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintManager;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import io.github.yavski.fabspeeddial.FabSpeedDial;
import io.github.yavski.fabspeeddial.SimpleMenuListenerAdapter;

import android.os.Bundle;

import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener;
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle;
import com.github.barteksc.pdfviewer.util.FitPolicy;
import com.jaredrummler.cyanea.prefs.CyaneaSettingsActivity;
import com.kobakei.ratethisapp.RateThisApp;
import com.shockwave.pdfium.PdfDocument;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.NonConfigurationInstance;
import org.androidannotations.annotations.OnActivityResult;
import org.androidannotations.annotations.ViewById;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

@EActivity(R.layout.activity_main)
public class MainActivity extends ProgressActivity implements OnPageChangeListener, OnLoadCompleteListener,
        OnPageErrorListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private PrintManager mgr = null;

    private final static int REQUEST_CODE = 42;
    public static final int PERMISSION_SAVE = 1;
    public static final int PERMISSION_CODE = 42042;

    public static final String SAMPLE_FILE = "pdf_sample.pdf";
    public static final String READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE";
    private static String PDF_PASSWORD = "";
    private SharedPreferences prefManager;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pdfFileName = "";
        prefManager = PreferenceManager.getDefaultSharedPreferences(this);
        onFirstInstall();
        onFirstUpdate();
        handleIntent(getIntent());

        if (Utils.tempBool && getIntent().getStringExtra("uri") != null)
            uri = Uri.parse(getIntent().getStringExtra("uri"));

        mgr = (PrintManager) getSystemService(PRINT_SERVICE);

        // Custom condition: 5 days and 5 launches
        RateThisApp.Config config = new RateThisApp.Config(5, 5);
        RateThisApp.init(config);
        RateThisApp.onCreate(this);
        RateThisApp.showRateDialogIfNeeded(this);
    }

    private void onFirstInstall() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isFirstRun = prefs.getBoolean("FIRSTINSTALL", true);
        if (isFirstRun) {
            startActivity(new Intent(this, MainIntroActivity.class));
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("FIRSTINSTALL", false);
            editor.apply();
        }
    }

    private void onFirstUpdate() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean isFirstRun = prefs.getBoolean(Utils.getAppVersion(), true);
        if (isFirstRun) {
            Utils.showLog(this);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(Utils.getAppVersion(), false);
            editor.apply();
        }
    }


    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        //Kinda not recommended by google but watever
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        Uri appLinkData = intent.getData();
        String appLinkAction = intent.getAction();
        if (Intent.ACTION_VIEW.equals(appLinkAction) && appLinkData != null) {
            uri = appLinkData;
        }
    }


    @ViewById
    PDFView pdfView;

    @ViewById
    FabSpeedDial fabMain;

    @NonConfigurationInstance
    static Uri uri;

    @NonConfigurationInstance
    Integer pageNumber = 0;

    String pdfFileName;

    String pdfTempFilePath;

    private void pickFile() {
        int permissionCheck = ContextCompat.checkSelfPermission(this,
                READ_EXTERNAL_STORAGE);

        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{READ_EXTERNAL_STORAGE},
                    PERMISSION_CODE
            );

            return;
        }

        launchPicker();
    }

    void shareFile() {
        startActivity(Utils.emailIntent(pdfFileName, "", getResources().getString(R.string.share), uri));
    }

    void launchPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        try {
            startActivityForResult(intent, REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            //alert user that file manager not working
            Toast.makeText(this, R.string.toast_pick_file_error, Toast.LENGTH_SHORT).show();
        }
    }

    @AfterViews
    void afterViews() {
        showProgressDialog();
        pdfView.setBackgroundColor(Color.LTGRAY);
        if (uri != null) {
            displayFromUri(uri);
        } else {
            displayFromAsset(SAMPLE_FILE);
        }
        setTitle(pdfFileName);
        hideProgressDialog();


        fabMain.setMenuListener(new SimpleMenuListenerAdapter() {
            @Override
            public boolean onMenuItemSelected(MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.pickFile:
                        pickFile();
                        break;
                    case R.id.metaFile:
                        if (uri != null)
                            getMeta();
                        break;
                    case R.id.unlockFile:
                        if (uri != null)
                            unlockPDF();
                        break;
                    case R.id.shareFile:
                        if (uri != null)
                            shareFile();
                        break;
                    case R.id.printFile:
                        if (uri != null)
                            print(pdfFileName,
                                    new PdfDocumentAdapter(getApplicationContext()),
                                    new PrintAttributes.Builder().build());
                        break;
                    default:
                        break;

                }
                return false;
            }
        });
    }


    void displayFromAsset(String assetFileName) {
        pdfFileName = assetFileName;

        pdfView.useBestQuality(prefManager.getBoolean("quality_pref", false));

        pdfView.fromAsset(assetFileName)
                .defaultPage(pageNumber)
                .onPageChange(this)
                .enableAnnotationRendering(true)
                .enableAntialiasing(prefManager.getBoolean("alias_pref", false))
                .onLoad(this)
                .scrollHandle(new DefaultScrollHandle(this))
                .spacing(10) // in dp
                .onPageError(this)
                .pageFitPolicy(FitPolicy.BOTH)
                .password(PDF_PASSWORD)
                .swipeHorizontal(prefManager.getBoolean("scroll_pref", false))
                .autoSpacing(prefManager.getBoolean("scroll_pref", false))
                .pageSnap(prefManager.getBoolean("snap_pref", false))
                .pageFling(prefManager.getBoolean("fling_pref", false))
                .load();
    }

    void displayFromUri(Uri uri) {
        pdfFileName = getFileName(uri);
        Utils.tempBool = true;
        SharedPreferences.Editor editor = prefManager.edit();
        editor.putString("uri", uri.toString());
        editor.apply();

        if (uri.getScheme().contains("http")) {
            // we will get the pdf asynchronously with the RetrievePDFStream object
            RetrievePDFStream retrievePDFStream = new RetrievePDFStream(this);
            retrievePDFStream.execute(uri.toString(), pdfFileName);
        } else {
            pdfView.useBestQuality(prefManager.getBoolean("quality_pref", false));

            pdfView.fromUri(uri)
                    .defaultPage(pageNumber)
                    .onPageChange(this)
                    .enableAnnotationRendering(true)
                    .enableAntialiasing(prefManager.getBoolean("alias_pref", false))
                    .onLoad(this)
                    .scrollHandle(new DefaultScrollHandle(this))
                    .spacing(10) // in dp
                    .onPageError(this)
                    .pageFitPolicy(FitPolicy.BOTH)
                    .password(PDF_PASSWORD)
                    .swipeHorizontal(prefManager.getBoolean("scroll_pref", false))
                    .autoSpacing(prefManager.getBoolean("scroll_pref", false))
                    .pageSnap(prefManager.getBoolean("snap_pref", false))
                    .pageFling(prefManager.getBoolean("fling_pref", false))
                    .load();
        }
    }

    void displayFromFile(File file) {
        pdfView.useBestQuality(prefManager.getBoolean("quality_pref", false));

        pdfView.fromFile(file)
                .defaultPage(pageNumber)
                .onPageChange(this)
                .enableAnnotationRendering(true)
                .enableAntialiasing(prefManager.getBoolean("alias_pref", false))
                .onLoad(this)
                .scrollHandle(new DefaultScrollHandle(this))
                .spacing(10) // in dp
                .onPageError(this)
                .pageFitPolicy(FitPolicy.BOTH)
                .password(PDF_PASSWORD)
                .swipeHorizontal(prefManager.getBoolean("scroll_pref", false))
                .autoSpacing(prefManager.getBoolean("scroll_pref", false))
                .pageSnap(prefManager.getBoolean("snap_pref", false))
                .pageFling(prefManager.getBoolean("fling_pref", false))
                .load();

    }

    public void saveFileAndDisplay (File file) {
        String filePath = saveTempFileToFile(file);

        pdfView.useBestQuality(prefManager.getBoolean("quality_pref", false));

        File newFile = new File(filePath);

        displayFromFile(newFile);
    }

    String saveTempFileToFile(File tempFile) {
        try {
            // check if the permission to write to external storage is granted
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                InputStream inputStream = new FileInputStream(tempFile);
                File newFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), pdfFileName);
                OutputStream outputStream = new FileOutputStream(newFile);
                Utils.readFromInputStreamToOutputStream(inputStream, outputStream);

                return tempFile.getPath();
            } else {
                // case if the permission hasn't been granted, we will store the pdf in a temp file
                //store the temporary file path, to be able to save it when permission will be granted
                pdfTempFilePath = tempFile.getPath();

                // request for the permission to write to external storage
                requestPermissions(
                        new String[] {
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        },
                        PERMISSION_SAVE
                );
                return pdfTempFilePath;
            }
        } catch (IOException e) {
            Log.e(TAG, "ERROR FILE " + e.getMessage() + e.getStackTrace());
        }

        return null;
    }

    void navToSettings() {
        startActivity(Utils.navIntent(this, SettingsActivity.class));
    }

    @OnActivityResult(REQUEST_CODE)
    void onResult(int resultCode, Intent intent) {
        if (resultCode == RESULT_OK) {
            uri = intent.getData();
            displayFromUri(uri);
        }
    }

    @Override
    public void onPageChanged(int page, int pageCount) {
        pageNumber = page;
        setTitle(String.format("%s %s / %s", pdfFileName + " ", page + 1, pageCount));
    }

    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int indexDisplayName = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (indexDisplayName != -1) {
                        result = cursor.getString(indexDisplayName);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    @Override
    public void loadComplete(int nbPages) {
        Log.d(TAG, "PDF loaded");

    }

    private PrintJob print(String name, PrintDocumentAdapter adapter,
                           PrintAttributes attrs) {
        startService(new Intent(this, PrintJobMonitorService.class));

        return (mgr.print(name, adapter, attrs));
    }

    void unlockPDF() {

        final EditText input = new EditText(this);
        input.setPadding(19, 19, 19, 19);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
                .setTitle(R.string.password)
                .setView(input)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        PDF_PASSWORD = input.getText().toString();
                        if (uri != null)
                            displayFromUri(uri);
                    }
                })
                .setIcon(R.drawable.lock_icon)
                .show();
    }

    void getMeta() {
        PdfDocument.Meta meta = pdfView.getDocumentMeta();
        if (meta != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.meta)
                    .setMessage("Title: " + meta.getTitle() + "\n" + "Author: " + meta.getAuthor() + "\n" + "Creation Date: " + meta.getCreationDate())
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .setIcon(R.drawable.alert_icon)
                    .show();
        }

    }

    public void printBookmarksTree(List<PdfDocument.Bookmark> tree, String sep) {
        for (PdfDocument.Bookmark b : tree) {

            Log.e(TAG, String.format("%s %s, p %d", sep, b.getTitle(), b.getPageIdx()));

            if (b.hasChildren()) {
                printBookmarksTree(b.getChildren(), sep + "-");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        int indexPermission = 0;
        switch (requestCode) {
            case PERMISSION_CODE:
                indexPermission = Arrays.asList(permissions).indexOf(Manifest.permission.READ_EXTERNAL_STORAGE);
                if (indexPermission != -1 && grantResults[indexPermission] == PackageManager.PERMISSION_GRANTED) {
                    launchPicker();
                }
                break;
            case PERMISSION_SAVE:
                indexPermission = Arrays.asList(permissions).indexOf(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                if (indexPermission != -1 && grantResults[indexPermission] == PackageManager.PERMISSION_GRANTED) {
                    File file = new File(pdfTempFilePath);
                    saveTempFileToFile(file);
                }
                break;
        }
    }


    @Override
    public void onPageError(int page, Throwable t) {
        Log.e(TAG, "Cannot load page " + page);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_about:
                startActivity(Utils.navIntent(this, AboutActivity.class));
                return true;
            case R.id.theme:
                startActivity(Utils.navIntent(getApplicationContext(), CyaneaSettingsActivity.class));
                return true;
            case R.id.settings:
                navToSettings();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
