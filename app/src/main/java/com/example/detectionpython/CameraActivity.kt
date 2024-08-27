package com.example.detectionpython

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.detectionpython.databinding.ActivityCameraBinding
import com.example.detectionpython.face.CameraManager
import com.example.detectionpython.face.FaceContourGraphic
import com.example.detectionpython.face.FaceStatus
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var cameraManager: CameraManager


    private lateinit var binding: ActivityCameraBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var outputDirectory: File
    private lateinit var imageCapture: ImageCapture
    private  var faceContourGraphic: FaceContourGraphic ?=null// Add this


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        createCameraManager()
        checkForPermission()
        checkTooltipText()

    }

    private fun checkForPermission() {
        if (allPermissionsGranted()) {
            cameraManager.startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                cameraManager.startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT)
                    .show()
                finish()
            }
        }
    }

    private fun createCameraManager() {
        cameraManager = CameraManager(
            this,
            binding.cameraPreview,
            this,
            binding.graphicOverlayFinder,
            ::processPicture
        )
    }


    private fun processPicture(faceStatus: FaceStatus) {
        Log.e("facestatus", "This is it ${faceStatus.name}")
//       when(faceStatus){}
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)
    }

    private fun checkTooltipText() {
        val tooltipText = faceContourGraphic?.getCurrentTooltipText()
        if (tooltipText?.contains("Your face is well-positioned") == true) {
            Log.d("CameraActivity result", "Tooltip text matches.")
            // Perform your action here
        } else {
            Log.d("CameraActivity result", "Tooltip text does not match.")
        }

    }
}