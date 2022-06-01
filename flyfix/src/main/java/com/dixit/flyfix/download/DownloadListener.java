package com.dixit.flyfix.download;

import android.net.Uri;

public interface DownloadListener {
    void OnDownloadSuccess(Uri uri);

    void OnDownloadFailure(String error);
    void OnDownloadProgress(int progress);
}
