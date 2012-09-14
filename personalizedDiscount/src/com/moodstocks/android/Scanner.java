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

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.os.AsyncTask;
import java.lang.RuntimeException;
import java.lang.ref.WeakReference;

import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;

/* Scanner class.
 * The scanner offers an unified interface to perform:
 * - local database synchronization with offline content,
 * - offline search over the local database of image records,
 * - remote search on Moodstocks API,
 * - 1D/2D barcode decoding.
 */
public final class Scanner {

	public static final String TAG = "Scanner";
	private static Scanner instance = null;
	private boolean syncing = false;
	private ApiSearcher api_searcher = null;
	private List<WeakReference<SyncListener>> extra_listeners = null;

	//--------------------------------
	// Moodstocks API key/secret pair
	//--------------------------------
	static final String API_KEY = "9kYxSDgOhWoMLoV8RlbZ";
	static final String API_SECRET = "no1E8LOeJJUbK65m";

//    static final String API_KEY = "mHCZ8xlmFZCTt0wSjA1W";
//	static final String API_SECRET = "fFMN7PrsBVbb1hIc";


	// interface that must be implemented by the caller of sync() function
	public static interface SyncListener{
		/* notifies the caller that a Sync has been launched */
		public void onSyncStart();
		/* notifies the caller that a Sync has successfully ended */
		public void onSyncComplete();
		/* notifies the caller that a Sync has failed with the given error */
		public void onSyncFailed(MoodstocksError e);
		/* notifies the caller of the Sync progression.
		 * `total` is the total number of images that will be synced.
		 * `current` is the current number of synced images.
		 */
		public void onSyncProgress(int total, int current);
	}

	// interface that must be implemented by the caller of ApiSearch() function
	public static interface ApiSearchListener{
		/* notifies the caller that an API Search has been launched */
		public void onApiSearchStart();
		/* notifies the caller that an API Search has successfully ended.
		 * result will be null if nothing was found.
		 */
		public void onApiSearchComplete(Result result);
		/* notifies the caller that an API Search has failed with the given error */
		public void onApiSearchFailed(MoodstocksError e);
	}

	private int ptr = 0;

	static {
		Loader.load();
		if (isCompatible()) init();
	}

	private Scanner() {
		super();
		this.extra_listeners = new ArrayList<WeakReference<SyncListener>>();
	}

	/* singleton accessor */
	public static Scanner get()
			throws MoodstocksError {
		if (Scanner.instance == null) {
			synchronized(Scanner.class) {
				if (Scanner.instance == null) {
					Scanner.instance = new Scanner();
					if (isCompatible()) Scanner.instance.initialize();
				}
			}
		}
		return Scanner.instance;
	}

	/* destructor */
	public void destroy() {
		Scanner.instance.destruct();
		Scanner.instance = null;
	}

	/* Open the scanner and connect it to the database file.
	 *****************************************************************
	 * It also checks **at runtime** that the device is compatible
	 * with Moodstocks SDK, aka that it runs Android 2.3 or over.
	 * If it's not the case, it throws a RuntimeException.
	 * We advise you to design your applications so it won't try to
	 * use the scanner in such case, as the SDK will probably crash.
	 *****************************************************************
	 */
	public void open(Context context, String filename)
			throws MoodstocksError {
		if (!isCompatible()) {
			throw new RuntimeException("DEVICE IS NOT COMPATIBLE WITH MOODSTOCKS SDK");
		}
		String path = context.getFilesDir().getAbsolutePath();
		this.open(path + "/" + filename, API_KEY, API_SECRET);
	}

	/* close the scanner and disconnect it from the database file */
	public native void close()
			throws MoodstocksError;


	/* Synchronize the local database with offline content from Moodstocks API
	 * This method runs in the background so you can safely call it from the UI thread.
	 * Caller must implement Scanner.SyncListener interface. It will receive notifications
	 * for this Sync only.
	 * Returns false if a sync is already running.
	 * NOTE: this method requires an Internet connection.
	 */
	public boolean sync(final Scanner.SyncListener listener) {
		if (!syncing) {
			new Syncer(listener).execute(this);
			return true;
		}
		return false;
	}

	/* Add an extra SyncListener to the scanner. It will be used
	 * every time a new sync is launched until it is removed.
	 */
	public void addExtraSyncListener(SyncListener l) {
		for (WeakReference<SyncListener> listener : this.extra_listeners) {
			if (listener.get() == l)
				return;
		}
		this.extra_listeners.add(new WeakReference<SyncListener>(l));
	}

	/* Removes an extra SyncListener. It won't be notified anymore
	 * on any sync.
	 */
	public void removeExtraSyncListener(SyncListener l) {
		for (WeakReference<SyncListener> listener : this.extra_listeners) {
			if (listener.get() == l)
				this.extra_listeners.remove(listener);
		}
	}

	/* Returns true if the scanner is currently syncing, false otherwise */
	public boolean isSyncing() {
		return syncing;
	}

