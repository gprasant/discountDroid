package com.personalzedDiscounts.android;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import com.facebook.android.DialogError;
import com.facebook.android.Facebook;
import com.facebook.android.FacebookError;
import org.json.JSONObject;

public class CredentialManager {
    private static final String USER = "user";
    private static final String ACCESS_EXPIRES = "access_expires";
    private static final String ACCESS_TOKEN = "access_token";
    private Facebook facebook;
    private SharedPreferences preferences;
    private static CredentialManager credentialManager = new CredentialManager();

    private CredentialManager(){
         facebook = new Facebook("226490220813959");
    }

    public static void init(SharedPreferences preferences) {
        credentialManager.preferences = preferences;
    }

    public static Facebook getFacebook(){
        return credentialManager.facebook;
    }

    public static String getCurrentUser(){
        return  credentialManager.preferences.getString(USER, "def_user");
    }

    public static void authorize(final Activity activity){
        credentialManager.authorizeWithFacebook(activity);
    }

    private String getUser(){
        try {
            String response = facebook.request("me");
            JSONObject jsonObject = new JSONObject(response);
            return jsonObject.getString("username");
        } catch (Exception e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }

    private void authorizeWithFacebook(final Activity activity) {
        String access_token = preferences.getString(ACCESS_TOKEN, null);
        long expires = preferences.getLong(ACCESS_EXPIRES, 0);
        if(access_token != null) {
            facebook.setAccessToken(access_token);
        }
        if(expires != 0) {
            facebook.setAccessExpires(expires);
        }

        if(!facebook.isSessionValid()) {

            facebook.authorize(activity, new String[] {}, new Facebook.DialogListener() {
                @Override
                public void onComplete(Bundle values) {
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString(ACCESS_TOKEN, facebook.getAccessToken());
                    editor.putLong(ACCESS_EXPIRES, facebook.getAccessExpires());
                    editor.putString(USER, getUser());
                    editor.commit();
                }

                @Override
                public void onFacebookError(FacebookError error) {
                    authorizeWithFacebook(activity);
                }

                @Override
                public void onError(DialogError e) {
                    authorizeWithFacebook(activity);
                }

                @Override
                public void onCancel() {
                    authorizeWithFacebook(activity);
                }
            });
        }
    }



}
