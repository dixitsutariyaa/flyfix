/*
 * Created by Dixit Sutariya on 01/06/22, 6:24 PM
 *     dixitsutariya@gmail.com
 *     Last modified 01/06/22, 6:23 PM
 *     Copyright (c) 2022.
 *     All rights reserved.
 */

package com.dixit.flyfix;

import static com.dixit.flyfix.FixDexUtil.DEX_DIR;
import static com.dixit.flyfix.FixDexUtil.DEX_DIR_EXT;

import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.dixit.flyfix.download.DownloadFile;
import com.dixit.flyfix.download.DownloadListener;
import com.dixit.flyfix.download.UriUtils;

import net.lingala.zip4j.ZipFile;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipInputStream;

public class FlyFixActivity extends AppCompatActivity {
    static ProgressDialog progressDialog;
    static FixCallBack fixCallBack;
    static DownloadCallBack downloadCallBack;

    public static void initFixResult(Context context, FixCallBack callb) {
        fixCallBack = callb;
        if (progressDialog == null) progressDialog = new ProgressDialog(context);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            if (FixDexUtil.isGoingToFix(context.getApplicationContext())) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    progressDialog.setMessage("Please wait...");
                    progressDialog.show();
                });
                FixDexUtil.loadFixedDex(context.getApplicationContext());
                progressDialog.dismiss();
                fixCallBack.onResult(true);
            } else {
                progressDialog.dismiss();
                fixCallBack.onResult(false);
            }
        });
    }

    public static void downloadPatchFile(Context context,String patchUrl, String patchFileName, DownloadCallBack downloadCB) {
//        String patchFileName = "patch.zip";
        downloadCallBack = downloadCB;
        File externalStorageDirectory = Environment.getExternalStorageDirectory();
        File fileDir = externalStorageDirectory != null ?
                new File(externalStorageDirectory, DEX_DIR_EXT) :
                new File(context.getFilesDir(), DEX_DIR);
        if (!fileDir.exists()) {
            fileDir.mkdirs();
        }
        File patchFile = new File(fileDir, UriUtils.getFileNameWithoutExtension(patchFileName));
        if (!patchFile.exists()) { //if not exist then download
            if (progressDialog == null) progressDialog = new ProgressDialog(context);
            progressDialog.show();
            DownloadFile.Instance().doDownload(context, patchUrl, fileDir, patchFileName, new DownloadListener() {
                @Override
                public void OnDownloadSuccess(Uri path) {
                    File file = new File(UriUtils.getPathFromUri(context, path));
                    boolean isUnpacked = unpackZip(file.getPath(), file.getName());
                    if (isUnpacked) {
                        downloadCallBack.onResult(true);
                        progressDialog.dismiss();
                    }
                }

                @Override
                public void OnDownloadFailure(String error) {
                    downloadCallBack.onResult(false);
                    progressDialog.dismiss();
                }

                @Override
                public void OnDownloadProgress(int progress) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        public void run() {
                            progressDialog.setProgress(progress);
                            progressDialog.setMessage("Downloading... " + progress + "%");
                        }
                    });

                }
            });
        } else {
            downloadCallBack.onResult(false);
        }

    }

    private static boolean unpackZip(String path, String fileName) {
        InputStream is;
        ZipInputStream zis;
        try {
            ZipFile zipFile = new ZipFile(path);
            is = new FileInputStream(path);
            zis = new ZipInputStream(new BufferedInputStream(is));

            File selectedFile = new File(path);
            String fileNameWithoutExt = fileName.substring(0, fileName.indexOf("."));
            File directory = new File(selectedFile.getParent() + "/" + fileNameWithoutExt);
            if (!directory.exists()) {
                if (directory.listFiles() != null && directory.listFiles().length > 0) {
                    for (File file : directory.listFiles()) {
                        file.delete();
                    }
                }
                directory.delete();
                directory.mkdir();
            }

            if (zipFile.isEncrypted()) {
                char[] passwordChars = new char[]{'A', 'd', 'm', 'i', 'n', '@', '1', '2', '3'};
                zipFile.setPassword(passwordChars);
            }
            zipFile.extractAll(directory.getAbsolutePath());
            zis.close();
            selectedFile.delete();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    private void clearDexDirectory(File directory) {
        if (directory.isDirectory())
            for (File child : directory.listFiles())
                clearDexDirectory(child);
        directory.delete();
    }

    public interface FixCallBack {
        void onResult(Boolean isFixed);
    }

    public interface DownloadCallBack {
        void onResult(Boolean isDownloaded);
    }
}
