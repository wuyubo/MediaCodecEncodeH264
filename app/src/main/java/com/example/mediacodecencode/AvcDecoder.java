package com.example.mediacodecencode;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;


public class AvcDecoder
{
	private final static String TAG = "MediaCodec";

	public enum VideoCodecType { VIDEO_CODEC_VP8, VIDEO_CODEC_VP9, VIDEO_CODEC_H264 }

	private static final String H264_MIME_TYPE = "video/avc";
	
	private int TIMEOUT_USEC = 12000;

	private MediaCodec mediaCodec;
	int m_width;
	int m_height;
	int m_framerate;
	byte[] m_info = null;
	public boolean isInit = false;

	boolean hasDecodedFirstFrame = false;
	private static final int DEQUEUE_INPUT_TIMEOUT = 500000;
	 
	public byte[] configbyte;

	Surface m_surface;

	private static int yuvqueuesize = 10;
	public static ArrayBlockingQueue<byte[]> YUVQueue = new ArrayBlockingQueue<byte[]>(yuvqueuesize);

	@SuppressLint("NewApi")
	public AvcDecoder() {

	}

	public void setSurface(Surface surface){
		Log.d(TAG, "set surface:" + surface);
		m_surface = surface;
	}


	public boolean InitDecoder(int width, int height){

		m_width  = width;
		m_height = height;
		String mime = null;
		String[] supportedCodecPrefixes = null;
//		 if (type == VideoCodecType.VIDEO_CODEC_H264) {
			 mime = H264_MIME_TYPE;
//			 supportedCodecPrefixes = supportedH264HwCodecPrefixes;
//		 }
		try {
			MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
			format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
			Log.d(TAG, "InitDecoder  Format: " + format);
			mediaCodec = MediaCodec.createDecoderByType(mime);
			mediaCodec.configure(format, m_surface, null, 0);
			mediaCodec.start();
			outputBuffers = mediaCodec.getOutputBuffers();
			inputBuffers = mediaCodec.getInputBuffers();
			hasDecodedFirstFrame = false;
			isInit = true;
		} catch (IllegalStateException e) {
			Log.e(TAG, "initEncode failed", e);
			return false;
		} catch (IllegalArgumentException e){
			Log.e(TAG, "initEncode failed with illegal argument exception: ", e);
			return false;
		} catch(Exception e){
			Log.e(TAG, "initEncode failed", e);
			return false;
		}
		return true;
	}

	public void dequeueInputBuffer(byte[] data, int offset, int size, long presentationTimeUs, int flags)
	{
		int index = mediaCodec.dequeueInputBuffer(DEQUEUE_INPUT_TIMEOUT);

		if (index >= 0) {
			ByteBuffer buffer;
			buffer = mediaCodec.getInputBuffer(index);
			if (buffer != null) {
				buffer.put(data, offset, size);
				mediaCodec.queueInputBuffer(index, 0, size, presentationTimeUs, flags);
				if(!hasDecodedFirstFrame){
					hasDecodedFirstFrame = true;
				}
			}
		}
	}

	public void dequeueOutputBuffer() {
		Log.e(TAG, "dequeueOutputBuffer start");
		try {
			MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
			int index = mediaCodec.dequeueOutputBuffer(info, DEQUEUE_INPUT_TIMEOUT);
			if (index >= 0) {
				// setting true is telling system to render frame onto Surface
				mediaCodec.releaseOutputBuffer(index, true);
//			if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
//				break;
//			}
			}
		}catch (IllegalStateException e) {
			Log.e(TAG, "dequeueOutputBuffer failed", e);
		}
	}

	@SuppressLint("NewApi")
	public void StopDecoder() {
	    try {
	        mediaCodec.stop();
	        mediaCodec.release();

	    } catch (Exception e){ 
	        e.printStackTrace();
	    }
	}
	
	ByteBuffer[] inputBuffers;
	ByteBuffer[] outputBuffers;

	public boolean isRuning = false;


	public void start(){
		if(isRuning)
			return;
		Log.d(TAG, "start decoder: " + m_width + "x" + m_height);
		StartDecoderThread();
	}

	public void stop(){
		if(!isRuning)
			return;
		Log.d(TAG, "stop decoder: " + m_width + "x" + m_height);
		StopThread();
	}
	
	public void StopThread(){
		isRuning = false;
		StopDecoder();
	}

    public boolean outputBuffer(){
		dequeueOutputBuffer();
		return true;
	}

	public void StartDecoderThread(){
		Thread EncoderThread = new Thread(new Runnable() {

			@SuppressLint("NewApi")
			@Override
			public void run() {
			isRuning = true;
			byte[] input = null;
			while (isRuning) {
				if(!outputBuffer()){
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
			}
		});
		EncoderThread.start();
	}

}
