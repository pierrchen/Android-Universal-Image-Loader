package com.nostra13.universalimageloader.core;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Handler;
import android.util.Log;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;

import com.nostra13.universalimageloader.cache.disc.DiscCacheAware;
import com.nostra13.universalimageloader.cache.memory.MemoryCacheAware;
import com.nostra13.universalimageloader.core.assist.ImageLoadingListener;
import com.nostra13.universalimageloader.core.assist.ImageSize;
import com.nostra13.universalimageloader.core.assist.MemoryCacheKeyUtil;
import com.nostra13.universalimageloader.core.assist.SimpleImageLoadingListener;

/**
 * Singletone for image loading and displaying at {@link ImageView ImageViews}<br />
 * <b>NOTE:</b> {@link #init(ImageLoaderConfiguration)} method must be called before any other method.
 * 
 * @author Sergey Tarasevich (nostra13[at]gmail[dot]com)
 */
public class ImageLoader {

	public static final String TAG = ImageLoader.class.getSimpleName();

	private static final String ERROR_WRONG_ARGUMENTS = "Wrong arguments were passed to displayImage() method (ImageView reference are required)";
	private static final String ERROR_NOT_INIT = "ImageLoader must be init with configuration before using";
	private static final String ERROR_INIT_CONFIG_WITH_NULL = "ImageLoader configuration can not be initialized with null";
	private static final String LOG_LOAD_IMAGE_FROM_MEMORY_CACHE = "Load image from memory cache [%s]";

	private ImageLoaderConfiguration configuration;
	private ThreadPoolExecutor cachedImageLoadingExecutor;
	private ThreadPoolExecutor imageLoadingExecutor;
	private ImageLoadingListener emptyListener;

	//tasks that have been submitted but not finished.
	private Queue<String> submittedTaskQueue = new LinkedList<String>();
	
	//URl=>Futures pairs for the submitted tasks
	private Map<String , Future<?>> submittedTaskMap = Collections.synchronizedMap(new WeakHashMap<String , Future<?>>());
	private Map<ImageView, String> cacheKeyForImageView = Collections.synchronizedMap(new WeakHashMap<ImageView, String>());

	private volatile static ImageLoader instance;

	/** Returns singletone class instance */
	public static ImageLoader getInstance() {
		if (instance == null) {
			synchronized (ImageLoader.class) {
				if (instance == null) {
					instance = new ImageLoader();
				}
			}
		}
		return instance;
	}

	private ImageLoader() {
		//empty
	}

	/**
	 * initializes imageloader's singletone instance with configuration. Method shoiuld be called <b>once</b> (each
	 * following call will have no effect)<br />
	 * 
	 * @param configuration
	 *            {@linkplain ImageLoaderConfiguration ImageLoader configuration}
	 * @throws IllegalArgumentException
	 *             if <b>configuration</b> parameter is null
	 */
	public synchronized void init(ImageLoaderConfiguration configuration) {
		if (configuration == null) {
			throw new IllegalArgumentException(ERROR_INIT_CONFIG_WITH_NULL);
		}
		if (this.configuration == null) {
			this.configuration = configuration;
			emptyListener = new SimpleImageLoadingListener();
		}
	}

	/**
	 * Adds display image task to execution pool. Image will be set to ImageView when it's turn. <br/>
	 * Default {@linkplain DisplayImageOptions display image options} from {@linkplain ImageLoaderConfiguration
	 * configuration} will be used.<br />
	 * <b>NOTE:</b> {@link #init(ImageLoaderConfiguration)} method must be called before this method call
	 * 
	 * @param url
	 *            Image URL (i.e. "http://site.com/image.png", "file:///mnt/sdcard/image.png")
	 * @param imageView
	 *            {@link ImageView} which should display image
	 * @throws RuntimeException
	 *             if {@link #init(ImageLoaderConfiguration)} method wasn't called before
	 */
	public void displayImage(String url, ImageView imageView) {
		displayImage(url, imageView, null, null);
	}

