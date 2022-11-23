package com.example.mediacodecencode;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;


public class AvcEncoder
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
	int mId;
	 
	public byte[] configbyte;

	private double bitrateAccumulator;
	private double bitrateAccumulatorMax;
	private double bitrateObservationTimeMs;
	private int bitrateAdjustmentScaleExp;
	private int targetBitrateBps;
	private int targetFps;
	List<AvcDecoder> decoderlist = new ArrayList<>();

	private static int yuvqueuesize = 10;
	public static ArrayBlockingQueue<byte[]> YUVQueue = new ArrayBlockingQueue<byte[]>(yuvqueuesize);

	@SuppressLint("NewApi")
	public AvcEncoder() {

	}

	static MediaCodec createByCodecName(String codecName) {
		try {
			// In the L-SDK this call can throw IOException so in order to work in
			// both cases catch an exception.
			return MediaCodec.createByCodecName(codecName);
		} catch (Exception e) {
			return null;
		}
	}

	public void addDecoder(AvcDecoder decoder){
		decoderlist.add(decoder);
		if(isRuning){
			requestKeyframe();
		}
	}

	public void clearDecoders(){
		decoderlist.clear();
	}

	public boolean InitEncoder(int profile, int width, int height, int kbps, int fps, int bitrateMode, int id){
        mId = id;
		m_width  = width;
		m_height = height;
		m_framerate = fps;


		String mime = null;
		int keyFrameIntervalSec = 0;
//		if (type == VideoCodecType.VIDEO_CODEC_H264) {
			mime = H264_MIME_TYPE;
			keyFrameIntervalSec = 10;
//		}
		targetBitrateBps = 1000 * kbps;
		targetFps = fps;
		bitrateAccumulatorMax = targetBitrateBps / 8.0;
		bitrateAccumulator = 0;
		bitrateObservationTimeMs = 0;
		bitrateAdjustmentScaleExp = 0;

		try {
			MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
			format.setInteger(MediaFormat.KEY_BIT_RATE, targetBitrateBps);
			format.setInteger(MediaFormat.KEY_BITRATE_MODE, bitrateMode);
//			format.setInteger(MediaFormat.KEY_COLOR_FORMAT, properties.colorFormat);
			format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
			format.setInteger(MediaFormat.KEY_FRAME_RATE, targetFps);
			format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, keyFrameIntervalSec);
			Log.d(TAG, "  Format: " + format);
			mediaCodec = MediaCodec.createEncoderByType(mime);
			if (mediaCodec == null) {
				Log.e(TAG, "Can not create media encoder");
				return false;
			}
			mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

			mediaCodec.start();
			outputBuffers = mediaCodec.getOutputBuffers();
			Log.d(TAG, "Output buffers: " + outputBuffers.length);
//			createfile();

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
	
	private static String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/test1.h264";
	private BufferedOutputStream outputStream;
	FileOutputStream outStream;
	private void createfile(){
		File file = new File(path);
		if(file.exists()){
			file.delete();
		}
	    try {
	        outputStream = new BufferedOutputStream(new FileOutputStream(file));
	    } catch (Exception e){ 
	        e.printStackTrace();
	    }
	}

	@SuppressLint("NewApi")
	public void StopEncoder() {
	    try {
	        mediaCodec.stop();
	        mediaCodec.release();

			outputStream.flush();
			outputStream.close();
	    } catch (Exception e){ 
	        e.printStackTrace();
	    }
	}
	
	ByteBuffer[] inputBuffers;
	ByteBuffer[] outputBuffers;

	public boolean isRuning = false;

	public void putEncodeFrame(byte[] buffer) {
		if(!isRuning)
			return;

		Log.d(TAG, "mId:" + mId + " putEncodeFrame");
		if (YUVQueue.size() >= 10) {
			YUVQueue.poll();
		}

		YUVQueue.add(buffer);
	}

	public void start(){
		if(isRuning)
			return;
		Log.d(TAG, "start encoder: " + m_width + "x" + m_height);
		StartEncoderThread();
	}

	public void stop(){
		if(!isRuning)
			return;
		Log.d(TAG, "stop encoder: " + m_width + "x" + m_height);
		StopThread();
		for(int i = 0; i < decoderlist.size(); i++){
			decoderlist.get(i).stop();
		}
	}
	
	public void StopThread(){
		isRuning = false;
		StopEncoder();
        try {
        	if(outputStream != null)  {
				outputStream.flush();
				outputStream.close();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	int count = 0;

	public void requestKeyframe(){
		Log.d(TAG, "request keyframe mId:" + mId);
		Bundle b = new Bundle();
		b.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
		mediaCodec.setParameters(b);
	}

	public boolean encodeFrame(byte[] input){
        if (input == null)
            return false;

        /*if (input.length != m_width*m_height*3/2) {
        	Log.d(TAG,"crop to " + m_width + "x" + m_height);
			byte[] yuv420sp = new byte[m_width * m_height * 3 / 2];
			NV21ToNV12(input, yuv420sp, m_width, m_height);
			input = yuv420sp;
		}*/

        long pts =  0;
        long generateIndex = 0;
        try {
            long startMs = System.currentTimeMillis();
            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
            int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0) {
                pts = computePresentationTime(generateIndex);
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                inputBuffer.put(input);
                mediaCodec.queueInputBuffer(inputBufferIndex, 0, m_width * m_height * 3 / 2, pts, 0);
                generateIndex += 1;
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
	    return true;
    }

    public boolean outputBuffer(){
//		dequeueOutputBuffer();
		try {
			ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
			MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
			int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
			while (outputBufferIndex >= 0) {
				//Log.i("AvcEncoder", "Get H264 Buffer Success! flag = "+bufferInfo.flags+",pts = "+bufferInfo.presentationTimeUs+"");
				ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
				byte[] outData = new byte[bufferInfo.size];
				outputBuffer.get(outData);
				if(bufferInfo.flags == 2){
					configbyte = new byte[bufferInfo.size];
					configbyte = outData;
				}else if(bufferInfo.flags == 1){
					byte[] keyframe = new byte[bufferInfo.size + configbyte.length];
					System.arraycopy(configbyte, 0, keyframe, 0, configbyte.length);
					System.arraycopy(outData, 0, keyframe, configbyte.length, outData.length);

//					outputStream.write(keyframe, 0, keyframe.length);
					Log.d(TAG, m_width+"x"+m_height+" key frame");
				}else{
//					outputStream.write(outData, 0, outData.length);
				}
				for(int i = 0; i < decoderlist.size(); i++){
					if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == MediaCodec.BUFFER_FLAG_CODEC_CONFIG
							||(bufferInfo.flags == 1 && !decoderlist.get(i).isInit))
					{
						decoderlist.get(i).InitDecoder(m_width, m_height);
						decoderlist.get(i).dequeueInputBuffer(outData, bufferInfo.offset, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags);
						decoderlist.get(i).start();
					}else{
						decoderlist.get(i).dequeueInputBuffer(outData, bufferInfo.offset, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags);
					}
				}

				mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
				outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
			}

		} catch (Throwable t) {
			t.printStackTrace();
		}
		return true;
	}

	public void StartEncoderThread(){
		Thread EncoderThread = new Thread(new Runnable() {

			@SuppressLint("NewApi")
			@Override
			public void run() {
			isRuning = true;
			byte[] input = null;
			while (isRuning) {
				Log.d(TAG,"YUVQueue poll start mId:" + mId);
				if (YUVQueue.size() > 0){
					input = YUVQueue.poll();
					if (input != null) {
						encodeFrame(input);
					} else {
						Log.d(TAG,"YUVQueue wait mId:" + mId);
						try {
							Thread.sleep(33);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
				outputBuffer();
			}
			}
		});
		EncoderThread.start();
	}
	
	private void NV21ToNV12(byte[] nv21,byte[] nv12,int width,int height){
		if(nv21 == null || nv12 == null) {
			return;
		}
		int framesize = width*height*3/2;
		int i = 0,j = 0;
		//System.arraycopy(nv21, 0, nv12, 0, framesize);
		/*for(i = 0; i < framesize; i++){
			nv12[i] = nv21[i];
		}
		for (j = 0; j < framesize/2; j+=2)
		{
		  nv12[framesize + j-1] = nv21[j+framesize];
		}
		for (j = 0; j < framesize/2; j+=2)
		{
		  nv12[framesize + j] = nv21[j+framesize-1];
		}*/
	}
	
    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / m_framerate;
    }

	// Helper struct for dequeueOutputBuffer() below.
	static class OutputBufferInfo {
		public OutputBufferInfo(
				int index, ByteBuffer buffer, boolean isKeyFrame, long presentationTimestampUs) {
			this.index = index;
			this.buffer = buffer;
			this.isKeyFrame = isKeyFrame;
			this.presentationTimestampUs = presentationTimestampUs;
		}

		public final int index;
		public final ByteBuffer buffer;
		public final boolean isKeyFrame;
		public final long presentationTimestampUs;
	}
}
