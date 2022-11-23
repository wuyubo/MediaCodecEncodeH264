package com.example.mediacodecencode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity  implements SurfaceHolder.Callback,PreviewCallback, TextureView.SurfaceTextureListener{

	private SurfaceView surfaceview;
    private SurfaceView surfaceview1;
    private SurfaceView surfaceview2;
    private SurfaceView surfaceview3;
    private SurfaceView surfaceview4;
    private SurfaceView surfaceview5;
    private Camera mCamera;

    private TextureView textureView;
    private TextureView textureView1;
    private TextureView textureView2;
    private TextureView textureView3;
    private TextureView textureView4;
    private TextureView textureView5;
	
    private SurfaceHolder surfaceHolder;
    private SurfaceTexture mSurfaceTexture;
    private CameraDevice mCameraDevice;
    private ImageReader mImageReader;
    private Handler mCameraHanlder;
    private CaptureRequest.Builder mPreviewBuilder;
    private CameraCaptureSession mCameraCaptureSession;
	
	private Camera camera = null;
	
    private Parameters parameters;
    
    int width = 1920;
    
    int height = 1080;
    
    int framerate = 30;
    
    int biterate = 8500*1000;
    
    private static int yuvqueuesize = 10;

    private byte[][] mDataBuffer;
	public static ArrayBlockingQueue<byte[]> YUVQueue = new ArrayBlockingQueue<byte[]>(yuvqueuesize); 
	
	private AvcEncoder avcCodec;

    public static int ENCODER_LAYER = 4;
    private List<AvcEncoder> avcCodecs;
    private AvcEncoder avcCodec0;
    private AvcEncoder avcCodec1;
    private AvcEncoder avcCodec2;
    private AvcEncoder avcCodec3;

    private AvcDecoder avcDeCodec1;
    private AvcDecoder avcDeCodec2;
    private AvcDecoder avcDeCodec3;
    private AvcDecoder avcDeCodec4;
    private AvcDecoder avcDeCodec5;


    private final static int CAMERA_OK = 10001;
    private static String[] PERMISSIONS_STORAGE = {
            "android.permission.CAMERA",
            "android.permission.WRITE_EXTERNAL_STORAGE" };

    RelativeLayout mContentLayout;
    RelativeLayout mContentLayout1;
    Button addButton;
    Button removeButton;
    Button switchCameraButton;
    Button MirrorButton;
    Button clearButton;
    TextView textView;
    TextView textView1;
    TextView textView2;
    private int currenCamera = -1;
    private int encoderCount = 0;
    private int decoderCount = 0;


    private static final String TAG = "MediaCodec";

    boolean isRuning = false;

    private View.OnClickListener listener = new View.OnClickListener(){

        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.preview_add: {
                    Log.d(TAG, "onClick: open camera");
                    if (doCameraApi1) switchCamera(0);
                    else startCameraApi2();
                    break;
                }
                case R.id.preview_remove: {
                    Log.d(TAG, "onClick: add encode");
                    switch (encoderCount){
                        case 0:
                        {
                            avcCodec3.start();
                            textView1.setText("编码数量：1 \n(1080P)");
                            encoderCount++;
                        }
                        break;
                        case 1:
                        {
                            avcCodec2.start();
                            textView1.setText("编码数量：2 \n(1080P, 720P)");
                            encoderCount++;
                        }
                        break;
                        case 2:
                        {
                            avcCodec1.start();
                            textView1.setText("编码数量：3 \n(1080P, 720P)\n(360P)");
                            encoderCount++;
                        }
                        break;
                        case 3:
                        {
                            avcCodec0.start();
                            textView1.setText("编码数量：4 \n(1080P, 720P)\n(360P, 180P)");
                            encoderCount++;
                        }
                        break;
                    }
                    if(!isRuning){
                        StartEncoderThread();
                    }
//                    avcCodec0.requestKeyframe();
                    break;
                }
                case R.id.switch_cam: {
                    Log.d(TAG, "onClick: cam");
                    switchCamera(currenCamera == 0? 1:0);
                    break;
                }
                case R.id.mirror: {
                    Log.d(TAG, "onClick: add decode");
                    if (!doSurfaceView) {
                        avcDeCodec1.setSurface(new Surface(textureView1.getSurfaceTexture()));
                        avcDeCodec2.setSurface(new Surface(textureView2.getSurfaceTexture()));
                        avcDeCodec3.setSurface(new Surface(textureView3.getSurfaceTexture()));
                        avcDeCodec4.setSurface(new Surface(textureView4.getSurfaceTexture()));
                        avcDeCodec5.setSurface(new Surface(textureView5.getSurfaceTexture()));
                    }

                    switch (decoderCount){
                        case 0:
                        {
                            avcCodec3.addDecoder(avcDeCodec1);
                            textView2.setText("解码数量：1 \n(1*1080P)");
                            decoderCount++;
                        }
                        break;
                        case 1:
                        {
                            avcCodec3.addDecoder(avcDeCodec2);
                            textView2.setText("解码数量：2 \n(2*1080P)");
                            decoderCount++;
                        }
                        break;
                        case 2:
                        {
                            avcCodec3.addDecoder(avcDeCodec3);
                            textView2.setText("解码数量：3 \n(3*1080P)");
                            decoderCount++;
                        }
                        break;
                        case 3:
                        {
                            avcCodec3.addDecoder(avcDeCodec4);
                            textView2.setText("解码数量：4 \n(4*1080P)");
                            decoderCount++;
                        }
                        break;
                        case 4:
                        {
                            avcCodec3.addDecoder(avcDeCodec5);
                            textView2.setText("解码数量：5 \n(5*1080P)");
                            decoderCount++;
                        }
                        break;
                    }
                    break;
                }
                case R.id.clear: {
                    Log.d(TAG, "onClick: clear");
                    avcCodec0.stop();
                    avcCodec0.clearDecoders();
                    avcCodec1.stop();
                    avcCodec1.clearDecoders();
                    avcCodec2.stop();
                    avcCodec2.clearDecoders();
                    avcCodec3.stop();
                    avcCodec3.clearDecoders();
                    textView1.setText("编码数量：0");
                    textView2.setText("解码数量：0");
                    encoderCount = 0;
                    decoderCount = 0;
                    break;
                }
            }
        }
    };
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

        mContentLayout = findViewById(R.id.content_layout);
        mContentLayout1 = findViewById(R.id.content_layout1);
        addButton = findViewById(R.id.preview_add);
        removeButton = findViewById(R.id.preview_remove);
        switchCameraButton = findViewById(R.id.switch_cam);
        MirrorButton = findViewById(R.id.mirror);
        clearButton = findViewById(R.id.clear);
        textView = findViewById(R.id.textView);
        textView1 = findViewById(R.id.textView1);
        textView2 = findViewById(R.id.textView2);

        mDataBuffer = new byte[yuvqueuesize][];
        for (int i = 0 ; i < yuvqueuesize; i++)
            mDataBuffer[i] = new byte[width * height * 3 / 2];


        addButton.setOnClickListener(listener);
        removeButton.setOnClickListener(listener);
        switchCameraButton.setOnClickListener(listener);
        MirrorButton.setOnClickListener(listener);
        clearButton.setOnClickListener(listener);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }

        SupportAvcCodec();
        init();
	}

	private void init(){
	    if (doSurfaceView) {
	        mContentLayout.setVisibility(View.VISIBLE);
	        mContentLayout1.setVisibility(View.GONE);
        }
	    else {
	        mContentLayout.setVisibility(View.GONE);
            mContentLayout1.setVisibility(View.VISIBLE);
        }

        surfaceview = findViewById(R.id.surfaceview);
        surfaceview1 = findViewById(R.id.surfaceview1);
        surfaceview2 = findViewById(R.id.surfaceview2);
        surfaceview3 = findViewById(R.id.surfaceview3);
        surfaceview4 = findViewById(R.id.surfaceview4);
        surfaceview5 = findViewById(R.id.surfaceview5);
        surfaceHolder = surfaceview.getHolder();
        surfaceHolder.addCallback(this);

        textureView = findViewById(R.id.textureview);
        textureView1 = findViewById(R.id.textureview1);
        textureView2 = findViewById(R.id.textureview2);
        textureView3 = findViewById(R.id.textureview3);
        textureView4 = findViewById(R.id.textureview4);
        textureView5 = findViewById(R.id.textureview5);
        textureView.setSurfaceTextureListener(this);
        HandlerThread handlerThread = new HandlerThread("camera");
        handlerThread.start();
        mCameraHanlder = new Handler(handlerThread.getLooper());
//        surfaceview2.getHolder().addCallback(this);

        avcDeCodec1 = new AvcDecoder();
        avcDeCodec2 = new AvcDecoder();
        avcDeCodec3 = new AvcDecoder();
        avcDeCodec4 = new AvcDecoder();
        avcDeCodec5 = new AvcDecoder();

        if (doSurfaceView) {
            avcDeCodec1.setSurface(surfaceview1.getHolder().getSurface());
            avcDeCodec2.setSurface(surfaceview2.getHolder().getSurface());
            avcDeCodec3.setSurface(surfaceview3.getHolder().getSurface());
            avcDeCodec4.setSurface(surfaceview4.getHolder().getSurface());
            avcDeCodec5.setSurface(surfaceview5.getHolder().getSurface());
        }

        avcCodec0 = new AvcEncoder();
        avcCodec1 = new AvcEncoder();
        avcCodec2 = new AvcEncoder();
        avcCodec3 = new AvcEncoder();

        avcCodec0.InitEncoder(3, 320, 180, 300, 30, 2, 0);
        avcCodec1.InitEncoder(3, 640, 360, 500, 30, 2, 1);
        avcCodec2.InitEncoder(3, 1280, 720, 700, 30, 2, 2);
        avcCodec3.InitEncoder(3, 1920, 1080, 2000, 30, 2, 3);
    }

    public void StartEncoderThread(){
        Thread EncoderThread = new Thread(new Runnable() {

            @SuppressLint("NewApi")
            @Override
            public void run() {
            isRuning = true;
            byte[] input = null;

            while (isRuning) {
                if (MainActivity.YUVQueue.size() >0){
                    Log.d(TAG, "MainActivity.YUVQueue poll start ");
                    input = MainActivity.YUVQueue.poll();
                    if (input != null) {
                        avcCodec0.putEncodeFrame(input);
                        avcCodec1.putEncodeFrame(input);
                        avcCodec2.putEncodeFrame(input);
                        avcCodec3.putEncodeFrame(input);
                    } else {
                        Log.d(TAG, "MainActivity.YUVQueue wait");
                        try {
                            Thread.sleep(33);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    Log.d(TAG, "MainActivity.YUVQueue poll end ");
                }
            }
            }
        });

        EncoderThread.start();
    }

    public void StopThread(){
        isRuning = false;
        avcCodec.StopEncoder();
    }

    private boolean checkPermissionAllGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                // 只要有一个权限没有被授予, 则直接返回 false
                return false;
            }
        }
        return true;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        switch (requestCode) {
            case CAMERA_OK:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //这里已经获取到了摄像头的权限，想干嘛干嘛了可以
                    init();
                } else {
                    showWaringDialog();
                }
                break;
            default:
                break;
        }
    }

    private void showWaringDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("警告！")
                .setMessage("请前往设置->应用->PermissionDemo->权限中打开相关权限，否则功能无法正常运行！")
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 一般情况下如果用户不授权的话，功能是无法运行的，做退出处理
                        finish();
                    }
                }).show();
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (null != camera) {
        	camera.setPreviewCallbackWithBuffer(null);
        	camera.stopPreview();
            camera.release();
            camera = null;
            StopThread();
        }
    }


    private long last_time = 0;
    private int fps = 0;

	@Override
	public void onPreviewFrame(byte[] data, android.hardware.Camera camera) {
		// TODO Auto-generated method stub
        Log.d(TAG, "onPreviewFrame start");
		putYUVData(data,data.length);

        long time=System.currentTimeMillis();
        if(time - last_time > 1000)
        {
//            Log.d(TAG, "frame rate: "+fps);
            textView.setText("采集帧率：" + fps);
            last_time = time;
            fps = 0;
        }
        fps++;
        mCamera.addCallbackBuffer(data);
        Log.d(TAG, "onPreviewFrame end");
	}

	private void sleep(long ms){
        try{
            Thread.sleep(ms);
        }catch (Exception e ){
        }
    }


	
	public void putYUVData(byte[] buffer, int length) {
        byte [] copy = new byte[width * height * 3/2];
        System.arraycopy(buffer, 0, copy, 0,buffer.length);
		if (YUVQueue.size() >= 10) {
			YUVQueue.poll();
		}
		YUVQueue.add(buffer);
	}
	
	@SuppressLint("NewApi")
	private boolean SupportAvcCodec(){
		if(Build.VERSION.SDK_INT>=18){
			for(int j = MediaCodecList.getCodecCount() - 1; j >= 0; j--){
				MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(j);
	
				String[] types = codecInfo.getSupportedTypes();
				for (int i = 0; i < types.length; i++) {
					if (types[i].equalsIgnoreCase("video/avc")) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private void closeCamera(Camera camera){
	    mCamera = camera;
        if(mCamera != null){
            try {
                mCamera.stopPreview();
                mCamera.setPreviewDisplay(null);
                mCamera.setPreviewCallbackWithBuffer(null);
                mCamera.release();
                mCamera = null;
                StopThread();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void closeCameraApi2() {
	    if (mCameraDevice != null) mCameraDevice.close();
	    if (mImageReader != null) mImageReader.close();
	    StopThread();
    }

    private static byte[] convertPlanes2NV21(int width, int height, ByteBuffer yPlane, ByteBuffer uPlane, ByteBuffer vPlane) {
        int totalSize = width * height * 3 / 2;
        byte[] nv21Buffer = new byte[totalSize];
        int len = yPlane.capacity();
        yPlane.get(nv21Buffer, 0, len);
        vPlane.get(nv21Buffer, len, vPlane.capacity());
        byte lastValue = uPlane.get(uPlane.capacity() - 1);
        nv21Buffer[totalSize - 1] = lastValue;
        return nv21Buffer;
    }

    private static byte[] convertPlanes2NV12(int width, int height, ByteBuffer yPlane, ByteBuffer uPlane, ByteBuffer vPlane) {
        int totalSize = width * height * 3 / 2;
        byte[] nv12Buffer = new byte[totalSize];
        int len = yPlane.capacity();
        yPlane.get(nv12Buffer, 0, len);
        uPlane.get(nv12Buffer, len, uPlane.capacity());
        byte lastValue = vPlane.get(vPlane.capacity() - 1);
        nv12Buffer[totalSize - 1] = lastValue;
        return nv12Buffer;
    }

    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {
            Log.d(TAG, "onImageAvailable start");
            Image image = imageReader.acquireNextImage();
            if (image != null) {
                byte[] nv12Buffer = convertPlanes2NV12(width, height, image.getPlanes()[0].getBuffer(),
                        image.getPlanes()[1].getBuffer(), image.getPlanes()[2].getBuffer());
                putYUVData(nv12Buffer, nv12Buffer.length);
                image.close();
            }
            runOnUiThread(new Runnable(){

                @Override
                public void run() {
                    long time=System.currentTimeMillis();
                    if(time - last_time > 1000)
                    {
//            Log.d(TAG, "frame rate: "+fps);
                        textView.setText("采集帧率：" + fps);
                        last_time = time;
                        fps = 0;
                    }
                    fps++;
                }
            });

            Log.d(TAG, "onImageAvailable end");
        }
    };

	private boolean doSurfaceView = false;
	private boolean doCameraApi1 = false;


	private CameraCaptureSession.StateCallback mCpStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            try {
                mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                CaptureRequest request = mPreviewBuilder.build();
                cameraCaptureSession.setRepeatingRequest(request, null, mCameraHanlder);
                mCameraCaptureSession = cameraCaptureSession;
            } catch (Exception e) {
                Log.e(TAG, "capture session config fail:" + e);
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

        }
    };

	private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            try {
                mCameraDevice = cameraDevice;
                Surface previewSurface = null;
                if (doSurfaceView) {
                    surfaceHolder.setFixedSize(width, height);
                    previewSurface = surfaceHolder.getSurface();
                } else {
                    SurfaceTexture sf = textureView.getSurfaceTexture();
                    sf.setDefaultBufferSize(width, height);
                    previewSurface = new Surface(sf);
                }
                mPreviewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                mPreviewBuilder.addTarget(previewSurface);
                mPreviewBuilder.addTarget(mImageReader.getSurface());
                cameraDevice.createCaptureSession(Arrays.asList(previewSurface, mImageReader.getSurface()), mCpStateCallback, mCameraHanlder);
            } catch (Exception e) {
                Log.d(TAG, "start preview fail:" + e);
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {

        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {

        }
    };

    private void startCameraApi2() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = cameraManager.getCameraIdList()[0];
            mImageReader = ImageReader.newInstance(width, height,ImageFormat.YUV_420_888, yuvqueuesize);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mCameraHanlder);
            cameraManager.openCamera(cameraId, mStateCallback, mCameraHanlder);
        }  catch (SecurityException e) {
             Log.e(TAG, "SecurityException");
        } catch (Exception e) {
            Log.e(TAG, "open camera fail:" + e);
        }
    }

    private void startcamera(Camera camera){
	    mCamera = camera;
        if(mCamera != null){
            try {
                for (int i = 0; i< yuvqueuesize; i++)
                    mCamera.addCallbackBuffer(mDataBuffer[i]);
                mCamera.setPreviewCallbackWithBuffer(this);
                if(parameters == null){
                    parameters = mCamera.getParameters();
                }
                parameters = mCamera.getParameters();
                parameters.setPreviewFormat(ImageFormat.NV21);
                parameters.setPreviewSize(width, height);
                mCamera.setParameters(parameters);
                mCamera.setPreviewDisplay(surfaceHolder);
                mCamera.startPreview();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @TargetApi(9)
	private Camera getBackCamera() {
        Camera c = null;
        try {
            c = Camera.open(0); // attempt to get a Camera instance
        } catch (Exception e) {
            e.printStackTrace();
            Log.d(TAG, "startcamera");
        }
        return c; // returns null if camera is unavailable
    }

    private Camera getFontCamera() {
        Camera c = null;
        try {
            c = Camera.open(1); // attempt to get a Camera instance
        } catch (Exception e) {
            e.printStackTrace();
        }
        return c; // returns null if camera is unavailable
    }

    private void switchCamera(int i){
        int count = Camera.getNumberOfCameras();
        if(i > Camera.getNumberOfCameras()
                || i == currenCamera){
            return;
        }

        if(camera != null){
            closeCamera(camera);
        }

        if(i == 0) {
            camera = getBackCamera();
            startcamera(camera);
            currenCamera = 0;

        }
        else if(i == 1){
            camera = getFontCamera();
            startcamera(camera);
            currenCamera = 1;
        }
    }


    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
        mSurfaceTexture = surfaceTexture;
        //startCameraApi2();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        if (mCamera != null)
            closeCamera(mCamera);
        closeCameraApi2();
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

    }
}
