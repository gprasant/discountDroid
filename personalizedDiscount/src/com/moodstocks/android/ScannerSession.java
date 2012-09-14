/*
 * Copyright (c) 2012 Moodstocks SAS
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.moodstocks.android;

import android.app.Activity;
import android.hardware.Camera;
import android.view.SurfaceView;

public class ScannerSession implements CameraManager.Listener, Scanner.ApiSearchListener {
	public static final String TAG = "ScannerSession";
	private Scanner scanner = null;
	private Listener listener;
	private int preview_width;
	private int preview_height;
	private boolean running = false;
	private boolean snap_requested = false;
	private Result _result = null;
	private int _losts = 0;

	// default options: offline image recognition only.
	protected int options = Result.Type.IMAGE;

	/* Interface that must be implemented by the calling Activity. */
	public static interface Listener extends Scanner.ApiSearchListener {
		/* Notifies the caller that a scan has ended. If a barcode was successfully
		 * decoded, or an image from the offline cache recognized, result will be
		 * non-null. Otherwise, it is null.
		 */
		public void onScanComplete(Result result);
	}

	/* Constructor. Requires:
	 * - a parent activity
	 * - a listener to notify
	 * - a fullscreen SurfaceView to display camera preview.
	 */
	public ScannerSession(Activity parent, Listener listener, SurfaceView preview) throws MoodstocksError {
		this.listener = listener;
		this.scanner = Scanner.get();
		OrientationListener.init(parent);
		OrientationListener.get().enable();
		boolean camera_success = CameraManager.get().start(this, preview, parent.getWindowManager());
		if (!camera_success) {
			parent.finish();
		}
	}

	/* Set the operations you want the scan() function to perform,
	 * among offline image recognition and barcode decoding.
	 * `options` must be a list of bitwise-or separated options
	 * chosen among Result.Type, e.g:
	 * Result.type.IMAGE | Result.Type.QRCODE if you choose to
	 * perform offline image recognition and QR-Codes decoding.
	 */
	public void setOptions(int options) {
		this.options = options;
	}

	/* Performs a search in the local cache, as well as
	 * barcode decoding, according to the options previously set.
	 */
	private void scan(Image qry) {
		qry.retain();
		Result result = null;
		MoodstocksError err = null;

		//----------
		// LOCKING
		//----------
		try {
			boolean lock = false;
			if (_result != null && _losts < 2) {
				int found = 0;
				int _type = _result.getType();
				String _value = _result.getValue();
				if (_type == Result.Type.IMAGE) {
					found = scanner.match(qry, _value) ? 1 : -1;
				}
				else if (_type == Result.Type.QRCODE) {
					Result bar = scanner.decode(qry, Result.Type.QRCODE);
					if (bar != null) {
						found = bar.getValue().equals(_value) ? 1 : -1;
					}
					else {
						found = -1;
					}
				}

				if (found == 1) {
					lock = true;
					_losts = 0;
				}
				else if (found == -1) {
					_losts++;
					lock = (_losts >= 2) ? false : true;
				}
			}
			if (lock) {
				result = _result;
			}
		} catch (MoodstocksError e) {
			e.log();
		}

		//---------------
		// IMAGE SEARCH
		//---------------
		try {
			if (result == null && ((options & Result.Type.IMAGE) != 0)) {
				result = scanner.search(qry);
				if (result != null) {
					_losts = 0;
				}
			}
		} catch (MoodstocksError e) {
			if (e.getErrorCode() != MoodstocksError.Code.EMPTY) err = e;
		}

		//-------------------
		// BARCODE DECODING
		//-------------------
		try {
			if (result == null && err == null &&
					((options & (Result.Type.QRCODE|Result.Type.EAN13|Result.Type.EAN8)) != 0)) {
				result = scanner.decode(qry, options);
				if (result != null) {
					_losts = 0;
				}
			}
		} catch (MoodstocksError e) {
			err = e;
		}

		//----------------
		// LOCKING UPDATE
		//----------------
		_result = result;

		//------------------
		// RESULTS ANALYSIS
		//------------------
		qry.release();
		if (err != null) {
			err.log();
		}
		else {
			listener.onScanComplete(result);
		}
		if (running)
			CameraManager.get().requestNewFrame();
	}

	/* Launch an online search on the next frame.
	 * Returns false if the operation could not be performed,
	 * because either the session is paused or a previous call
	 * to snap has not terminated yet.
	 */
	public boolean snap() {
		if (running && !snap_requested) {
			snap_requested = true;
			return true;
		}
		return false;
	}

	/* Cancel previous call to snap().
	 * Returns false if the operation could not be performed,
	 * because either the session is paused or there is no
	 * online search currently running.
	 */
	public boolean cancel() {
		scanner.apiSearchCancel();
		if (running && snap_requested) {
			CameraManager.get().requestNewFrame();
			snap_requested = false;
			return true;
		}
		snap_requested = false;
		return false;
	}

	/* Start/restart scanning.
	 * Returns false if the scanner
	 * session was already running.
	 */
	public boolean resume() {
		if (!running) {
			_result = null;
			_losts = 0;
			running = true;
			CameraManager.get().requestNewFrame();
			return true;
		}
		return false;
	}

	/* Pause scanning.
	 * Returns false if the scanner
	 * session was already paused.
	 */
	public boolean pause() {
		if (running) {
			running = false;
			return true;
		}
		return false;
	}

	/* close the session */
	public void close() {
		pause();
		cancel();
		OrientationListener.get().disable();
		CameraManager.get().stop();
	}

	//------------------------
	// CameraManager.Listener
	//------------------------
	@Override
	public void onPreviewSizeFound(int w, int h) {
		this.preview_width = w;
		this.preview_height = h;
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		if (running) {
			if (snap_requested) {
				// wait for focus
				if (CameraManager.get().isFocussed()) {
					scanner.apiSearch(this, new Image(data, preview_width, preview_height, preview_width, OrientationListener.get().getOrientation()));
				}
				else {
					CameraManager.get().requestFocus();
					CameraManager.get().requestNewFrame();
				}
			}
			else {
				scan(new Image(data, preview_width, preview_height, preview_width, OrientationListener.get().getOrientation()));
			}
		}
	}

	//---------------------------
	// Scanner.ApiSearchListener
	//---------------------------

	@Override
	public void onApiSearchStart() {
		listener.onApiSearchStart();
	}

	@Override
	public void onApiSearchComplete(Result result) {
		snap_requested = false;
		if (result == null) {
			listener.onApiSearchComplete(null);
		}
		else {
			listener.onApiSearchComplete(result);
		}
		if (running)
			CameraManager.get().requestNewFrame();
	}

	@Override
	public void onApiSearchFailed(MoodstocksError e) {
		snap_requested = false;
		listener.onApiSearchFailed(e);
		if (running)
			CameraManager.get().requestNewFrame();
	}

}
