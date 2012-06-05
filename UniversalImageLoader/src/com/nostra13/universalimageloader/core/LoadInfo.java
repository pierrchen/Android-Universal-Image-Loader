package com.nostra13.universalimageloader.core;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * show the task downing information. This class help us to persistence downling
 * status.
 */

/**
 * @author simsun
 *	
 */
public class LoadInfo {
	private int mFileSize;
	private int mCompletedSize;
	private String mUrlString;

	public LoadInfo(int fileSize, int completedSize, String urlString) {
		super();
		this.mFileSize = fileSize;
		this.mCompletedSize = completedSize;
		this.mUrlString = urlString;
	}

	public int getFileSize() {
		return mFileSize;
	}

	public synchronized void setFileSize(int fileSize) {
		this.mFileSize = fileSize;
	}

	public int getCompletedSize() {
		return mCompletedSize;
	}

	public synchronized void setCompletedSize(int completedSize) {
		this.mCompletedSize = completedSize;
	}

	public String getUrlString() {
		return mUrlString;
	}

	public void setUrlString(String urlString) {
		this.mUrlString = urlString;
	}
	
	public JSONObject toJSONObj() {
		JSONObject obj = new JSONObject();
		try {
			obj.put("fileSize", mFileSize);
			obj.put("completeSize", mCompletedSize);
			obj.put("url", mUrlString);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return obj;
	}
	
	public String toJSONString() {
		return toJSONObj().toString();
	}
}


