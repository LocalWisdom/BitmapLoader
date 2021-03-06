package org.collegelabs.library.bitmaploader.views;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.concurrent.Future;

import org.collegelabs.library.bitmaploader.BitmapLoader;
import org.collegelabs.library.bitmaploader.Constants;
import org.collegelabs.library.bitmaploader.LoadDiskBitmap;
import org.collegelabs.library.bitmaploader.LoadNetworkBitmap;
import org.collegelabs.library.bitmaploader.caches.DiskCache;
import org.collegelabs.library.bitmaploader.caches.StrongBitmapCache;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

public class AsyncImageView extends ImageView {

	/** Possible locations to load the image from */
	public enum SourceType{
		Network,
		Disk,
	}
	
	/** Current String representation of a URL to load the image from */
	private String mUrl = "";

	/** 
	 * Need to keep track of the current request
	 */
	private WeakReference<Future<?>> mRequest = null;

	/** Bitmap to draw while the real one is loading */
	private Bitmap defaultBitmap = null;

	/** Delay before posting bitmap to UI thread */
	private long mDelay = 300l; 
	
	/** Boolean indicating if the request finished loading yet */
	private boolean isLoaded = false;
	
	/*
	 * Inherited Constructors
	 */
	public AsyncImageView(Context context) {
		this(context, null);
	}

	public AsyncImageView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public AsyncImageView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	/*
	 * Public
	 */
	
	public void setImageUrl(String url, BitmapLoader loader){
		if(url == null) throw new IllegalArgumentException("url cannot be null");
		
		if(!mUrl.equals(url)){
			mUrl = url;
			loadUrl(loader);
		}else{
			if(Constants.DEBUG) Log.d(Constants.TAG, "[AsyncImageView] urls match, not loading: "+url);
		}
	}
	
	
	/**
	 * Called upon completion of asyncLoadBitmap(BitmapLoader, SourceType, String)
	 * 
	 * @param bitmap The final bitmap to display
	 * @param pUrl The url representing this bitmap
	 */
	public void asyncCompleted(final Bitmap bitmap, final String pUrl){
		if(Constants.DEBUG) Log.d(Constants.TAG, "[AsyncImageView] asyncCompleted: "+pUrl);

		if(pUrl == null) throw new IllegalArgumentException("pUrl can't be null");
		
		postDelayed(new Runnable() {
			@Override public void run() {
				if(!mUrl.equals(pUrl)){
					if(Constants.DEBUG) Log.d(Constants.TAG, "[AsyncImageView] mUrl != pUrl: "+mUrl+", "+pUrl);
					return;
				}
				
				isLoaded = true;
				Resources resources = getResources();
				Drawable[] layers = {new BitmapDrawable(resources, defaultBitmap), new BitmapDrawable(resources,bitmap)};
				TransitionDrawable transition = new TransitionDrawable(layers);
				transition.startTransition(300);
				setImageDrawable(transition);
				
				if(mListener!=null) mListener.onStateChanged(AsyncImageView.this, IStateChangeListener.State.LOADING_COMPLETED);
			}
		}, mDelay);
	}

	/**
	 * 
	 * @param pUrl
	 */
	public void asyncFailed(final String pUrl){
		if(Constants.DEBUG) Log.w(Constants.TAG, "[AsyncImageView] asyncFailed: "+pUrl);
		
		if(pUrl == null) throw new IllegalArgumentException("pUrl can't be null");
		//Should already be the defaultDrawable, TODO add a failure drawable

		if(mListener!=null) mListener.onStateChanged(AsyncImageView.this, IStateChangeListener.State.LOADING_FAILED);
	}
	
	public String getImageUrl(){
		return mUrl;
	}
	
	public void setDefaultBitmap(Bitmap bitmap){
		defaultBitmap = bitmap;
		if(!isLoaded){
			setImageBitmap(defaultBitmap);
		}
	}

	public void setDelay(long delayInMs){
		mDelay = delayInMs;
	}
	
	/*
	 * Protected
	 */
	
	protected Future<?> asyncLoadBitmap(BitmapLoader loader, SourceType source, String url){
		switch(source){
		case Network:{
			return loader.getInternetThread().submit(new LoadNetworkBitmap(this, url, loader.getCachePolicy(), loader.getBitmapCache()));	
		}
		case Disk:{
			return loader.getBitmapThread().submit(new LoadDiskBitmap(this, url, loader.getCachePolicy(), loader.getBitmapCache()));		
		}
		default:
			if(Constants.DEBUG) Log.w(Constants.TAG,"[AsyncImageView] unknown source type: "+source);
			return null;
		}			
	}
	
	/*
	 * Private
	 */
	
	private void cancelCurrentRequest(){
		Future<?> request = (mRequest != null) ? mRequest.get() : null;
		if(request != null){
			request.cancel(true);
		}
		mRequest = null;
		isLoaded = false;
	}

	private void loadUrl(BitmapLoader loader){
		cancelCurrentRequest();
		
		StrongBitmapCache bitmapCache = loader.getBitmapCache();
		DiskCache diskCache = loader.getCachePolicy();
		
		Bitmap bitmap = bitmapCache.get(mUrl);
		
		if(bitmap!=null){
			isLoaded = true;
			setImageBitmap(bitmap);
			if(Constants.DEBUG) Log.d(Constants.TAG,"[AsyncImageView] Cache hit: "+mUrl);
			if(mListener!=null) mListener.onStateChanged(AsyncImageView.this, IStateChangeListener.State.LOADING_COMPLETED);
			return;
		}

		if(Constants.DEBUG) Log.d(Constants.TAG,"[AsyncImageView] Cache miss: "+mUrl);
		
		setImageBitmap(defaultBitmap);

		File file = diskCache.getFile(mUrl);

		SourceType source = file.exists() ? SourceType.Disk : SourceType.Network;
		mRequest = new WeakReference<Future<?>>(asyncLoadBitmap(loader, source, mUrl));
		
		if(mListener!=null) mListener.onStateChanged(AsyncImageView.this, IStateChangeListener.State.LOADING_STARTED);
	}
	
	private IStateChangeListener mListener=null;
	public void setStateChangeListener(IStateChangeListener listener){
		mListener = listener;
	}
	
	public static interface IStateChangeListener{
		public static enum State{
			LOADING_STARTED,
			LOADING_COMPLETED,
			LOADING_FAILED
		}
		
		public void onStateChanged(AsyncImageView view, IStateChangeListener.State newState);
	}
}
