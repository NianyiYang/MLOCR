package com.yny.mlocr.camera

import android.content.res.Configuration
import android.content.res.Resources
import android.hardware.Camera
import kotlin.math.abs

/**
 * 工具方法
 *
 * @author nianyi.yang
 * create on 2021/7/15 10:51
 */
object CameraUtils {

    private const val ASPECT_TOLERANCE = 0.1

    /**
     * 获取摄像头预览对应给定的宽高的最佳适配
     */
    fun getBestOptimalSize(sizes: List<Camera.Size>, w: Int, h: Int): Camera.Size? {
        val targetRatio = h.toDouble() / w
        var optimalSize: Camera.Size? = null
        var minDiff: Double = java.lang.Double.MAX_VALUE
        for (size in sizes) {
            val ratio = size.width.toDouble() / size.height
            if (abs(ratio - targetRatio) > ASPECT_TOLERANCE) {
                continue
            }
            if (abs(size.height - h) < minDiff) {
                optimalSize = size
                minDiff = abs(size.height - h).toDouble()
            }
        }
        if (optimalSize == null) {
            minDiff = java.lang.Double.MAX_VALUE
            for (size in sizes) {
                if (abs(size.height - h) < minDiff) {
                    optimalSize = size
                    minDiff = abs(size.height - h).toDouble()
                }
            }
        }
        return optimalSize
    }

    /**
     * 获取摄像头ID
     *
     * @param isFront 前置摄像头
     */
    fun getCameraId(isFront: Boolean): Int? {
        return if (isFront) {
            getFacingFrontCameraId()
        } else {
            getFacingBackCameraId()
        }
    }

    private fun getFacingFrontCameraId(): Int? {
        try {
            val cameraInfo = Camera.CameraInfo()
            for (i in 0 until Camera.getNumberOfCameras()) {
                Camera.getCameraInfo(i, cameraInfo) // 得到每一个摄像头的信息
                // 取前置摄像头
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    return i
                }
            }
        } catch (e: RuntimeException) {
            e.printStackTrace()
        }
        return null
    }

    private fun getFacingBackCameraId(): Int? {
        try {
            val cameraInfo = Camera.CameraInfo()
            for (i in 0 until Camera.getNumberOfCameras()) {
                Camera.getCameraInfo(i, cameraInfo) // 得到每一个摄像头的信息
                // 取后置摄像头
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    return i
                }
            }
        } catch (e: RuntimeException) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * 设置横屏时采样 size
     */
    fun setCameraSizeByOrientation(width: Int, height: Int): IntArray {
        val realSize = IntArray(2)
        if (isLandscape()) {
            realSize[0] = height
            realSize[1] = width
        } else {
            realSize[0] = width
            realSize[1] = height
        }
        return realSize
    }

    private fun isLandscape(): Boolean {
        return Resources.getSystem().configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }
}