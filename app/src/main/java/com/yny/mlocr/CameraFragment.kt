package com.yny.mlocr

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.yny.mlocr.camera2.AutoFitTextureView
import com.yny.mlocr.camera2.ConnectionCallback
import com.yny.mlocr.camera2.Constants
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * 摄像机预览
 * 1. 初始化 SurfaceView / TextureView，获取 Surface
 * 2. 获取系统 Camera 服务，打开摄像头（摄像头权限已获取的前提下）
 * 3. 打开摄像头后通过回调获取到 CameraDevice，创建 CameraPreviewSession
 * 4. 创建 Session 成功后，通过 setRepeatingRequest 实时预览
 *
 * 实时帧截取
 * Camera2 - 实质上是创建了一个 reader 去获取预览帧
 * Camera - 直接通过 Camera.PreviewCallback 回调获取预览帧
 *
 * @author nianyi.yang
 * create on 2021/7/15 17:19
 */
class CameraFragment : Fragment() {

    companion object {
        fun newInstance(): CameraFragment {
            return CameraFragment()
        }
    }

    private var textureView: AutoFitTextureView? = null

    private var cameraId: String? = null
    private var sensorOrientation: Int? = null
    private var previewSize: Size? = null

    // 判断 camera 状态的锁
    private val cameraOpenCloseLock = Semaphore(1)

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    // 创建 CameraPreviewSession 时构建参数
    private var captureRequestBuilder: CaptureRequest.Builder? = null
    private var captureRequest: CaptureRequest? = null

    // 获取实时帧的 Reader
    private var previewReader: ImageReader? = null

    // 关联异步线程去处理图像
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    // 获取实时帧的回调
    private var imageListener: OnImageAvailableListener? = null

    private var cameraConnectionCallback: ConnectionCallback? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        textureView = view.findViewById(R.id.texture)
    }

    override fun onResume() {
        super.onResume()
        startBackgroundThread()
        prepare()
    }

    override fun onPause() {
        closeCamera()
        stopBackgroundThread()
        super.onPause()
    }

    fun setImageListener(listener: OnImageAvailableListener) {
        imageListener = listener
    }

    fun setCameraConnectionCallback(callback: ConnectionCallback) {
        cameraConnectionCallback = callback
    }

    private fun prepare() {
        textureView?.let {
            if (it.isAvailable) {
                openCamera(it.width, it.height)
            } else {
                it.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
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
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(width: Int, height: Int) {
        val cameraManager = getCameraManager()
        // 设置 Camera 的输出参数
        setUpCameraOutputs()
        // 适配输出 Size 与 Surface 大小
        configureTransform(width, height)
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            if (cameraId != null) {
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
            previewReader?.close()
            previewReader = null
        } catch (e: InterruptedException) {
            throw java.lang.RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    private fun setUpCameraOutputs() {
        val cameraManager = getCameraManager()

        try {
            cameraId = Camera2Utils.chooseCamera(cameraManager)
            if (cameraId != null) {
                val characteristics: CameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId!!)
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                previewSize = Camera2Utils.chooseOptimalSize(
                    map!!.getOutputSizes(SurfaceTexture::class.java),
                    Constants.DESIRED_PREVIEW_SIZE.width,
                    Constants.DESIRED_PREVIEW_SIZE.height
                )

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                val orientation = resources.configuration.orientation
                if (previewSize != null) {
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        textureView?.setAspectRatio(previewSize!!.width, previewSize!!.height)
                    } else {
                        textureView?.setAspectRatio(previewSize!!.height, previewSize!!.width)
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

        // 预览 size 设置完成回调
        cameraConnectionCallback?.onPreviewSizeChosen(previewSize, sensorOrientation)
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
        if (null == textureView || null == previewSize || activity == null) {
            return
        }
        val rotation = activity!!.windowManager.defaultDisplay.rotation
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
        textureView?.setTransform(matrix)
    }

    private fun createCameraPreviewSession() {
        try {
            // We configure the size of default buffer to be the size of camera preview we want.
            if (previewSize != null) {
                // We set up a CaptureRequest.Builder with the output Surface.
                captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                val previewSurface = createPreviewSurface(previewSize!!)
                captureRequestBuilder?.addTarget(previewSurface)
                val captureSurface = createCaptureSurface(previewSize!!)
                captureRequestBuilder?.addTarget(captureSurface)

                Log.i(Constants.TAG, "Opening camera preview: " + previewSize!!.width + "x" + previewSize!!.height)

                // Here, we create a CameraCaptureSession for camera preview.
                cameraDevice?.createCaptureSession(
                    listOf(previewSurface, captureSurface),
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
                                captureRequestBuilder?.set<Int>(
                                    CaptureRequest.CONTROL_AF_MODE,
                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                )
                                // Flash is automatically enabled when necessary.
                                captureRequestBuilder?.set<Int>(
                                    CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                                )

                                // Finally, we start displaying the camera preview.
                                captureRequest = captureRequestBuilder?.build()
                                if (captureRequest != null) {
                                    captureSession?.setRepeatingRequest(
                                        captureRequest!!, captureCallback, backgroundHandler
                                    )
                                }
                            } catch (e: CameraAccessException) {
                                Log.e(Constants.TAG, "CameraAccessException!")
                            }
                        }

                        override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                            Log.e(Constants.TAG, "Failed!")
                        }
                    },
                    null
                )
            }
        } catch (e: CameraAccessException) {
            Log.e(Constants.TAG, "CameraAccessException!")
        }
    }

    private fun createPreviewSurface(previewSize: Size): Surface {
        val texture = textureView?.surfaceTexture
        texture?.setDefaultBufferSize(previewSize.width, previewSize.height)

        // This is the output Surface we need to start preview.
        return Surface(texture)
    }

    private fun createCaptureSurface(previewSize: Size): Surface {

        // Create the reader for the preview frames.
        previewReader = ImageReader.newInstance(previewSize.width, previewSize.height, ImageFormat.YUV_420_888, 2)
        previewReader!!.setOnImageAvailableListener(imageListener, backgroundHandler)

        // This is the output Surface we need to start preview.
        return previewReader!!.surface
    }

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
            Log.e(Constants.TAG, "InterruptedException!")
        }
    }

    private fun getCameraManager(): CameraManager {
        if (context == null) {
            throw RuntimeException("当前 Fragment 的 Context 对象为 null！")
        }
        return context!!.getSystemService(AppCompatActivity.CAMERA_SERVICE) as CameraManager
    }

    private val stateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cd: CameraDevice) {
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
        }
    }

    private val captureCallback: CameraCaptureSession.CaptureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureProgressed(session: CameraCaptureSession, request: CaptureRequest, partialResult: CaptureResult) {}

        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {}
    }
}