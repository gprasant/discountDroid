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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;

/* Wrapper class around the camera. */
public class CameraManager implements SurfaceHolder.Callback, Camera.PreviewCallback {

	public static interface Listener extends Camera.PreviewCallback {
		/* to notify the listener of the camera frames size */
		public void onPreviewSizeFound(int w, int h);
	}

	public static final String TAG = "CameraManager";
	private static CameraManager instance = null;
	private Listener listener;
	private Camera cam;
	private SurfaceHolder preview;
	private AutoFocusManager focus_manager;
	private List<Size> banned;
	private WindowManager wm;

	private int preview_width;
	private int preview_height;
	private byte[] buffer;

	private boolean frame_requested = false;
	private boolean ready = false;

	private CameraManager() {
		super();
		banned = new ArrayList<Size>();
		banned.clear();
	}

	/* Singleton accessor */
	public static CameraManager get() {
		if (CameraManager.instance == null) {
			synchronized(CameraManager.class) {
				if (CameraManager.instance == null) {
					CameraManager.instance = new CameraManager();
				}
			}
		}
		return CameraManager.instance;
	}

	/* Start the camera.
	 * Parameters:
	 * - A listener to receive frames
	 * - A *fullscreen* SurfaceView to display the preview
	 * - A WindowManager to get the screen dimensions.
	 */
	public boolean start(Listener l, SurfaceView surface, WindowManager wm) {
		this.ready = false;
		this.frame_requested = false;
		this.listener = l;
		this.wm = wm;
		this.cam = getCameraInstance();
		if (this.cam == null) {
			Log.e(TAG, "ERROR: Could not access camera");
			return false;
		}
		preview = surface.getHolder();
		preview.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		preview.addCallback(this);
		findBestPreviewSize();
		cam.setPreviewCallback(this);
		// adapt preview orientation or portrait mode
		cam.setDisplayOrientation(90);
		focus_manager = new AutoFocusManager(cam);
		frame_requested = false;
		return true;
	}

	/* Stops the camera and preview */
	public void stop() {
		focus_manager.stop();
		if (cam != null) {
			cam.stopPreview();
			cam.setPreviewCallback(null);
			cam.cancelAutoFocus();
			cam.release();
			cam = null;
		}
	}

	/* Asks for a new frame to be delivered
	 * to the listener.
	 */
	public void requestNewFrame() {
		if (!ready)
			frame_requested = true;
		else
			cam.addCallbackBuffer(buffer);
	}

	/* check if the AutoFocusManager is currently
	 * focussing, or if the image is already focussed.
	 */
	public boolean isFocussed() {
		return focus_manager.isFocussed();
	}

	/* Interrupts the AutoFocusManager loop and
	 * requests an autofocus immediately if it is
	 * necessary.
	 */
	public void requestFocus() {
		focus_manager.requestFocus();
	}

	private static Camera getCameraInstance(){
		Camera c = null;
		try {
			c = Camera.open(); // attempt to get a Camera instance
		}
		catch (Exception e){
			// camera is unavailable, return null
		}
		return c;
	}

	// compute best preview size: highest possible
	// with ratio within 10% of screen resolution
	private void findBestPreviewSize() {
		Parameters params  = cam.getParameters();
		// get screen ratio:
		Display display = wm.getDefaultDisplay();
		float ratio = (float)display.getHeight()/(float)display.getWidth();
		// available preview sizes:
		List<Size> prev_sizes = params.getSupportedPreviewSizes();
		for (Size s : banned) {
			prev_sizes.remove(s);
		}
		int best_w = 0;
		int best_h = 0;
		for (Size s : prev_sizes) {
			int w = s.width;
			int h = s.height;
			if (w > 1280 || h > 1280) continue;
			float r = (float)w/(float)h;
			if (((r-ratio)*(r-ratio))/(ratio*ratio) < 0.01 && w > best_w) {
				best_w = w;
				best_h = h;
			}
		}
		// nothing found with good ratio? take biggest.
		// should rarely (never?) happen.
		if (best_w == 0) {
			for (Size s : prev_sizes) {
				int w = s.width;
				if (w > best_w) {
					best_w = w;
					best_h = s.height;
				}
			}
		}
		// set the values
		preview_width = best_w;
		preview_height = best_h;
		params.setPreviewSize(preview_width, preview_height);
		// we force the preview format to NV21
		params.setPreviewFormat(ImageFormat.NV21);
		cam.setParameters(params);
		// pre-allocate buffer of size #pixels x 3/2
		// as NV21 uses #pixels for grayscale and twice
		// #pixels/4 for chroma.
		buffer = new byte[preview_width*preview_height*3/2];
		// notify Listener
		listener.onPreviewSizeFound(preview_width, preview_height);
	}

	//------------------------
	// SurfaceHolder.Callback
	//------------------------
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		// void implementation
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		try {
			cam.setPreviewDisplay(holder);
		} catch (IOException e) {
			Log.e(TAG, "ERROR: Could not start preview");
		}
		cam.startPreview();
		focus_manager.start();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// void implementation
	}

	/*******************************************************************
	 * UGLY HACK due to some problems on older devices and custom ROMs
	 * (e.g. HTC Desire Bravo + Cyanogenmod 7.1):
	 * In some cases, Camera.getSupportedPreviewSizes() returns sizes
	 * that are *not* available.
	 * This checks that the chosen resolution is indeed available, and
	 * chooses another one if it's not.
	 *******************************************************************/
	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		if (data.length != 3*preview_width*preview_height/2) {
			Size s = cam.new Size(preview_width,preview_height);
			banned.add(s);
			findBestPreviewSize();
		}
		else {
			cam.setPreviewCallbackWithBuffer(listener);
			ready = true;
			if (frame_requested)
				requestNewFrame();
		}
	}

}