	/* Perform a remote image search on Moodstocks API
	 * This method runs in the background so you can safely call it from the UI thread.
	 * Caller must implement Scanner.ApiSearchListener to receive notifications and result.
	 * NOTE: this method requires an Internet connection.
	 */
	public void apiSearch(Scanner.ApiSearchListener listener, Image qry) {
		api_searcher = new ApiSearcher(listener, qry);
		api_searcher.execute(this);
	}

	/* Cancel any pending API Search. */
	public void apiSearchCancel() {
		if (api_searcher != null && api_searcher.getStatus() == AsyncTask.Status.RUNNING) {
			api_searcher.cancel(true);
		}
	}

	/* Return the total number of images recorded into the local database */
	public native int count()
			throws MoodstocksError;

	/* Return an array of all images IDs found into the local database */
	public native List<String> info()
			throws MoodstocksError;

	/* performs an offline image search among the local database*/
	public native Result search(Image qry)
			throws MoodstocksError;

	/* performs barcode decoding on the image, among the given formats */
	public native Result decode(Image qry, int formats)
			throws MoodstocksError;

	/* Match a query image against a reference from the local database */
	public native boolean match(Image qry, String uid)
			throws MoodstocksError;

	/* check compatibility (Android level >= 2.3) */
	public static boolean isCompatible() {
		return (VERSION.SDK_INT >= VERSION_CODES.GINGERBREAD);
	}

	/**************************
	 * private native methods *
	 **************************/
	/* performs a *synchronous* synchronization */
	private native void sync(AsyncTask<Scanner, Integer, MoodstocksError> task)
			throws MoodstocksError;

	/* performs a *synchronous* API search */
	private native String apiSearch(Image qry)
			throws MoodstocksError;

	private static native void init();

	private native void initialize()
			throws MoodstocksError;

	private native void destruct();

	private native void open(String path, String key, String secret)
			throws MoodstocksError;

	/************************************
	 * Inner classes used to make       *
	 * synchronous methods asynchronous *
	 ************************************/

	/* inner class to allow Asynchronous syncing */
	private class Syncer extends AsyncTask<Scanner, Integer, MoodstocksError> {
		private WeakReference<SyncListener> listener;

		protected Syncer(SyncListener l) {
			super();
			for (WeakReference<SyncListener> listener : extra_listeners) {
				if (listener.get() == l)
					l = null;
				// clean dead references
				if (listener.get() == null)
					extra_listeners.remove(listener);
			}
			this.listener = new WeakReference<SyncListener>(l);
		}

		@Override
		protected void onPreExecute() {
			if (listener.get() != null)
				listener.get().onSyncStart();
			for (WeakReference<SyncListener> l : extra_listeners) {
				SyncListener listener = l.get();
				if (listener != null)
					listener.onSyncStart();
			}
			syncing = true;
		}

		@Override
		protected MoodstocksError doInBackground(Scanner... s) {
			try {
				s[0].sync(this);
				return null;
			} catch (MoodstocksError e) {
				return e;
			}
		}

		@Override
		protected void onPostExecute(MoodstocksError e) {
			syncing = false;
			if (e == null) {
				if (listener.get() != null)
					listener.get().onSyncComplete();
				for (WeakReference<SyncListener> l : extra_listeners) {
					SyncListener listener = l.get();
					if (listener != null)
						listener.onSyncComplete();
				}
			}
			else {
				if (listener.get() != null)
					listener.get().onSyncFailed(e);
				for (WeakReference<SyncListener> l : extra_listeners) {
					SyncListener listener = l.get();
					if (listener != null)
						listener.onSyncFailed(e);
				}
			}
		}

		@Override
		protected void onProgressUpdate(Integer... p) {
			if (listener.get() != null)
				listener.get().onSyncProgress(p[0], p[1]);
			for (WeakReference<SyncListener> l : extra_listeners) {
				SyncListener listener = l.get();
				if (listener != null) {
					listener.onSyncProgress(p[0], p[1]);
				}
			}
		}

		// called from native sync.
		@SuppressWarnings("unused")
		protected void syncProgress(int total, int current) {
			publishProgress(total, current);
		}
	}

	/* inner class to perform asynchronous API searches */
	private class ApiSearcher extends AsyncTask<Scanner, Void, MoodstocksError> {
		private ApiSearchListener listener;
		private Image qry;
		private String r = null;

		protected ApiSearcher(ApiSearchListener listener, Image qry) {
			super();
			this.listener = listener;
			this.qry = qry;
			qry.retain();
		}

		@Override
		protected void onPreExecute() {
			listener.onApiSearchStart();
		}

		@Override
		protected MoodstocksError doInBackground(Scanner... s) {
			try {
				r = s[0].apiSearch(qry);
				return null;
			} catch (MoodstocksError e) {
				return e;
			}
		}

		@Override
		protected void onPostExecute(MoodstocksError e) {
			qry.release();
			if (e == null) {
				if (r == null)
					listener.onApiSearchComplete(null);
				else
					listener.onApiSearchComplete(new Result(Result.Type.IMAGE, r));
			}
			else {
				listener.onApiSearchFailed(e);
			}
		}

		@Override
		protected void onCancelled() {
			qry.release();
		}
	}
}
