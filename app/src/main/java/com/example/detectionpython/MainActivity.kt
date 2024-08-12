package com.example.detectionpython

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val REQUEST_CAMERA_PERMISSION = 100
    private lateinit var photoUri: Uri
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        }

        cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val imageFile = File(photoUri.path!!)
                val bitmap = BitmapFactory.decodeFile(imageFile.path)
                processImage(bitmap)
            }
        }

        // Button click to capture image
        findViewById<Button>(R.id.takePictureButton).setOnClickListener {
            dispatchTakePictureIntent()
        }
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        photoUri = Uri.fromFile(File(externalCacheDir, "photo.jpg"))
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        cameraLauncher.launch(takePictureIntent)
    }

    private fun processImage(bitmap: Bitmap) {
        val python = Python.getInstance()
        val pythonModule = python.getModule("myscript")

        // Save bitmap to a temporary file
        val imageFile = File(cacheDir, "temp_image.jpg")
        try {
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return
        }

        // Call the Python function
        val encodingFilePath = File(filesDir, "assets/encoding.pkl").absolutePath
        val result: PyObject = pythonModule.callAttr("process_image", imageFile.absolutePath, encodingFilePath)

        // Handle the result
        val status = result.get("status").toString()
        val message = result.get("message").toString()
        Log.d("PythonResult", "Status: $status, Message: $message")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with camera action
            } else {
                // Permission denied, show a message to the user
            }
        }
    }
}
