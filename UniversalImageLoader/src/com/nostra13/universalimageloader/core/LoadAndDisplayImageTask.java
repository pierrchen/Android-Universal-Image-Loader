package com.nostra13.universalimageloader.core;


import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;

import junit.framework.Assert;

import android.graphics.Bitmap;
import android.os.Handler;
import android.util.Log;
import android.widget.ImageView;

import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.assist.ImageLoadingListener;
import com.nostra13.universalimageloader.utils.FileUtils;

/**
 * Presents load'n'display image task. Used to load image from Internet or file system, decode it to {@link Bitmap}, and
 * display it in {@link ImageView} through {@link DisplayBitmapTask}.
 * 
 * @author Sergey Tarasevich (nostra13[at]gmail[dot]com)
 * @see ImageLoaderConfiguration
 * @see ImageLoadingInfo
 */
final class LoadAndDisplayImageTask implements Runnable {

	private static final String LOG_START_DISPLAY_IMAGE_TASK = "Start load & display image task [%s]";
	private static final String LOG_LOAD_IMAGE_FROM_INTERNET = "Load image from Internet [%s]";
	private static final String LOG_LOAD_IMAGE_FROM_DISC_CACHE = "Load image from disc cache [%s]";
	private static final String LOG_CACHE_IMAGE_IN_MEMORY = "Cache image in memory [%s]";
	private static final String LOG_CACHE_IMAGE_ON_DISC = "Cache image on disc [%s]";
	private static final String LOG_DISPLAY_IMAGE_IN_IMAGEVIEW = "Display image in ImageView [%s]";

	private static final int ATTEMPT_COUNT_TO_DECODE_BITMAP = 3;

	private final ImageLoaderConfiguration configuration;
	private final ImageLoadingInfo imageLoadingInfo;
	private final Handler handler;
	
	//feature on-off flag - as the server don't always support partial get. This should alwasy turn off
	private boolean savePartialAndResume = false;

	public LoadAndDisplayImageTask(ImageLoaderConfiguration configuration, ImageLoadingInfo imageLoadingInfo, Handler handler) {
		this.configuration = configuration;
		this.imageLoadingInfo = imageLoadingInfo;
		this.handler = handler;
	}

	@Override
	public void run() {

		if (ImageLoaderConfiguration.loggingEnabled) Log.i(ImageLoader.TAG, String.format(LOG_START_DISPLAY_IMAGE_TASK, imageLoadingInfo.memoryCacheKey));
		
		//TODO: just for make downloading slow
//		try {
//			Thread.sleep(10000);
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//
//		}
		
		if (checkTaskIsNotActual()) return;
		Bitmap bmp = loadBitmap();

		if (bmp == null) return;

		if (checkTaskIsNotActual()) return;
		if (imageLoadingInfo.options.isCacheInMemory()) {
			if (ImageLoaderConfiguration.loggingEnabled) Log.i(ImageLoader.TAG, String.format(LOG_CACHE_IMAGE_IN_MEMORY, imageLoadingInfo.memoryCacheKey));

			configuration.memoryCache.put(imageLoadingInfo.memoryCacheKey, bmp);
		}

		if (checkTaskIsNotActual()) return;
		if (ImageLoaderConfiguration.loggingEnabled) Log.i(ImageLoader.TAG, String.format(LOG_DISPLAY_IMAGE_IN_IMAGEVIEW, imageLoadingInfo.memoryCacheKey));

		DisplayBitmapTask displayBitmapTask = new DisplayBitmapTask(bmp, imageLoadingInfo.imageView, imageLoadingInfo.listener);
		handler.post(displayBitmapTask);
	}

