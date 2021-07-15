package com.yny.mlocr.camera;

import android.content.Context;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import java.io.IOException;
import java.util.List;

/**
 * 摄像头 srufaceview
 *
 * @author nianyi.yang
 * create on 2021/7/15 10:50
 */
public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    private static final String TAG = CameraPreview.class.getSimpleName();

    private final SurfaceHolder mHolder;
    private Camera mCamera;
    private int mCameraId;

    public CameraPreview(Context context) {
        super(context);
        mHolder = getHolder();
    }

    public CameraPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        mHolder = getHolder();
    }

    public CameraPreview(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mHolder = getHolder();
    }

    private void attachSurfaceView() {
        setFocusable(true);
        setFocusableInTouchMode(true);
        this.mHolder.removeCallback(this);
        this.mHolder.addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        prepareCamera(holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        openCamera(width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // 应用内切换横屏时，会走这个回调
        releaseCamera();
    }

    public Camera getCamera() {
        return mCamera;
    }

    public int getCameraId() {
        return mCameraId;
    }

    private void prepareCamera(SurfaceHolder holder) {
        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.startPreview();
        } catch (IOException e) {
            Log.e(TAG, "Error setting camera preview: " + e.getMessage());
        }
    }

    public void attachCamera(int cameraId) {
        mCameraId = cameraId;
        mCamera = Camera.open(cameraId);
        attachSurfaceView();
    }

    public void openCamera(int width, int height) {
        if (mHolder.getSurface() == null) {
            return;
        }

        // 在修改设置前停止preview
        try {
            mCamera.stopPreview();
            mCamera.setDisplayOrientation(getDisplayOrientation());

            Camera.Parameters params = mCamera.getParameters();
            List<Camera.Size> sizes = mCamera.getParameters().getSupportedPreviewSizes();
            int[] realSize = CameraUtils.INSTANCE.setCameraSizeByOrientation(width, height);
            Camera.Size optimalSize = CameraUtils.INSTANCE.getBestOptimalSize(sizes, realSize[0], realSize[1]);
            if (optimalSize != null) {
                params.setPreviewSize(optimalSize.width, optimalSize.height);
                mCamera.setParameters(params);
                mCamera.setPreviewDisplay(mHolder);
                mCamera.startPreview();
            }
        } catch (Exception e) {
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }

    private void releaseCamera() {
        Log.d(TAG, "surfaceDestroyed(SurfaceHolder");
    }

    /**
     * 切换摄像头
     */
    public void changeCamera(boolean isFrontCamera, int width, int height) {

        if (mCamera != null) {
            mCamera.release();
        }

        Integer cameraId;

        if (isFrontCamera) {
            cameraId = CameraUtils.INSTANCE.getCameraId(false);
        } else {
            cameraId = CameraUtils.INSTANCE.getCameraId(true);
        }

        if (cameraId != null) {
            attachCamera(cameraId);
            openCamera(width, height);
        }
    }

    /**
     * 切换摄像头
     */
    public void changeCamera(int width, int height) {

        boolean isFrontCamera = false;

        if (mCamera != null) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(mCameraId, info);
            isFrontCamera = info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT;
            mCamera.release();
        }

        Integer cameraId;

        if (isFrontCamera) {
            cameraId = CameraUtils.INSTANCE.getCameraId(false);
        } else {
            cameraId = CameraUtils.INSTANCE.getCameraId(true);
        }

        if (cameraId != null) {
            attachCamera(cameraId);
            openCamera(width, height);
        }
    }

    /**
     * 获取当前适合的渲染方向
     *
     * @return degree角度 0 - 360
     */
    public int getDisplayOrientation() {
        Display display = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int rotation = display.getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
            default:
                break;
        }

        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(mCameraId, info);

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            result = (info.orientation - degrees + 360) % 360;
        }

        return result;
    }
}
