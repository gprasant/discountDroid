package com.personalzedDiscounts.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
import com.facebook.android.DialogError;
import com.facebook.android.Facebook;
import com.facebook.android.FacebookError;
import com.moodstocks.android.MoodstocksError;
import com.moodstocks.android.Scanner;
import org.json.JSONObject;

import java.io.IOException;

public class HomeScreen extends Activity implements View.OnClickListener, Scanner.SyncListener {

	public static final String TAG = "HomeScreen";
	private boolean compatible = false;
	private Scanner scanner = null;

	/* sync related variables */
	private long last_sync = 0;
	private static final long DAY = DateUtils.DAY_IN_MILLIS;

    Facebook facebook = new Facebook("226490220813959");
    private SharedPreferences mPrefs;



    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        authorizeWithFacebook();

        try {

            String me = facebook.request("me");
            Toast.makeText(getApplicationContext(),me,Toast.LENGTH_SHORT);
            Log.i(me,"superUnique");
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(),"meow",Toast.LENGTH_LONG);
            Toast.makeText(getApplicationContext(),e.getMessage(),Toast.LENGTH_LONG);
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }


        /* First of all, check that the device is compatible, aka runs Android 2.3 or over.
           * If it's not the case, you **must** not try using the scanner as it will crash.
           * Here we chose to inform the user with a popup and kill the app. In practice, you
           * may want to do this verification at application startup and display the button
           * allowing scanner access if and only if the device is compatible.
           */
		compatible = Scanner.isCompatible();
		if (compatible) {
			setContentView(R.layout.home);
			findViewById(R.id.scan_button).setOnClickListener(this);
			try {
				this.scanner = Scanner.get();
				/* Open the scanner, necessary to perform any operation using it.
				 * This step also checks at runtime that the device is compatible.
				 * If the device is not compatible, it will throw a RuntimeException
				 * and crash the app.
				 */
				scanner.open(this, "ms.db");
			} catch (MoodstocksError e) {
				/* an error occurred while opening the scanner */
				if (e.getErrorCode() == MoodstocksError.Code.CREDMISMATCH) {
					// == DO NOT USE IN PRODUCTION: THIS IS A HELP MESSAGE FOR DEVELOPERS
					String errmsg = "there is a problem with your key/secret pair: "+
							"the current pair does NOT match with the one recorded within the on-disk datastore. "+
							"This could happen if:\n"+
							" * you have first build & run the app without replacing the default"+
							" \"ApIkEy\" and \"ApIsEcReT\" pair, and later on replaced with your real key/secret,\n"+
							" * or, you have first made a typo on the key/secret pair, build & run the"+
							" app, and later on fixed the typo and re-deployed.\n"+
							"\n"+
							"To solve your problem:\n"+
							" 1) uninstall the app from your device,\n"+
							" 2) make sure to properly configure your key/secret pair within Scanner.java\n"+
							" 3) re-build & run\n";
					MoodstocksError err = new MoodstocksError(errmsg, MoodstocksError.Code.CREDMISMATCH);
					err.log();
					finish();
					// == DO NOT USE IN PRODUCTION: THIS WAS A HELP MESSAGE FOR DEVELOPERS
				}
				else {
					e.log();
				}
			}
		}
		else {
			/* device is *not* compatible. In this demo application, we chose
       * to inform the user and exit application. `compatible` flag is here
       * to avoid calling scanner methods that *will* fail and log errors. 
       */
      AlertDialog.Builder builder = new AlertDialog.Builder(this);
      builder.setCancelable(false);
      builder.setTitle("Unsupported device!");
      builder.setMessage("Device must run Android Gingerbread or over, sorry...");
      builder.setNeutralButton("Quit", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int id) {
          finish();
        }
      });
      builder.show();
		}
	}

    private String getUser(){
        try {
            String response = facebook.request("me");
            JSONObject jsonObject = new JSONObject(response);
            String username = jsonObject.getString("username");
            return username;
        } catch (Exception e) {
            e.printStackTrace();
            return e.getMessage();
        }
    }

    private void authorizeWithFacebook() {
        mPrefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
        String access_token = mPrefs.getString("access_token", null);
        long expires = mPrefs.getLong("access_expires", 0);
        if(access_token != null) {
            facebook.setAccessToken(access_token);
        }
        if(expires != 0) {
            facebook.setAccessExpires(expires);
        }

        if(!facebook.isSessionValid()) {

            facebook.authorize(this, new String[] {}, new Facebook.DialogListener() {
                @Override
                public void onComplete(Bundle values) {
                    SharedPreferences.Editor editor = mPrefs.edit();
                    editor.putString("access_token", facebook.getAccessToken());
                    editor.putLong("access_expires", facebook.getAccessExpires());
                    editor.putString("user", getUser());
                    editor.commit();
                }

                @Override
                public void onFacebookError(FacebookError error) {
                    Toast.makeText(getApplicationContext(),error.getMessage(),Toast.LENGTH_SHORT);
                    authorizeWithFacebook();
                }

                @Override
                public void onError(DialogError e) {
                    Toast.makeText(getApplicationContext(),e.getMessage(),Toast.LENGTH_SHORT);
                    authorizeWithFacebook();
                }

                @Override
                public void onCancel() {
                    Toast.makeText(getApplicationContext(),R.string.please_login,Toast.LENGTH_SHORT);
                    authorizeWithFacebook();
                }
            });
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        facebook.authorizeCallback(requestCode, resultCode, data);
    }
	
	@Override
	protected void onResume() {
		/* perform a sync if:
		 * - the app is started either for the first time, 
		 *   or has been killed and is started back.
		 * - the app is resumed from the background AND 
		 *   has not been synced for more than one day.
		 */
		super.onResume();
        facebook.extendAccessTokenIfNeeded(this, null);
        if (System.currentTimeMillis() - last_sync > DAY)
			scanner.sync(this);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (compatible) {
			try {
				/* you must close the scanner before exiting */
				scanner.close();
			} catch (MoodstocksError e) {
				e.log();
			}
		}
	}

	@Override
	public void onClick(View v) {
		if (v == findViewById(R.id.scan_button)) {
			// launch scanner
			startActivity(new Intent(this, ScanActivity.class));
		}
	}

	//----------------------
	// Scanner.SyncListener
	//----------------------
	
	/* The synchronization is performed seamlessly. Until it has ended,
	 * the user can still use the online search as a fallback.
	 */

	@Override
	public void onSyncStart() {
		// Developer logs, do not use in production
		Log.d(TAG, "[SYNC] Starting...");
	}

	@Override
	public void onSyncComplete() {
		last_sync = System.currentTimeMillis();
		// Developer logs, do not use in production
		Log.d(TAG, "[SYNC] Complete!");
	}

	@Override
	public void onSyncFailed(MoodstocksError e) {
		// fail silently, the user has online search fallback.
		e.log();
	}

	@Override
	public void onSyncProgress(int total, int current) {
		// Developer logs, do not use in production
		Log.d(TAG, "[SYNC] "+current+"/"+total);
	}

}
