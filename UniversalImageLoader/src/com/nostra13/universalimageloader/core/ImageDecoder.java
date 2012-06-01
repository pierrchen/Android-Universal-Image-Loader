package com.nostra13.universalimageloader.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.util.Log;

import com.nostra13.universalimageloader.core.assist.DecodingType;
import com.nostra13.universalimageloader.core.assist.ImageSize;

/**
 * Decodes images to {@link Bitmap}
 * 
 * @author Sergey Tarasevich (nostra13[at]gmail[dot]com)
 * @see DecodingType
 */
class ImageDecoder {

	private final URL imageUrl;
	private final ImageDownloader imageDownloader;
	private final ImageSize targetSize;
	private final DecodingType decodingType;
	private final static String TAG = "imageDecoder";
	/**
	 * @param imageUrl
	 *            Image URL (<b>i.e.:</b> "http://site.com/image.png", "file:///mnt/sdcard/image.png")
	 * @param imageDownloader
	 *            Image downloader
	 * @param targetImageSize
	 *            Image size to scale to during decoding
	 * @param decodingType
	 *            {@link DecodingType Decoding type}
	 */
	ImageDecoder(URL imageUrl, ImageDownloader imageDownloader, ImageSize targetImageSize, DecodingType decodingType) {
		this.imageUrl = imageUrl;
		this.imageDownloader = imageDownloader;
		this.targetSize = targetImageSize;
		this.decodingType = decodingType;
	}

	/**
	 * Decodes image from URL into {@link Bitmap}. Image is scaled close to incoming {@link ImageSize image size} during
	 * decoding. Initial image size is reduced by the power of 2 (according Android recommendations)
	 * 
	 * @return Decoded bitmap
	 * @throws IOException
	 */
	public Bitmap decode() throws IOException {
		Options decodeOptions = getBitmapOptionsForImageDecoding();
		InputStream imageStream = imageDownloader.getStream(imageUrl);
		try {
			return BitmapFactory.decodeStream(imageStream, null, decodeOptions);
		} finally {
			imageStream.close();
		}
	}

	private Options getBitmapOptionsForImageDecoding() throws IOException {
		Options options = new Options();
		options.inSampleSize = computeImageScale();
		return options;
	}

	private int computeImageScale() throws IOException {
		int width = targetSize.getWidth();
		int height = targetSize.getHeight();

		// decode image size
		Options options = new Options();
		options.inJustDecodeBounds = true;
		InputStream imageStream = imageDownloader.getStream(imageUrl);
		try {
			BitmapFactory.decodeStream(imageStream, null, options);
		} finally {
			imageStream.close();
		}

		int scale = 1;
		switch (decodingType) {
			//default use FAST mode
			default:
			case FAST:
				// Find the correct scale value. It should be the power of 2.
				int width_tmp = options.outWidth; 
				int height_tmp = options.outHeight;

				while (true) {
					if (width_tmp / 2 < width || height_tmp / 2 < height) break;
					width_tmp /= 2;
					height_tmp /= 2;
					scale *= 2;
				}
				if (ImageLoaderConfiguration.loggingEnabled) {
					Log.i(TAG, "FAST bitmap width:"+options.outWidth + " height:" + options.outHeight +
							" target width:"+width+" height:"+height+" scale:"+scale);
				}
				break;
			case MEMORY_SAVING:
				int widthScale = (int) (Math.floor(((double) options.outWidth) / width));
				int heightScale = (int) (Math.floor(((double) options.outHeight) / height));
				int minScale = Math.min(widthScale, heightScale);
				if (minScale > 1) {
					scale = minScale;
				}
				if (ImageLoaderConfiguration.loggingEnabled) {
					Log.i(TAG, "MEMORY_SAVING bitmap width:"+options.outWidth + " height:" + options.outHeight +
							" target width:"+width+" height:"+height+" scale:"+scale);
				}
				
			case ABSOLUTE_SIZE:
				scale = calculateAbsoluteSampleSize(options, width,height);
				if (ImageLoaderConfiguration.loggingEnabled) {
					Log.i(TAG, "ABSOLUTE_SIZE bitmap width:"+options.outWidth + " height:" + options.outHeight +
							" target width:"+width+" height:"+height+" scale:"+scale);
				}
				
				break;
		}

		return scale;
	}
	/**
	 * This implementation calculates the closest sample size that will result in the final decoded bitmap having a width 
	 * and height equal to or larger than the requested width and height 
	 * This implementation doesnot ensure a power of 2 is returned for sample which can be faster when decoding but results in a larger 
	 * bitmap which isn`t as useful for caching and displaying
	 * @param options bitmap option information. u can get this use decode interface with options.inJustDecodeBounds = true;
	 * it will help u to save memory
	 * @param width target view width 
	 * @param height target view height
	 * @return scale
	 */
	private int calculateAbsoluteSampleSize(Options options, int reqWidth, int reqHeight) {
		
		int sampleSize = 1;
		
		if (options.outWidth > reqWidth || options.outHeight > reqHeight) {
			if (reqWidth < reqHeight) {
				sampleSize = Math.round((float) options.outWidth
						/ (float) reqWidth);
			} else {
				sampleSize = Math.round((float) options.outHeight
						/ (float) reqHeight);
			}

			/*
			 * This offers some additional logic in case the image has a strange
			 * aspect ratio. For example , a panorama may have a much larger
			 * width than height. in these cases tha total pixels might still
			 * end up being too large to fit comfortably in memory, so we should
			 * be more aggressive with sample down the image
			 */

			final float totalPixels = options.outHeight * options.outWidth;
			final float totalReqPixels = reqHeight * reqWidth;

			while (totalPixels / (sampleSize * sampleSize) > totalReqPixels) {
				sampleSize++;
			}
		}
		return sampleSize;
	}

	
}
