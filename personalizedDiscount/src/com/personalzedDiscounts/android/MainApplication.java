package com.personalzedDiscounts.android;

import android.app.Application;

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        CredentialManager.init(getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE));
    }

}


