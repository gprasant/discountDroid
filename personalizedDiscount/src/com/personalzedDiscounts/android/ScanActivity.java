package com.personalzedDiscounts.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.View;
import android.widget.SlidingDrawer;
import android.widget.Toast;
import com.aurasma.aurasma.AurasmaIntentFactory;
import com.aurasma.aurasma.application.AurasmaSetupCallback;
import com.aurasma.aurasma.exceptions.AurasmaLaunchException;
import com.moodstocks.android.MoodstocksError;
import com.moodstocks.android.Result;
import com.moodstocks.android.ScannerSession;

public class ScanActivity extends Activity implements ScannerSession.Listener, View.OnClickListener, ProgressDialog.OnCancelListener {
	//-----------------------------------
	// Interface implemented by overlays
	//-----------------------------------
	public static interface Listener {
		/* send a new result to Overlay */
		public void onResult(ScannerSession session, Result result);
		/* send any other information in a Bundle */
		public void onStatusUpdate(Bundle status);
	}

	// Enabled scanning types: configure it according to your needs.
	// Here we allow Image recognition, EAN13 and QRCodes decoding.
	// Feel free to add `EAN8` if you want in addition to decode EAN-8.
	private int ScanOptions = Result.Type.IMAGE | Result.Type.EAN13 | Result.Type.QRCODE;

	public static final String TAG = "Main";
	private ScannerSession session;
	private Overlay overlay;
	private View touch;
	private Bundle status;
	private ProgressDialog searching;
    private static final boolean DELAY_START = false;

    private static final int DIALOG_PROGRESS = 0;
    private static final int DIALOG_ERROR = 1;


    @Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
	}

	@Override
	protected void onResume() {
		super.onResume();
		// initialize the overlay, that will display results and informations
		overlay = (Overlay) findViewById(R.id.overlay);
		overlay.init();
		// initialize the tap-on-screen
		touch = findViewById(R.id.touch);
		touch.setOnClickListener(this);
		// get the camera preview surface
		SurfaceView preview = (SurfaceView) findViewById(R.id.preview);
		
		// Create a scanner session
		try {
			session = new ScannerSession(this, this, preview);
		} catch (MoodstocksError e) {
			e.log();
		}
		// set session options
		session.setOptions(ScanOptions);
		// start scanning!
		session.resume();
		
		// Send information to the overlay
		status = new Bundle();
		status.putBoolean("decode_ean_8", (ScanOptions & Result.Type.EAN8) != 0);
		status.putBoolean("decode_ean_13", (ScanOptions & Result.Type.EAN13) != 0);
		status.putBoolean("decode_qrcode", (ScanOptions & Result.Type.QRCODE) != 0);
		overlay.onStatusUpdate(status);
	}

    @Override
	protected void onPause() {
		super.onPause();
		session.close();
		finish();
	}
	
	@Override
	public void onBackPressed() {
		SlidingDrawer drawer = (SlidingDrawer) findViewById(R.id.drawer);
		if (drawer.isOpened()) {
			drawer.animateClose();
		}
		else {
			super.onBackPressed();
		}
	}

	//-------------------------
	// ScannerSession.Listener
	//-------------------------

	@Override
	public void onScanComplete(Result result) {
		if (result != null) {
			// pause scanning session
			session.pause();
			// result found, send to overlay
			overlay.onResult(session, result);
		}
	}
	
	@Override
	public void onApiSearchStart() {
		// inform user
		searching = ProgressDialog.show(this, "", "Searching...", true, true, this);
	}
	
	@Override
	public void onApiSearchComplete(Result result) {
		searching.dismiss();
		if (result != null) {
			// pause scanning session
			session.pause();
			// result found, send to overlay
			overlay.onResult(session, result);
		}
		else {
			// no result found, inform user
			Toast t = Toast.makeText(this, "No match found", Toast.LENGTH_SHORT);
			t.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM, 0, 200);
			t.show();
		}
	}
	
	
	@Override
	public void onApiSearchFailed(MoodstocksError e) {
		searching.dismiss();
		// A problem occurred, e.g. there is no available network. Inform user:
		Toast t = Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT);
		t.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.BOTTOM, 0, 200);
		t.show();
	}
	
	//----------------------
	// View.OnClickListener
	//----------------------
	
	// Intercept tap-on-screen:
	@Override
	public void onClick(View v) {
		if (v == touch) {
			session.snap();
		}
	}
    public void onAurasmaClick(View v){
        final Intent aurasmaIntent;
        try {
            aurasmaIntent = AurasmaIntentFactory.getAurasmaLaunchIntent(this,
                    getString(R.string.app_name), getString(R.string.app_version));
        } catch (AurasmaLaunchException e) {
            Log.e("AKTest", "Error getting intent", e);
            showDialog(DIALOG_ERROR);
            return;
        }

        if (DELAY_START) {
            AurasmaSetupCallback callback = new AurasmaSetupCallback() {

                @Override
                public void onLoaded() {
                    dismissDialog(DIALOG_PROGRESS);
                    startActivity(aurasmaIntent);
                }

                @Override
                public void onLoadWarning(final int code) {
                    Log.w("AKTest", "Preload warning: " + code);
                }

                @Override
                public void onLoadFail(final int code) {
                    Log.e("AKTest", "Preload error: " + code);
                    dismissDialog(DIALOG_PROGRESS);
                    showDialog(DIALOG_ERROR);
                }
            };
            showDialog(DIALOG_PROGRESS);

            AurasmaIntentFactory.startAurasmaPreload(getApplicationContext(), aurasmaIntent,
                    callback);
        } else {
            startActivity(aurasmaIntent);
        }
    }
	
	//---------------------------------
	// ProgressDialog.OnCancelListener
	//---------------------------------
	
	// User cancelled snap
	@Override
	public void onCancel(DialogInterface dialog) {
		if (dialog == this.searching) {
			session.cancel();
		}
	}
    @Override
    protected Dialog onCreateDialog(final int id, final Bundle args) {
        if (id == DIALOG_PROGRESS) {
            ProgressDialog dialog = new ProgressDialog(this);
            dialog.setMessage("Aurasma is loading");
            dialog.setIndeterminate(true);
            dialog.setCancelable(false);
            return dialog;
        } else if (id == DIALOG_ERROR) {
            return new AlertDialog.Builder(this).setCancelable(true).setMessage(
                    "Error starting Aurasma").setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface d, final int i) {
                        }
                    }).create();
        }
        return super.onCreateDialog(id);
    }
	
	
}