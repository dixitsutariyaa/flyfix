package com.dixit.hotfixdemo;

import android.content.Context;

import androidx.multidex.MultiDex;
import androidx.multidex.MultiDexApplication;

public class MyAPP extends MultiDexApplication {
    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    protected void attachBaseContext(Context base) {
        MultiDex.install(base);
//        FixDexUtil.loadFixedDex(base,true);
        super.attachBaseContext(base);
    }
}
