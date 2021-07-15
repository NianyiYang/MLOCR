package com.yny.mlocr.camera

import android.content.Context
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import com.yny.mlocr.camera.CameraUtils.getBestOptimalSize
import com.yny.mlocr.camera.CameraUtils.getCameraId
import com.yny.mlocr.camera.CameraUtils.setCameraSizeByOrientation
import java.io.IOException

/**
 * 使用 camera2 实现的视频预览
 *
 * @author nianyi.yang
 * create on 2021/7/15 10:54
 */
class Camera2Preview : SurfaceView, SurfaceHolder.Callback {
    companion object {
        const val TAG = "Camera2Preview"
    }

    private var mCamera: Camera? = null
    private var mCameraId = 0

    constructor(context: Context?) : super(context)

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private fun attachSurfaceView() {
        isFocusable = true
        isFocusableInTouchMode = true
        holder.removeCallback(this)
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        prepareCamera(holder)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        openCamera(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // 应用内切换横屏时，会走这个回调
        releaseCamera()
    }

    fun getCamera(): Camera? {
        return mCamera
    }

    fun getCameraId(): Int {
        return mCameraId
    }

    private fun prepareCamera(holder: SurfaceHolder) {
        try {
            mCamera!!.setPreviewDisplay(holder)
            mCamera!!.startPreview()
        } catch (e: IOException) {
            Log.e(TAG, "Error setting camera preview: " + e.message)
        }
    }

    fun attachCamera(cameraId: Int) {
        mCameraId = cameraId
        mCamera = Camera.open(cameraId)
        attachSurfaceView()
    }

    fun openCamera(width: Int, height: Int) {
        if (holder?.surface == null) {
            return
        }

        // 在修改设置前停止preview
        try {
            mCamera!!.stopPreview()
            mCamera!!.setDisplayOrientation(getDisplayOrientation())
            val params = mCamera!!.parameters
            val sizes = mCamera!!.parameters.supportedPreviewSizes
            val realSize = setCameraSizeByOrientation(width, height)
            val optimalSize = getBestOptimalSize(sizes, realSize[0], realSize[1])
            if (optimalSize != null) {
                params.setPreviewSize(optimalSize.width, optimalSize.height)
                mCamera!!.parameters = params
                mCamera!!.setPreviewDisplay(holder)
                mCamera!!.startPreview()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error starting camera preview: " + e.message)
        }
    }

    private fun releaseCamera() {
        Log.d(TAG, "surfaceDestroyed(SurfaceHolder")
    }

    /**
     * 切换摄像头
     */
    fun changeCamera(isFrontCamera: Boolean, width: Int, height: Int) {
        if (mCamera != null) {
            mCamera!!.release()
        }
        val cameraId: Int?
        cameraId = if (isFrontCamera) {
            getCameraId(false)
        } else {
            getCameraId(true)
        }
        if (cameraId != null) {
            attachCamera(cameraId)
            openCamera(width, height)
        }
    }

    /**
     * 切换摄像头
     */
    fun changeCamera(width: Int, height: Int) {
        var isFrontCamera = false
        if (mCamera != null) {
            val info = CameraInfo()
            Camera.getCameraInfo(mCameraId, info)
            isFrontCamera = info.facing == CameraInfo.CAMERA_FACING_FRONT
            mCamera!!.release()
        }
        val cameraId: Int?
        cameraId = if (isFrontCamera) {
            getCameraId(false)
        } else {
            getCameraId(true)
        }
        if (cameraId != null) {
            attachCamera(cameraId)
            openCamera(width, height)
        }
    }

    /**
     * 获取当前适合的渲染方向
     *
     * @return degree角度 0 - 360
     */
    fun getDisplayOrientation(): Int {
        val display = (getContext().getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val rotation = display.rotation
        var degrees = 0
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
            else -> {
            }
        }

        val info = CameraInfo()
        Camera.getCameraInfo(mCameraId, info)

        var result: Int
        if (info.facing == CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360
            result = (360 - result) % 360
        } else {
            result = (info.orientation - degrees + 360) % 360
        }

        return result
    }
}