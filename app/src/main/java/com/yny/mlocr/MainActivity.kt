package com.yny.mlocr

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.Image
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yny.mlocr.camera2.ConnectionCallback
import com.yny.mlocr.camera2.Constants
import com.yny.mlocr.camera2.ImageHelper
import com.yny.mlocr.paddlelite.PredictorManager
import java.util.*

/**
 * 主页面
 *
 * @author nianyi.yang
 * create on 2021/7/15 10:39
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_CAMERA = Manifest.permission.CAMERA
        private const val PERMISSIONS_REQUEST = 1
    }

    private var isProcessingFrame = false
    private val yuvBytes = arrayOfNulls<ByteArray>(3)
    private var yRowStride = 0
    private var rgbBytes: IntArray? = null

    private var handler: Handler? = null
    private var handlerThread: HandlerThread? = null

    private var imageConverter: Runnable? = null
    private var postInferenceCallback: Runnable? = null

    private var rgbFrameBitmap: Bitmap? = null

    private val tvResult: TextView by lazy { findViewById(R.id.tv_result) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (hasPermission()) {
            openFragment()
        } else {
            requestPermission()
        }

        PredictorManager.init(this)
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        handlerThread = HandlerThread("inference")
        handlerThread?.let {
            it.start()
            handler = Handler(it.looper)
        }
    }

    @Synchronized
    override fun onPause() {
        handlerThread?.quitSafely()
        try {
            handlerThread?.join()
            handlerThread = null
            handler = null
        } catch (e: InterruptedException) {
            Log.e(Constants.TAG, "InterruptedException")
        }
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST) {
            if (allPermissionsGranted(grantResults)) {
                openFragment()
            } else {
                requestPermission()
            }
        }
    }

    @Synchronized
    private fun runInBackground(r: Runnable) {
        handler?.post(r)
    }

    private fun openFragment() {
        val fragment = CameraFragment.newInstance().apply {
            setCameraConnectionCallback(object : ConnectionCallback {
                override fun onPreviewSizeChosen(size: Size?, cameraRotation: Int?) {
                    // 暂时定死高宽
                    rgbFrameBitmap = Bitmap.createBitmap(
                        Constants.DESIRED_PREVIEW_SIZE.width,
                        Constants.DESIRED_PREVIEW_SIZE.height,
                        Bitmap.Config.ARGB_8888
                    )
                }
            })
            setImageListener {
                // We need wait until we have some size from onPreviewSizeChosen
                val previewWidth = Constants.DESIRED_PREVIEW_SIZE.width
                val previewHeight = Constants.DESIRED_PREVIEW_SIZE.height
                if (previewWidth == 0 || previewHeight == 0) {
                    return@setImageListener
                }

                rgbBytes = IntArray(previewWidth * previewHeight)
                try {
                    val image: Image = it.acquireLatestImage()
                    if (isProcessingFrame) {
                        image.close()
                        return@setImageListener
                    }
                    isProcessingFrame = true

                    val planes = image.planes
                    ImageHelper.fillBytes(planes, yuvBytes)
                    yRowStride = planes[0].rowStride
                    val uvRowStride = planes[1].rowStride
                    val uvPixelStride = planes[1].pixelStride
                    imageConverter = Runnable {
                        ImageHelper.convertYUV420ToARGB8888(
                            yuvBytes[0]!!,
                            yuvBytes[1]!!,
                            yuvBytes[2]!!,
                            previewWidth,
                            previewHeight,
                            yRowStride,
                            uvRowStride,
                            uvPixelStride,
                            rgbBytes!!
                        )
                    }
                    postInferenceCallback = Runnable {
                        image.close()
                        isProcessingFrame = false
                    }
                    processImage()
                } catch (e: Exception) {
                    Log.e(Constants.TAG, "Exception!")
                    return@setImageListener
                }
            }
        }
        supportFragmentManager.beginTransaction().replace(R.id.fl_camera, fragment).commit()
    }

    private fun processImage() {
        // 处理图片
        Log.i(Constants.TAG, "获取到图片帧！")
        val previewWidth = Constants.DESIRED_PREVIEW_SIZE.width
        val previewHeight = Constants.DESIRED_PREVIEW_SIZE.height
        rgbFrameBitmap?.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight)
        val cropSize = Math.min(previewWidth, previewHeight)

        runInBackground {
            val result = if (PredictorManager.onImageChanged(rgbFrameBitmap)) {
                PredictorManager.getOutputResult()
            } else {
                "Failed"
            }
            runOnUiThread {
                tvResult.text = result
            }
        }

        // 标识处理完成，开始处理下一帧
        readyForNextImage()
    }

    private fun getRgbBytes(): IntArray? {
        imageConverter?.run()
        return rgbBytes
    }

    private fun readyForNextImage() {
        postInferenceCallback?.run()
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
                    "请打开摄像头权限！",
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
}