	/**
	 * Adds display image task to execution pool. Image will be set to ImageView when it's turn.<br />
	 * <b>NOTE:</b> {@link #init(ImageLoaderConfiguration)} method must be called before this method call
	 * 
	 * @param url
	 *            Image URL (i.e. "http://site.com/image.png", "file:///mnt/sdcard/image.png")
	 * @param imageView
	 *            {@link ImageView} which should display image
	 * @param options
	 *            {@linkplain DisplayImageOptions Display image options} for image displaying. If <b>null</b> - default
	 *            display image options
	 *            {@linkplain ImageLoaderConfiguration.Builder#defaultDisplayImageOptions(DisplayImageOptions) from
	 *            configuration} will be used.
	 * @throws RuntimeException
	 *             if {@link #init(ImageLoaderConfiguration)} method wasn't called before
	 */
	public void displayImage(String url, ImageView imageView, DisplayImageOptions options) {
		displayImage(url, imageView, options, null);
	}

	/**
	 * Adds display image task to execution pool. Image will be set to ImageView when it's turn.<br />
	 * Default {@linkplain DisplayImageOptions display image options} from {@linkplain ImageLoaderConfiguration
	 * configuration} will be used.<br />
	 * <b>NOTE:</b> {@link #init(ImageLoaderConfiguration)} method must be called before this method call
	 * 
	 * @param url
	 *            Image URL (i.e. "http://site.com/image.png", "file:///mnt/sdcard/image.png")
	 * @param imageView
	 *            {@link ImageView} which should display image
	 * @param listener
	 *            {@linkplain ImageLoadingListener Listener} for image loading process. Listener fires events only if
	 *            there is no image for loading in memory cache. If there is image for loading in memory cache then
	 *            image is displayed at ImageView but listener does not fire any event. Listener fires events on UI
	 *            thread.
	 * @throws RuntimeException
	 *             if {@link #init(ImageLoaderConfiguration)} method wasn't called before
	 */
	public void displayImage(String url, ImageView imageView, ImageLoadingListener listener) {
		displayImage(url, imageView, null, listener);
	}

