package com.yny.mlocr

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
        val fragment = CameraFragment.newInstance()
        supportFragmentManager.beginTransaction().replace(R.id.fl_camera, fragment).commit()
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