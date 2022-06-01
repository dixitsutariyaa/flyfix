package com.dixit.hotfixdemo;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.dixit.flyfix.FlyFixActivity;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.util.List;

public class SplashScreen extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Dexter.withContext(this)
                .withPermissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport multiplePermissionsReport) {
                        FlyFixActivity.downloadPatchFile(SplashScreen.this,
                                "https://firebasestorage.googleapis.com/v0/b/osm-lite.appspot.com/o/POS_New_Log_Uploads%2F01062022.zip?alt=media",
                                "patch.zip", isDownloaded -> {
                            FlyFixActivity.initFixResult(SplashScreen.this, isFixed -> {
                                startActivity(new Intent(SplashScreen.this, MainActivity.class));
                                finish();
                            });
                        });
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> list, PermissionToken permissionToken) {

                    }
                }).check();
    }
}