	/**
	 * Adds display image task to execution pool. Image will be set to ImageView when it's turn.<br />
	 * <b>NOTE:</b> {@link #init(ImageLoaderConfiguration)} method must be called before this method call
	 * 
	 * @param url
	 *            Image URL (i.e. "http://site.com/image.png", "file:///mnt/sdcard/image.png")
	 * @param imageView
	 *            {@link ImageView} which should display image
	 * @param options
	 *            {@linkplain DisplayImageOptions Display image options} for image displaying. If <b>null</b> - default
	 *            display image options
	 *            {@linkplain ImageLoaderConfiguration.Builder#defaultDisplayImageOptions(DisplayImageOptions) from
	 *            configuration} will be used.
	 * @param listener
	 *            {@linkplain ImageLoadingListener Listener} for image loading process. Listener fires events only if
	 *            there is no image for loading in memory cache. If there is image for loading in memory cache then
	 *            image is displayed at ImageView but listener does not fire any event. Listener fires events on UI
	 *            thread.
	 * @throws RuntimeException
	 *             if {@link #init(ImageLoaderConfiguration)} method wasn't called before
	 */
	public void displayImage(String url, ImageView imageView, DisplayImageOptions options, ImageLoadingListener listener) {
		if (configuration == null) {
			throw new RuntimeException(ERROR_NOT_INIT);
		}
		if (imageView == null) {
			Log.w(TAG, ERROR_WRONG_ARGUMENTS);
			return;
		}
		if (listener == null) {
			listener = emptyListener;
		}
		if (options == null) {
			options = configuration.defaultDisplayImageOptions;
		}

		if (url == null || url.length() == 0) {
			cacheKeyForImageView.remove(imageView);
			if (options.isShowImageForEmptyUrl()) {
				imageView.setImageResource(options.getImageForEmptyUrl());
			} else {
				imageView.setImageBitmap(null);
			}
			return;
		}

		ImageSize targetSize = getImageSizeScaleTo(imageView);
		String memoryCacheKey = MemoryCacheKeyUtil.generateKey(url, targetSize);
		
		Log.d(TAG,"load & display image at " + url);
		//Log.d(TAG,"ImageViewCache:"+imageView + ":" + memoryCacheKey);
		cacheKeyForImageView.put(imageView, memoryCacheKey);

		Bitmap bmp = configuration.memoryCache.get(memoryCacheKey);
		if (bmp != null && !bmp.isRecycled()) {
			if (ImageLoaderConfiguration.loggingEnabled) Log.i(TAG, String.format(LOG_LOAD_IMAGE_FROM_MEMORY_CACHE, memoryCacheKey));
			listener.onLoadingStarted();
			imageView.setImageBitmap(bmp);
			listener.onLoadingComplete();
		} else {
			listener.onLoadingStarted();
			checkExecutors();

			ImageLoadingInfo imageLoadingInfo = new ImageLoadingInfo(url, imageView, targetSize, options, listener);
			LoadAndDisplayImageTask displayImageTask = new LoadAndDisplayImageTask(configuration, imageLoadingInfo, new Handler());
			boolean isImageCachedOnDisc = configuration.discCache.get(url).exists();
			if (isImageCachedOnDisc) {
				cachedImageLoadingExecutor.submit(displayImageTask);
			} else {
				makeSureThreadPoolIsNotTooFull();
				Future<?> future = imageLoadingExecutor.submit(displayImageTask);
				recordTheTask(url,future);
			}

			if (options.isShowStubImage()) {
				imageView.setImageResource(options.getStubImage());
			} else {
				imageView.setImageBitmap(null);
			}
		}
	}
	
	
	private void makeSureThreadPoolIsNotTooFull() {
		
		if (ImageLoaderConfiguration.loggingEnabled){
			Log.i(TAG, "activating downloading tasks:" + imageLoadingExecutor.getActiveCount() + " Pool size is " +
					configuration.threadPoolSize);
		}
		//TODO:make it a configuration
		int SPACE_THREAD_NUMBER = 0;
		if ((configuration.threadPoolSize - imageLoadingExecutor.getActiveCount()) <= SPACE_THREAD_NUMBER){
			Assert.assertEquals(submittedTaskQueue.size(), submittedTaskMap.size());			
			//find the oldest unfinished task..
			Iterator<String> it = submittedTaskQueue.iterator();
			//the task in this list is either finished or has just been canceled
			Queue<String> toBeRemoved = new LinkedList<String>();
			while(it.hasNext()){
					String oldestTask = it.next();
					Assert.assertNotNull(oldestTask);
					toBeRemoved.offer(oldestTask);
					Future<?> f= submittedTaskMap.remove(oldestTask);
					Log.d(ImageLoader.TAG, "CHECK:task for " + oldestTask + " has Finished : " + f.isDone());
					if(f != null && !f.isDone()){
						Log.d(ImageLoader.TAG, "CHECK:cancel Task " + oldestTask);
						f.cancel(true);
						break;
					}
			}
			
			Iterator<String> it2 = toBeRemoved.iterator();
			while(it2.hasNext()){
				submittedTaskQueue.remove(it2.next());
			}
			
			Assert.assertEquals(submittedTaskQueue.size(), submittedTaskMap.size());
			
		}
		
	}
	
	private void recordTheTask(String url, Future<?> future){
		submittedTaskQueue.add(url);
		submittedTaskMap.put(url, future);
	}

	private void checkExecutors() {
		if (imageLoadingExecutor == null || imageLoadingExecutor.isShutdown()) {
			//imageLoadingExecutor = (ThreadPoolExecutor)Executors.newFixedThreadPool(
			//		configuration.threadPoolSize, configuration.displayImageThreadFactory);
			int nThreads = configuration.threadPoolSize;
			imageLoadingExecutor = new ThreadPoolExecutor(nThreads, nThreads,
                                      0L, TimeUnit.MILLISECONDS,
                                      new LinkedBlockingQueue<Runnable>(),
                                      configuration.displayImageThreadFactory);
		}
		if (cachedImageLoadingExecutor == null || cachedImageLoadingExecutor.isShutdown()) {
			//cachedImageLoadingExecutor = (ThreadPoolExecutor)Executors.newSingleThreadExecutor(configuration.displayImageThreadFactory);
			cachedImageLoadingExecutor = new ThreadPoolExecutor(1, 1,
                                    		0L, TimeUnit.MILLISECONDS,
                                    		new LinkedBlockingQueue<Runnable>(),
                                    		configuration.displayImageThreadFactory);
		}
	}

