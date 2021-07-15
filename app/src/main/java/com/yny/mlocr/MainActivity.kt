package com.yny.mlocr

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.media.browse.MediaBrowser
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView.SurfaceTextureListener
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * 主页面
 *
 * @author nianyi.yang
 * create on 2021/7/15 10:39
 */
class MainActivity : AppCompatActivity() {

    companion object {
        val DESIRED_PREVIEW_SIZE = Size(640, 480)

        private const val PERMISSION_CAMERA = Manifest.permission.CAMERA
        private const val PERMISSIONS_REQUEST = 1
    }

    private fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA)) {
                Toast.makeText(
                    this@MainActivity,
                    "Camera permission is required for this demo",
                    Toast.LENGTH_LONG
                ).show()
            }
            requestPermissions(
                arrayOf(PERMISSION_CAMERA),
                PERMISSIONS_REQUEST
            )
        }
    }

    private fun allPermissionsGranted(grantResults: IntArray): Boolean {
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST) {
            if (allPermissionsGranted(grantResults)) {
                // do
                start()
            } else {
                requestPermission()
            }
        }
    }

    private val textureView by lazy {
        findViewById<AutoFitTextureView>(R.id.texture)
    }

    private var cameraId: String? = null
    private var sensorOrientation: Int? = null
    private var previewSize: Size? = null

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    // TODO 搞明白这个
    private val cameraOpenCloseLock = Semaphore(1)

    private val cameraConnectionCallback: ConnectionCallback = object : ConnectionCallback {
        override fun onPreviewSizeChosen(size: Size?, cameraRotation: Int?) {

        }
    }

    private val stateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cd: CameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            cameraOpenCloseLock.release()
            cameraDevice = cd
            createCameraPreviewSession()
        }

        override fun onDisconnected(cd: CameraDevice) {
            cameraOpenCloseLock.release()
            cd.close()
            cameraDevice = null
        }

        override fun onError(cd: CameraDevice, error: Int) {
            cameraOpenCloseLock.release()
            cd.close()
            cameraDevice = null
            finish()
        }
    }

    private val captureCallback: CaptureCallback = object : CaptureCallback() {
        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
        }
    }

    private val surfaceTextureListener: SurfaceTextureListener = object : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            openCamera(width, height)
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (hasPermission()) {
            start()
        } else {
            requestPermission()
        }
    }

    fun start() {
        if (textureView.isAvailable) {
            openCamera(textureView.width, textureView.height)
        } else {
            textureView.surfaceTextureListener = surfaceTextureListener
        }
    }

    fun stop() {
        closeCamera()
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        start()
    }

    override fun onPause() {
        stop()
        stopBackgroundThread()
        super.onPause()
    }

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    /** Starts a background thread and its [Handler].  */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("ImageListener")
        backgroundThread?.let {
            it.start()
            backgroundHandler = Handler(it.looper)
        }

    }

    /** Stops the background thread and its [Handler].  */
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(Constants.TAG, "Exception!")
        }
    }

    /**
     * Step1 获取 CameraId
     */
    fun chooseCamera(): String? {
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in cameraManager.cameraIdList) {

                if (cameraId == null) {
                    continue
                }

                val characteristics = cameraManager.getCameraCharacteristics(cameraId)

                // 这里不使用前置摄像头
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue
                }

                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue

                return cameraId
            }
        } catch (e: CameraAccessException) {
            Log.e(Constants.TAG, "Not allowed to access camera")
        }

        return null
    }

    /** Opens the camera specified by [CameraConnectionFragment.cameraId].  */
    private fun openCamera(width: Int, height: Int) {
        setUpCameraOutputs()
        configureTransform(width, height)
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            if (cameraId != null) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    return
                }
                cameraManager.openCamera(cameraId!!, stateCallback, backgroundHandler)
            }
        } catch (e: CameraAccessException) {
            Log.e(Constants.TAG, "CameraAccessException!")
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }
    }

    /** Closes the current [CameraDevice].  */
    private fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