	/**
	 * Check whether the image URL of this task matches to image URL which is actual for current ImageView at this
	 * moment and fire {@link ImageLoadingListener#onLoadingCancelled()} event if it doesn't.
	 */
	boolean checkTaskIsNotActual() {
		String currentCacheKey = ImageLoader.getInstance().getLoadingUrlForView(imageLoadingInfo.imageView);
		// Check whether memory cache key (image URL) for current ImageView is actual. 
		// If ImageView is reused for another task then current task should be cancelled.
		boolean imageViewWasReused = !imageLoadingInfo.memoryCacheKey.equals(currentCacheKey);
		if (imageViewWasReused) {
			handler.post(new Runnable() {
				@Override
				public void run() {
					imageLoadingInfo.listener.onLoadingCancelled();
				}
			});
		}
		return imageViewWasReused;
	}
	
	//For Test only
	private boolean needSlowDown(String url){
		
		return url.contains("495") ||
			   url.contains("517") ||
			   url.contains("556") ||
			   url.contains("535") ||
			   url.contains("590") ;
			   
	}

	
	
	
	
	private boolean fileExsitAndComplete(File imageFile){
		
		if(!savePartialAndResume){
			return imageFile.exists();
		} else {
			return imageFile.exists() && !ImageLoader.isPartialDownloaded(imageFile);
		}
	}
	private Bitmap loadBitmap() {
		File imageFile = configuration.discCache.get(imageLoadingInfo.url);

		Bitmap bitmap = null;
		try {
			if (fileExsitAndComplete(imageFile)) {
					if (ImageLoaderConfiguration.loggingEnabled) Log.i(ImageLoader.TAG, String.format(LOG_LOAD_IMAGE_FROM_DISC_CACHE, imageLoadingInfo.memoryCacheKey));
					Bitmap b = decodeImage(imageFile.toURL());
					if (b != null) {
						return b;
					} else {
						//When decode error, go and download the image again from the Web
						Log.w(ImageLoader.TAG, ">>>>error when decoding image file on cache " + imageFile + " partial downloaded?");
					}
			}

			// Load image from Web & the file could possibly be partially downloaded
			if (ImageLoaderConfiguration.loggingEnabled) Log.i(ImageLoader.TAG, String.format(LOG_LOAD_IMAGE_FROM_INTERNET, imageLoadingInfo.memoryCacheKey));

			URL imageUrlForDecoding;
			if (imageLoadingInfo.options.isCacheOnDisc()) {
				saveImageOnDisc(imageFile);
				configuration.discCache.put(imageLoadingInfo.url, imageFile);
				if(savePartialAndResume){
					if(ImageLoader.isPartialDownloaded(imageFile)){
						ImageLoader.clearPartialFlag(imageFile);
					}
				}
				if (ImageLoaderConfiguration.loggingEnabled) Log.i(ImageLoader.TAG, String.format(LOG_CACHE_IMAGE_ON_DISC, imageLoadingInfo.memoryCacheKey));
				
				imageUrlForDecoding = imageFile.toURL();

			} else {
				imageUrlForDecoding = new URL(imageLoadingInfo.url);
			}

			bitmap = decodeImage(imageUrlForDecoding);
		} catch (IOException e) {
			Log.e(ImageLoader.TAG, e.getMessage(), e);
			fireImageLoadingFailedEvent(FailReason.IO_ERROR);
			if (imageFile.exists()) {
				imageFile.delete();
			}
		} catch (InterruptedException e){
			Log.e(ImageLoader.TAG, e.getMessage(), e);
			if(savePartialAndResume){
				if(imageFile.exists()){
					ImageLoader.setAsPartial(imageFile);
				}
			}else{
				if(imageFile.exists()){
					Log.d(ImageLoader.TAG,"delete the partial download file");
					imageFile.delete();
				}
			}
		}
		catch (OutOfMemoryError e) {
			Log.e(ImageLoader.TAG, e.getMessage(), e);
			fireImageLoadingFailedEvent(FailReason.OUT_OF_MEMORY);
		} catch (Throwable e) {
			Log.e(ImageLoader.TAG, e.getMessage(), e);
			fireImageLoadingFailedEvent(FailReason.UNKNOWN);
		}
		return bitmap;
	}