	/** Returns memory cache */
	public MemoryCacheAware<String, Bitmap> getMemoryCache() {
		return configuration.memoryCache;
	}

	/**
	 * Clear memory cache.<br />
	 * Do nothing if {@link #init(ImageLoaderConfiguration)} method wasn't called before.
	 */
	public void clearMemoryCache() {
		if (configuration != null) {
			configuration.memoryCache.clear();
		}
	}

	/** Returns disc cache */
	public DiscCacheAware getDiscCache() {
		return configuration.discCache;
	}

	/**
	 * Clear disc cache.<br />
	 * Do nothing if {@link #init(ImageLoaderConfiguration)} method wasn't called before.
	 */
	public void clearDiscCache() {
		if (configuration != null) {
			configuration.discCache.clear();
		}
	}

	/** Returns URL of image which is loading at this moment into passed {@link ImageView} */
	public String getLoadingUrlForView(ImageView imageView) {
		return cacheKeyForImageView.get(imageView);
	}

	/**
	 * Cancel the task of loading and displaying image for passed {@link ImageView}.
	 * 
	 * @param imageView
	 *            {@link ImageView} for which display task will be cancelled
	 */
	public void cancelDisplayTask(ImageView imageView) {
		cacheKeyForImageView.remove(imageView);
	}

	/** 
	 * Stops all running display image tasks, discards all other scheduled tasks 
	 * */
	public void stop() {
		if (imageLoadingExecutor != null) {
			shutdownThreadPool(imageLoadingExecutor);
		}
		if (cachedImageLoadingExecutor != null) {
			shutdownThreadPool(cachedImageLoadingExecutor);
		}
	}
	/**
	 * shutdown all the task in the pool
	 * @param pool which u want to shutdown immediately
	 */
	public void shutdownThreadPool( ExecutorService pool) {
		pool.shutdown();
		try {
			pool.shutdownNow();
			if (pool.awaitTermination(1, TimeUnit.SECONDS)) {
				Log.e(TAG, "Pool did not terminal");
			}
		}catch (InterruptedException ie){
			pool.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
	
	/**
	 * Defines image size for loading at memory (for memory economy) by {@link ImageView} parameters.<br />
	 * Size computing algorithm:<br />
	 * 1) Get <b>maxWidth</b> and <b>maxHeight</b>. If both of them are not set then go to step #2.<br />
	 * 2) Get <b>layout_width</b> and <b>layout_height</b>. If both of them haven't exact value then go to step #3.</br>
	 * 3) Get device screen dimensions.
	 */
	private ImageSize getImageSizeScaleTo(ImageView imageView) {
		int width = -1;
		int height = -1;

		// Check maxWidth and maxHeight parameters
		try {
			Field maxWidthField = ImageView.class.getDeclaredField("mMaxWidth");
			Field maxHeightField = ImageView.class.getDeclaredField("mMaxHeight");
			maxWidthField.setAccessible(true);
			maxHeightField.setAccessible(true);
			int maxWidth = (Integer) maxWidthField.get(imageView);
			int maxHeight = (Integer) maxHeightField.get(imageView);

			if (maxWidth >= 0 && maxWidth < Integer.MAX_VALUE) {
				width = maxWidth;
			}
			if (maxHeight >= 0 && maxHeight < Integer.MAX_VALUE) {
				height = maxHeight;
			}
		} catch (Exception e) {
			Log.e(TAG, e.getMessage(), e);
		}

		if (width < 0 && height < 0) {
			// Get layout width and height parameters
			LayoutParams params = imageView.getLayoutParams();
			width = params.width;
			height = params.height;
		}

		if (width < 0 && height < 0) {
			// Get device screen dimensions
			width = configuration.maxImageWidthForMemoryCache;
			height = configuration.maxImageHeightForMemoryCache;

			// Consider device screen orientation
			int screenOrientation = imageView.getContext().getResources().getConfiguration().orientation;
			if ((screenOrientation == Configuration.ORIENTATION_PORTRAIT && width > height)
					|| (screenOrientation == Configuration.ORIENTATION_LANDSCAPE && width < height)) {
				int tmp = width;
				width = height;
				height = tmp;
			}
		}
		return new ImageSize(width, height);
	}
}
