/**
 * Copyright 2017 Kartik Arora
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.kartikarora.transfersh.activities;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.GridView;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import me.kartikarora.transfersh.BuildConfig;
import me.kartikarora.transfersh.R;
import me.kartikarora.transfersh.actions.IntentAction;
import me.kartikarora.transfersh.adapters.FileGridAdapter;
import me.kartikarora.transfersh.applications.TransferApplication;
import me.kartikarora.transfersh.contracts.FilesContract;
import me.kartikarora.transfersh.helpers.UtilsHelper;
import me.kartikarora.transfersh.network.TransferClient;
import retrofit.ResponseCallback;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.mime.TypedFile;

/**
 * Developer: chipset
 * Package : me.kartikarora.transfersh.activities
 * Project : Transfer.sh
 * Date : 9/6/16
 */
public class TransferActivity extends AppCompatActivity implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final int FILE_RESULT_CODE = BuildConfig.VERSION_CODE / 10000;
    private static final String PREF_GRID_VIEW_FLAG = "gridFlag";
    private boolean showAsGrid = false;
    private CoordinatorLayout mCoordinatorLayout;
    private TextView mNoFilesTextView;
    private GridView mFileItemsGridView;
    private FileGridAdapter mAdapter;
    private FirebaseAnalytics mFirebaseAnalytics;
    private AdView mAdView;
    private SharedPreferences mSharedPreferences = null;
    private Cursor mData = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transfer);
        mNoFilesTextView = findViewById(R.id.no_files_text_view);
        mFileItemsGridView = findViewById(R.id.file_grid_view);
        FloatingActionButton uploadFileButton = findViewById(R.id.upload_file_fab);
        mCoordinatorLayout = findViewById(R.id.coordinator_layout);
        mAdView = findViewById(R.id.banner_ad_view);

        if (uploadFileButton != null) {
            uploadFileButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.setType("*/*");
                    startActivityForResult(intent, FILE_RESULT_CODE);
                }
            });
        }

        getSupportLoaderManager().initLoader(BuildConfig.VERSION_CODE, null, this);
        TransferApplication application = (TransferApplication) getApplication();
        mFirebaseAnalytics = application.getDefaultTracker();

        UtilsHelper.getInstance().trackEvent(mFirebaseAnalytics, "Activity : " + this.getClass().getSimpleName(), "Launched");

        mSharedPreferences = getSharedPreferences(getPackageName(), MODE_PRIVATE);
        showAsGrid = mSharedPreferences.getBoolean(PREF_GRID_VIEW_FLAG, true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_RESULT_CODE && resultCode == RESULT_OK) {
            try {
                uploadFile(data.getData());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        AdRequest adRequest = new AdRequest.Builder()
                .addTestDevice("2F50AAA33A5225F171002E20B341E95D")
                .addTestDevice("DC2B94757C55E75585530EED78BBADEB")
                .build();
        mAdView.loadAd(adRequest);
        String action = getIntent().getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri dataUri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
            try {
                uploadFile(dataUri);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> dataUris = getIntent().getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            for (Uri uri : dataUris) {
                try {
                    uploadFile(uri);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else if (IntentAction.ACTION_REUPLOAD.equals(action)) {
            long id = getIntent().getLongExtra("file_id", -1);
            if (id != -1) {
                Cursor cursor = getContentResolver().query(FilesContract.BASE_CONTENT_URI, null, FilesContract.FilesEntry._ID + "=?", new String[]{String.valueOf(id)}, null);
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        Uri uri = Uri.parse(getString(cursor.getColumnIndex(FilesContract.FilesEntry.COLUMN_URI)));
                        try {
                            uploadFile(uri);
                            getContentResolver().delete(FilesContract.BASE_CONTENT_URI, FilesContract.FilesEntry._ID + "=?",
                                    new String[]{String.valueOf(id)});
                            mAdapter.notifyDataSetChanged();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    cursor.close();
                }
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    private void uploadFile(final Uri uri) throws IOException {
        final ProgressDialog dialog = new ProgressDialog(TransferActivity.this);
        dialog.setMessage(getString(R.string.uploading_file));
        dialog.setCancelable(false);
        dialog.show();
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            final String name = cursor.getString(nameIndex);
            final String mimeType = getContentResolver().getType(uri);
            InputStream inputStream = getContentResolver().openInputStream(uri);
            OutputStream outputStream = openFileOutput(name, MODE_PRIVATE);
            if (inputStream != null) {
                IOUtils.copy(inputStream, outputStream);
                final File file = new File(getFilesDir(), name);
                TypedFile typedFile = new TypedFile(mimeType, file);
                TransferClient.getInterface().uploadFile(typedFile, name, new ResponseCallback() {
                    @Override
                    public void success(Response response) {
                        BufferedReader reader;
                        StringBuilder sb = new StringBuilder();
                        try {
                            reader = new BufferedReader(new InputStreamReader(response.getBody().in()));
                            String line;
                            try {
                                while ((line = reader.readLine()) != null) {
                                    sb.append(line);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        final String result = sb.toString();
                        Snackbar.make(mCoordinatorLayout, name + " " + getString(R.string.uploaded), Snackbar.LENGTH_INDEFINITE)
                                .setAction(R.string.share, new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        UtilsHelper.getInstance().trackEvent(mFirebaseAnalytics, "Action", "Share : " + result);
                                        startActivity(new Intent()
                                                .setAction(Intent.ACTION_SEND)
                                                .putExtra(Intent.EXTRA_TEXT, result)
                                                .setType("text/plain")
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                                    }
                                }).show();

                        SimpleDateFormat sdf = UtilsHelper.getInstance().getSdf();
                        Calendar upCal = Calendar.getInstance();
                        upCal.setTime(new Date(file.lastModified()));
                        Calendar delCal = Calendar.getInstance();
                        delCal.setTime(upCal.getTime());
                        delCal.add(Calendar.DATE, 14);
                        ContentValues values = new ContentValues();
                        values.put(FilesContract.FilesEntry.COLUMN_NAME, name);
                        values.put(FilesContract.FilesEntry.COLUMN_TYPE, mimeType);
                        values.put(FilesContract.FilesEntry.COLUMN_URL, result);
                        values.put(FilesContract.FilesEntry.COLUMN_URI, uri.toString());
                        values.put(FilesContract.FilesEntry.COLUMN_SIZE, String.valueOf(file.getTotalSpace()));
                        values.put(FilesContract.FilesEntry.COLUMN_DATE_UPLOAD, sdf.format(upCal.getTime()));
                        values.put(FilesContract.FilesEntry.COLUMN_DATE_DELETE, sdf.format(delCal.getTime()));
                        getContentResolver().insert(FilesContract.BASE_CONTENT_URI, values);
                        FileUtils.deleteQuietly(file);
                        if (dialog.isShowing())
                            dialog.hide();
                    }

                    @Override
                    public void failure(RetrofitError error) {
                        error.printStackTrace();
                        if (dialog.isShowing())
                            dialog.hide();
                        Snackbar.make(mCoordinatorLayout, R.string.something_went_wrong, Snackbar.LENGTH_LONG).show();
                    }
                });
            } else {
                Snackbar.make(mCoordinatorLayout, R.string.unable_to_read, Snackbar.LENGTH_SHORT).show();
            }
            cursor.close();
        } else {
            Snackbar.make(mCoordinatorLayout, R.string.unable_to_read, Snackbar.LENGTH_SHORT).show();
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(this, FilesContract.BASE_CONTENT_URI, null, null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {

        mData = data;
        display(data);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        mFileItemsGridView.setVisibility(View.VISIBLE);
        mNoFilesTextView.setVisibility(View.GONE);
        mAdapter.swapCursor(null);
        mData = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == FileGridAdapter.PERM_REQUEST_CODE && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                mAdapter.getPermissionRequestResult().onPermitted();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_transfer, menu);
        menu.getItem(0).setVisible(!showAsGrid);
        menu.getItem(1).setVisible(showAsGrid);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_about) {
            startActivity(new Intent(this, AboutActivity.class));
        } else if (item.getItemId() == R.id.action_view_grid) {
            showAsGrid = true;
            mSharedPreferences.edit().putBoolean(PREF_GRID_VIEW_FLAG, true).apply();
            mFileItemsGridView.setNumColumns(getResources().getInteger(R.integer.col_count));
        } else if (item.getItemId() == R.id.action_view_list) {
            showAsGrid = false;
            mSharedPreferences.edit().putBoolean(PREF_GRID_VIEW_FLAG, false).apply();
            mFileItemsGridView.setNumColumns(1);
        }
        invalidateOptionsMenu();
        display(mData);
        return super.onOptionsItemSelected(item);
    }

    private void display(Cursor data) {
        mAdapter = new FileGridAdapter(TransferActivity.this, data, mFirebaseAnalytics, showAsGrid);
        if (showAsGrid) {
            mFileItemsGridView.setNumColumns(getResources().getInteger(R.integer.col_count));
        } else {
            mFileItemsGridView.setNumColumns(1);
        }
        mFileItemsGridView.setAdapter(mAdapter);

        if (null != data && data.getCount() == 0) {
            mFileItemsGridView.setVisibility(View.GONE);
            mNoFilesTextView.setVisibility(View.VISIBLE);
        } else {
            mFileItemsGridView.setVisibility(View.VISIBLE);
            mNoFilesTextView.setVisibility(View.GONE);
        }
    }
}