package com.nostra13.universalimageloader.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.util.Log;

import com.nostra13.universalimageloader.core.ImageLoader;

/**
 * Provides operations with files
 * 
 * @author Sergey Tarasevich (nostra13[at]gmail[dot]com)
 */
public final class FileUtils {

	private FileUtils() {
	}
	
	/**
	 * 
	 * @param is
	 * @param os
	 * @param needSlow for debug only
	 * @param url   for debug only
	 * @throws IOException
	 * @throws InterruptedException
	 */

	public static void copyStream(InputStream is, OutputStream os ,boolean needSlow ,String url) throws IOException , InterruptedException {
		final int buffer_size = 1024;
		byte[] bytes = new byte[buffer_size];
		int totol_size = 0;
		while (true) {
			
			//let's make the read slow than actually
			
			if(Thread.currentThread().isInterrupted()){
				//Log.d(ImageLoader.TAG, "oh...Downloading" + url + "was interrupted. saved file size " + totol_size);
				//just for test....even we received the intrrupt ,let's write something..
				
				int count = is.read(bytes, 0, buffer_size);
				if (count == -1) {
					break;
				}
				os.write(bytes, 0, count);
				totol_size +=count;
				
				//test finish..
				
				Log.d(ImageLoader.TAG, "oh...Downloading" + url + "was interrupted. saved file size " + totol_size);
				
				throw new InterruptedException("downloading " + url+ "was interrupted");
			}
			
			//Pierr - making the downloading longer 
//			if(needSlow){
//				try{
//					Log.d(ImageLoader.TAG, "->>> downloading " + url + " wait 1 seconds...");
//					Thread.sleep(1000);
//				}catch(InterruptedException ex){
//					//ex.printStackTrace();
//					Log.d(ImageLoader.TAG,"oh... downloading " + url + " was interrupted..");
//				}
//			}
			
			int count = is.read(bytes, 0, buffer_size);
			if (count == -1) {
				break;
			}
			os.write(bytes, 0, count);
			totol_size +=count;
		}
		
		
	}
}
