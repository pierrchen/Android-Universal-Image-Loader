package com.nostra13.universalimageloader.core;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import android.util.Log;

import com.nostra13.universalimageloader.core.assist.FlushedInputStream;

/**
 * Default implementation of ImageDownloader. Uses {@link URLConnection} for image stream retrieving.
 * 
 * @author Sergey Tarasevich (nostra13[at]gmail[dot]com)
 */
public class DefaultImageDownloader extends ImageDownloader {

	private int connectTimeout;
	private int readTimeout;

	public DefaultImageDownloader(int connectTimeout, int readTimeout) {
		this.connectTimeout = connectTimeout;
		this.readTimeout = readTimeout;
	}

	@Override
	public InputStream getStreamFromNetwork(URL imageUrl) throws IOException {
		URLConnection conn = imageUrl.openConnection();
		conn.setConnectTimeout(connectTimeout);
		conn.setReadTimeout(readTimeout);
		return new FlushedInputStream(new BufferedInputStream(conn.getInputStream()));
	}

	@Override
	protected InputStream getStreamFromNetwrokWithRange(URL imageUrl, long start) throws IOException {
		HttpURLConnection conn = (HttpURLConnection)imageUrl.openConnection();
		conn.setConnectTimeout(connectTimeout);
		conn.setReadTimeout(readTimeout);
		conn.setRequestMethod("GET");
		conn.setRequestProperty("Range", "bytes=" + start  + "-");
		Log.d(ImageLoader.TAG, "resume download from " + start + " for " + imageUrl + "response code " + conn.getResponseCode());
		
		if(conn.getResponseCode() != HttpURLConnection.HTTP_PARTIAL ){
			throw new IOException("Failed to get partial content from server " + conn.getResponseMessage());
		}
		return new FlushedInputStream(new BufferedInputStream(conn.getInputStream()));
		
	}
}