//            if (null != previewReader) {
//                previewReader.close()
//                previewReader = null
//            }
        } catch (e: InterruptedException) {
            throw java.lang.RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    private fun setUpCameraOutputs() {
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

        // step2 配置 surface
        try {
            cameraId = chooseCamera()
            if (cameraId != null) {
                val characteristics: CameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId!!)
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                previewSize = Camera2Utils.chooseOptimalSize(
                    map!!.getOutputSizes(SurfaceTexture::class.java),
                    DESIRED_PREVIEW_SIZE.width,
                    DESIRED_PREVIEW_SIZE.height
                )

                // We fit the aspect ratio of TextureView to the size of preview we picked.

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                val orientation = resources.configuration.orientation
                if (previewSize != null) {
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        textureView.setAspectRatio(previewSize!!.width, previewSize!!.height)
                    } else {
                        textureView.setAspectRatio(previewSize!!.height, previewSize!!.width)
                    }
                }
            }
        } catch (e: CameraAccessException) {
            Log.e(Constants.TAG, "CameraAccessException!")
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            throw IllegalStateException("This device doesn\'t support Camera2 API.");
        }

        cameraConnectionCallback.onPreviewSizeChosen(previewSize, sensorOrientation)
    }

    /**
     * Configures the necessary [Matrix] transformation to `mTextureView`. This method should be
     * called after the camera preview size is determined in setUpCameraOutputs and also the size of
     * `mTextureView` is fixed.
     *
     * @param viewWidth The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        if (null == textureView || null == previewSize) {
            return
        }
        val rotation = windowManager.defaultDisplay.rotation
        val matrix = Matrix()
        val viewRect = RectF(0F, 0F, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0F, 0F, previewSize!!.height.toFloat(), previewSize!!.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                viewHeight.toFloat() / previewSize!!.height,
                viewWidth.toFloat() / previewSize!!.width
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        textureView.setTransform(matrix)
    }

    private var previewRequestBuilder: CaptureRequest.Builder? = null
    private var previewRequest: CaptureRequest? = null

    /** Creates a new [CameraCaptureSession] for camera preview.  */
    private fun createCameraPreviewSession() {
        try {
            val texture = textureView.surfaceTexture

            // We configure the size of default buffer to be the size of camera preview we want.
            if (previewSize != null) {
                texture?.setDefaultBufferSize(previewSize!!.width, previewSize!!.height)
            }

            // This is the output Surface we need to start preview.
            val surface = Surface(texture)

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder?.addTarget(surface)
            Log.i(Constants.TAG, "Opening camera preview: " + previewSize!!.width + "x" + previewSize!!.height)

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice?.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        // The camera is already closed
                        if (null == cameraDevice) {
                            return
                        }

                        // When the session is ready, we start displaying the preview.
                        captureSession = cameraCaptureSession
                        try {
                            // Auto focus should be continuous for camera preview.
                            previewRequestBuilder?.set<Int>(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )
                            // Flash is automatically enabled when necessary.
                            previewRequestBuilder?.set<Int>(
                                CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                            )

                            // Finally, we start displaying the camera preview.
                            previewRequest = previewRequestBuilder?.build()
                            if (previewRequest != null) {
                                captureSession?.setRepeatingRequest(
                                    previewRequest!!, captureCallback, backgroundHandler
                                )
                            }
                        } catch (e: CameraAccessException) {
                            Log.e(Constants.TAG, "Exception!")
                        }
                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        Log.e(Constants.TAG, "Failed!")
                    }
                },
                null
            )
        } catch (e: CameraAccessException) {
            Log.e(Constants.TAG, "Exception!")
        }
    }

    /**
     * Callback for Activities to use to initialize their data once the selected preview size is
     * known.
     */
    interface ConnectionCallback {
        fun onPreviewSizeChosen(size: Size?, cameraRotation: Int?)
    }
}