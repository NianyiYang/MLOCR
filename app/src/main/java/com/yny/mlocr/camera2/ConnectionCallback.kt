package com.yny.mlocr.camera2

import android.util.Size

/**
 * Callback for Activities to use to initialize their data once the selected preview size is
 * known.
 *
 * @author nianyi.yang
 * create on 2021/7/15 18:18
 */
interface ConnectionCallback {
    fun onPreviewSizeChosen(size: Size?, cameraRotation: Int?)
}