	private Bitmap decodeImage(URL imageUrl) throws IOException {
		Bitmap bmp = null;
		ImageDecoder decoder = new ImageDecoder(imageUrl, configuration.downloader, imageLoadingInfo.targetSize, imageLoadingInfo.options.getDecodingType());

		if (configuration.handleOutOfMemory) {
			bmp = decodeWithOOMHandling(decoder);
		} else {
			bmp = decoder.decode();
		}

		decoder = null;
		return bmp;
	}

	private Bitmap decodeWithOOMHandling(ImageDecoder decoder) throws IOException {
		Bitmap result = null;
		for (int attempt = 1; attempt <= ATTEMPT_COUNT_TO_DECODE_BITMAP; attempt++) {
			try {
				result = decoder.decode();
			} catch (OutOfMemoryError e) {
				Log.e(ImageLoader.TAG, e.getMessage(), e);

				switch (attempt) {
					case 1:
						System.gc();
						break;
					case 2:
						configuration.memoryCache.clear();
						System.gc();
						break;
					case 3:
						throw e;
				}
				// Wait some time while GC is working
				try {
					Thread.sleep(attempt * 1000);
				} catch (InterruptedException ie) {
					Log.e(ImageLoader.TAG, ie.getMessage(), ie);
				}
			}
		}
		return result;
	}

	private void saveImageOnDisc(File targetFile) throws MalformedURLException, IOException , InterruptedException {

		InputStream is = null;
		String url = imageLoadingInfo.url;
		
		RandomAccessFile outFile = null;
		try {
			boolean needSlow = needSlowDown(url);
			if(savePartialAndResume && ImageLoader.isPartialDownloaded(targetFile)){
				Log.d(ImageLoader.TAG, ">>>>Partial downloaded file detected, resume download..." + targetFile);
				is = configuration.downloader.getStreamFromNetwrokWithRange(new URL(url), targetFile.length());
			}else{
				is = configuration.downloader.getStream(new URL(url));
			}
			OutputStream os = new BufferedOutputStream(new FileOutputStream(targetFile));
			//somewhere say the socket creation will make the interruptFlag malfunction
			//Log.d(ImageLoader.TAG, "thread downloading 2 " + url + "interrupted:" + Thread.currentThread().isInterrupted());
			try {
				if(savePartialAndResume){
					outFile = new RandomAccessFile(targetFile, "rw");
					//seek to the end of the file
					outFile.seek(targetFile.length());
					FileUtils.copyStream(is, outFile, needSlow, url);
				}else{
					FileUtils.copyStream(is, os , needSlow, imageLoadingInfo.url);
				}
				//Log.i(ImageLoader.TAG , "image of " + url + "was saved");
			} catch (InterruptedException e) {
				os.flush();
				os.close();
				
				if(savePartialAndResume){
					if(outFile != null) {outFile.close();}
				}
				
				Log.d(ImageLoader.TAG, "thread downloading " + url + " was interrupted. " +  
									   " Interrupt Flag " + Thread.currentThread().isInterrupted() + 
									   " saved file " + targetFile + " size " + targetFile.length());
				
				throw new InterruptedException("Interrupted when downloading" + url);
			} finally {
				if(is != null) {is.close();}
				os.close();
				if(outFile != null){outFile.close();}
			}
		} catch (SocketTimeoutException ex) {
			throw new IOException("SocketTimeoutException when downloading" + url);
		} catch (EOFException ex) {
			// https://code.google.com/p/google-http-java-client/issues/detail?id=116 
			throw new IOException("EOFException when downloading" + url);
		} finally {
			if (is != null)
				is.close();

		}
	}

	private void fireImageLoadingFailedEvent(final FailReason failReason) {
		handler.post(new Runnable() {
			@Override
			public void run() {
				imageLoadingInfo.listener.onLoadingFailed(failReason);
			}
		});
	}
}
