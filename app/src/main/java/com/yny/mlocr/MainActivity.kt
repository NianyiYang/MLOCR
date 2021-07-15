package com.yny.mlocr

import android.Manifest
import android.content.pm.PackageManager
import android.media.Image
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.yny.mlocr.camera2.Constants
import com.yny.mlocr.camera2.ImageHelper
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

    private var imageConverter: Runnable? = null
    private var postInferenceCallback: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (hasPermission()) {
            openFragment()
        } else {
            requestPermission()
        }
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

    private fun openFragment() {
        val fragment = CameraFragment.newInstance().apply {
            setImageListener {
                // We need wait until we have some size from onPreviewSizeChosen
                val previewWidth = Constants.DESIRED_PREVIEW_SIZE.width
                val previewHeight = Constants.DESIRED_PREVIEW_SIZE.height
                if (previewWidth == 0 || previewHeight == 0) {
                    return@setImageListener
                }

                val rgbBytes = IntArray(previewWidth * previewHeight)
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
                            rgbBytes
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
        Log.i(Constants.TAG, "获取到图片帧！")
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