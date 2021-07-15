package com.yny.mlocr

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.text.TextUtils
import android.util.Log
import android.util.Size
import androidx.appcompat.app.AppCompatActivity
import com.yny.mlocr.camera2.Constants
import java.util.*

/**
 * Camera2 工具类
 *
 * @author nianyi.yang
 * create on 2021/7/15 15:16
 */
object Camera2Utils {

    /**
     * Step1 获取 CameraId
     */
    fun chooseCamera(cameraManager: CameraManager): String? {
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

    /**
     * The camera preview size will be chosen to be the smallest frame by pixel size capable of
     * containing a DESIRED_SIZE x DESIRED_SIZE square.
     */
    private const val MINIMUM_PREVIEW_SIZE = 320

    /**
     * Given `choices` of `Size`s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the minimum of both, or an exact match if possible.
     *
     * @param choices The list of sizes that the camera supports for the intended output class
     * @param width The minimum desired width
     * @param height The minimum desired height
     * @return The optimal `Size`, or an arbitrary one if none were big enough
     */
    fun chooseOptimalSize(choices: Array<Size>, width: Int, height: Int): Size {
        val minSize = Math.max(Math.min(width, height), MINIMUM_PREVIEW_SIZE)
        val desiredSize = Size(width, height)

        // Collect the supported resolutions that are at least as big as the preview Surface
        var exactSizeFound = false
        val bigEnough: MutableList<Size?> = ArrayList()
        val tooSmall: MutableList<Size?> = ArrayList()
        for (option in choices) {
            if (option == desiredSize) {
                // Set the size but don't return yet so that remaining sizes will still be logged.
                exactSizeFound = true
            }
            if (option.height >= minSize && option.width >= minSize) {
                bigEnough.add(option)
            } else {
                tooSmall.add(option)
            }
        }
        Log.i(Constants.TAG, "Desired size: " + desiredSize + ", min size: " + minSize + "x" + minSize)
        Log.i(
            Constants.TAG,
            "Valid preview sizes: [" + TextUtils.join(
                ", ",
                bigEnough
            ) + "]"
        )
        Log.i(
            Constants.TAG,
            "Rejected preview sizes: [" + TextUtils.join(
                ", ",
                tooSmall
            ) + "]"
        )
        if (exactSizeFound) {
            Log.i(Constants.TAG, "Exact size match found.")
            return desiredSize
        }

        // Pick the smallest of those, assuming we found any
        return if (bigEnough.size > 0) {
            val chosenSize = Collections.min(bigEnough, CompareSizesByArea())!!
            Log.i(Constants.TAG, "Chosen size: " + chosenSize.width + "x" + chosenSize.height)
            chosenSize
        } else {
            Log.e(Constants.TAG, "Couldn't find any suitable preview size")
            choices[0]
        }
    }
}

/** Compares two `Size`s based on their areas.  */
class CompareSizesByArea : Comparator<Size?> {
    override fun compare(lhs: Size?, rhs: Size?): Int {
        // We cast here to ensure the multiplications won't overflow
        if (lhs == null || rhs == null) {
            return 0
        }
        return java.lang.Long.signum(
            lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height
        )
    }
}