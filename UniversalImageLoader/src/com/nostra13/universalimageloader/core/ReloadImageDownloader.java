/**
 * Resume uncompleted task downloader 
 */
package com.nostra13.universalimageloader.core;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.nostra13.universalimageloader.core.assist.FlushedInputStream;

/**
 * @author simsun
 *
 */
public class ReloadImageDownloader extends ImageDownloader {

	private int connectTimeout;
	private int readTimeout;
	private int mStartPos;
	private int mEndPos;
	
	private HttpURLConnection conn;
	/**
	 * 
	 * @param connectTimeout
	 * @param readTimeout
	 * @param startpos startpoint of the stream u want to load
	 * @param endpos endpoint of the stream u want to load
	 */
	public ReloadImageDownloader(int connectTimeout, int readTimeout, int startpos , int endpos) {
		this.connectTimeout = connectTimeout;
		this.readTimeout = readTimeout;
		this.mStartPos = startpos;
		this.mEndPos = endpos;
	}
	/**
	 * @see com.nostra13.universalimageloader.core.ImageDownloader#getStreamFromNetwork(java.net.URL)
	 */
	@Override
	protected InputStream getStreamFromNetwork(URL imageUrl) throws IOException {
		conn = (HttpURLConnection) imageUrl.openConnection();
		conn.setConnectTimeout(readTimeout);
		conn.setRequestMethod("GET");
		conn.setRequestProperty("Range", "bytes=" + mStartPos  + "-" + mEndPos);
		
		return new FlushedInputStream(new BufferedInputStream(conn.getInputStream()));
	}
	@Override
	protected InputStream getStreamFromNetwrokWithRange(URL imageUrl, long start) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

}
