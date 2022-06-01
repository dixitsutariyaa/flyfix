/*
 * Created by Dixit Sutariya on 01/06/22, 6:25 PM
 *     dixitsutariya@gmail.com
 *     Last modified 01/06/22, 4:13 PM
 *     Copyright (c) 2022.
 *     All rights reserved.
 */

package com.dixit.flyfix.download;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadFile {
    private static final int UPDATE_DOWNLOAD_PROGRESS = 1;
    private static volatile DownloadFile _instance = null;
    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    DownloadListener onDownloadCallback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            if (msg.what == UPDATE_DOWNLOAD_PROGRESS) {
                int downloadProgress = msg.arg1;
                if (onDownloadCallback != null) {
                    onDownloadCallback.OnDownloadProgress(downloadProgress);
                }
            }
            return true;
        }
    });
    BroadcastReceiver receiver;
    int progress = 0;
    long downloadId;
    private DownloadManager dm;

    public synchronized static DownloadFile Instance() {
        if (_instance == null) {
            _instance = new DownloadFile();

        }
        return _instance;
    }

    public void doDownload(Context con, String url, File destinationPath, String filename, DownloadListener downloadcallback) {
        setBroadcastReceiver(con);
        onDownloadCallback = downloadcallback;
        dm = (DownloadManager) con.getSystemService(con.DOWNLOAD_SERVICE);
        url = url.trim();
        DownloadManager.Request request = new DownloadManager.Request(Uri
                .parse(url));
        //request.setDestinationInExternalPublicDir(con.getExternalFilesDir(null).toString(), filename);

//        request.setDestinationUri(Uri.fromFile(new File(con.getExternalFilesDir(null).toString(), filename)));
        request.setDestinationUri(Uri.fromFile(new File(destinationPath, filename)));
        downloadId = dm.enqueue(request);

        // Run a task in a background thread to check download progress
        executor.execute(new Runnable() {
            @Override
            public void run() {
                boolean isDownloadFinished = false;
                while (!isDownloadFinished) {
                    Cursor cursor = dm.query(new DownloadManager.Query().setFilterById(downloadId));
                    if (cursor.moveToFirst()) {
                        int downloadStatus = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                        switch (downloadStatus) {
                            case DownloadManager.STATUS_RUNNING:
                                long totalBytes = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                                if (totalBytes > 0) {
                                    long downloadedBytes = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                                    progress = (int) (((float) downloadedBytes * 100) / (float) totalBytes);
                                }

                                break;
                            case DownloadManager.STATUS_SUCCESSFUL:
                                progress = 100;
                                isDownloadFinished = true;
                                break;
                            case DownloadManager.STATUS_PAUSED:
                            case DownloadManager.STATUS_PENDING:
                                break;
                            case DownloadManager.STATUS_FAILED:
                                isDownloadFinished = true;
                                break;
                        }
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            public void run() {
                                Message message = Message.obtain();
                                message.what = UPDATE_DOWNLOAD_PROGRESS;
                                message.arg1 = progress;
                                mainHandler.sendMessage(message);
                            }
                        });

                    }
                    cursor.close();
                }
            }
        });
    }


    public void setBroadcastReceiver(Context con) {
        receiver = new BroadcastReceiver() {
            @SuppressLint("SdCardPath")
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                    long downloadId = intent.getLongExtra(
                            DownloadManager.EXTRA_DOWNLOAD_ID, 0);
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(downloadId);
                    Cursor c = dm.query(query);
                    if (c.moveToFirst()) {
                        int columnIndex = c
                                .getColumnIndex(DownloadManager.COLUMN_STATUS);
                        if (c != null) {
                            int status = c.getInt(columnIndex);
                            if (DownloadManager.STATUS_SUCCESSFUL == c
                                    .getInt(columnIndex)) {
                                int filenameIndex = c
                                        .getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                                if (filenameIndex != 0) {
                                    String filename = c.getString(filenameIndex);
                                    Log.i("CGM", "Filename:" + filename);
                                    Uri uri = dm.getUriForDownloadedFile(downloadId);
                                    if (onDownloadCallback != null) {
                                        onDownloadCallback.OnDownloadSuccess(uri);
                                    }
                                }
                            } else if (status == DownloadManager.STATUS_FAILED) {
                                if (onDownloadCallback != null) {
                                    onDownloadCallback.OnDownloadFailure("Download Failed,Server Error!!");
                                }
                            } else if (status == DownloadManager.STATUS_PAUSED) {
                                if (onDownloadCallback != null) {
                                    onDownloadCallback.OnDownloadFailure("Download Paused!!");
                                }
                            } else if (status == DownloadManager.STATUS_PENDING) {

                            } else if (status == DownloadManager.STATUS_RUNNING) {

                            }
                        }
                    }
                }
            }
        };
        con.registerReceiver(receiver, new IntentFilter(
                DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    public void unregisterReceiver(Context con) {
        try {
            if (receiver != null & con != null) {
                con.unregisterReceiver(receiver);
                receiver = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


}
