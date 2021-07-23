package com.yny.mlocr.baidunewdemo

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.yny.mlocr.R
import com.yny.mlocr.paddlelite.PredictorManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Baidu 视频流的 OCR
 *
 * @author nianyi.yang
 * create on 2021/7/22 19:57
 */
class BaiduNewCameraActivity : Activity(), View.OnClickListener, CameraSurfaceView.OnTextureChangedListener {
    private val TAG: String = BaiduNewCameraActivity::class.java.getSimpleName()

    var svPreview: CameraSurfaceView? = null
    var tvStatus: TextView? = null
    var btnSwitch: ImageButton? = null
    var btnShutter: ImageButton? = null

    var savedImagePath = ""
    var lastFrameIndex = 0
    var lastFrameTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_baidu_new_camera)

        // Init the camera preview and UI components
        initView()

        // Check and request CAMERA and WRITE_EXTERNAL_STORAGE permissions
        if (!checkAllPermissions()) {
            requestAllPermissions()
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btn_switch -> svPreview!!.switchCamera()
            R.id.btn_shutter -> {
                val date = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")
                synchronized(this) {
                    savedImagePath = Utils.getDCIMDirectory().toString() + File.separator + date.format(Date()).toString() + ".png"
                }
                Toast.makeText(this@BaiduNewCameraActivity, "Save snapshot to $savedImagePath", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onTextureChanged(inTextureId: Int, outTextureId: Int, textureWidth: Int, textureHeight: Int): Boolean {
        var savedImagePath = ""
        synchronized(this) { savedImagePath = this@BaiduNewCameraActivity.savedImagePath }
        val result = if (PredictorManager.onImageChanged(rgbFrameBitmap)) {
            PredictorManager.getOutputResult()
        } else {
            "Failed"
        }
        val modified: Boolean = predictor.process(inTextureId, outTextureId, textureWidth, textureHeight, savedImagePath)
        if (!savedImagePath.isEmpty()) {
            synchronized(this) { this@BaiduNewCameraActivity.savedImagePath = "" }
        }
        lastFrameIndex++
        if (lastFrameIndex >= 30) {
            val fps = (lastFrameIndex * 1e9 / (System.nanoTime() - lastFrameTime)).toInt()
            runOnUiThread { tvStatus!!.text = Integer.toString(fps) + "fps" }
            lastFrameIndex = 0
            lastFrameTime = System.nanoTime()
        }
        return modified
    }

    override fun onResume() {
        super.onResume()
        // Initialize the predictor
        PredictorManager.init(this)
        // Open camera until the permissions have been granted
        if (!checkAllPermissions()) {
            svPreview!!.disableCamera()
        }
        svPreview!!.onResume()
    }

    override fun onPause() {
        super.onPause()
        svPreview!!.onPause()
    }

    override fun onDestroy() {
        PredictorManager.onUnloadModel()
        super.onDestroy()
    }

    fun initView() {
        svPreview = findViewById<View>(R.id.sv_preview) as CameraSurfaceView
        svPreview!!.setOnTextureChangedListener(this)
        tvStatus = findViewById<View>(R.id.tv_status) as TextView
        btnSwitch = findViewById<View>(R.id.btn_switch) as ImageButton
        btnSwitch!!.setOnClickListener(this)
        btnShutter = findViewById<View>(R.id.btn_shutter) as ImageButton
        btnShutter!!.setOnClickListener(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, @NonNull permissions: Array<String?>,
        @NonNull grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED || grantResults[1] != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(this@BaiduNewCameraActivity)
                .setTitle("Permission denied")
                .setMessage(
                    "Click to force quit the app, then open Settings->Apps & notifications->Target " +
                            "App->Permissions to grant all of the permissions."
                )
                .setCancelable(false)
                .setPositiveButton("Exit") { dialog, which -> this@BaiduNewCameraActivity.finish() }.show()
        }
    }

    private fun requestAllPermissions() {
        ActivityCompat.requestPermissions(
            this, arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA
            ), 0
        )
    }

    private fun checkAllPermissions(): Boolean {
        return (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) === PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) === PackageManager.PERMISSION_GRANTED)
    }
}