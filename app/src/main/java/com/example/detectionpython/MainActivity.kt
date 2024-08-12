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
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private val REQUEST_CAMERA_PERMISSION = 100
    private lateinit var photoUri: Uri
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private lateinit var resultTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        resultTextView = findViewById(R.id.resultTextView)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        }

        cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                try {
                    val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(photoUri))
                    processImage(bitmap)
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                    Log.e("BitmapFactory", "Unable to decode stream: ${e.message}")
                }
            }
        }

        findViewById<Button>(R.id.takePictureButton).setOnClickListener {
            dispatchTakePictureIntent()
        }
    }

    private fun dispatchTakePictureIntent() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        val photoFile = File(externalCacheDir, "photo.jpg")
        photoUri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", photoFile)

        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        cameraLauncher.launch(takePictureIntent)
    }

    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val ratioBitmap = width.toFloat() / height.toFloat()
        val ratioMax = maxWidth.toFloat() / maxHeight.toFloat()

        val finalWidth: Int
        val finalHeight: Int
        if (ratioMax > 1) {
            finalWidth = (maxHeight * ratioBitmap).toInt()
            finalHeight = maxHeight
        } else {
            finalWidth = maxWidth
            finalHeight = (maxWidth / ratioBitmap).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, finalWidth, finalHeight, true)
    }

    private fun processImage(bitmap: Bitmap) {
        val python = Python.getInstance()
        val pythonModule = python.getModule("myscript")

        if (pythonModule == null) {
            Log.e("PythonError", "Failed to load Python module")
            return
        }

        val resizedBitmap = resizeBitmap(bitmap, 800, 600)
        val imageFile = File(cacheDir, "temp_image.jpg")
        try {
            FileOutputStream(imageFile).use { out ->
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("processImage", "Failed to save bitmap to file: ${e.message}")
            return
        }

        val encodingFilePath = File(filesDir, "encodings.pkl")
        copyAssetToFile("encodings.pkl", encodingFilePath)

        Log.d("FilePaths", "Image file path: ${imageFile.absolutePath}")
        Log.d("FilePaths", "Encoding file path: ${encodingFilePath.absolutePath}")

        if (!imageFile.exists()) {
            Log.e("FileCheck", "Image file does not exist")
            return
        }
        if (!encodingFilePath.exists()) {
            Log.e("FileCheck", "Encoding file does not exist")
            return
        }

        val result: PyObject? = pythonModule.callAttr("process_image", imageFile.absolutePath, encodingFilePath.absolutePath)

        runOnUiThread {
            if (result != null) {
                val status = result.get("status").toString()
                val message = result.get("message").toString()
                resultTextView.text = "Status: $status\nMessage: $message"
            } else {
                Log.e("PythonError", "Python function returned null.")
                resultTextView.text = "Error: Python function returned null."
            }
        }
    }

    private fun copyAssetToFile(assetName: String, outFile: File) {
        try {
            assets.open(assetName).use { inputStream ->
                FileOutputStream(outFile).use { outputStream ->
                    val buffer = ByteArray(1024)
                    var length: Int
                    while (inputStream.read(buffer).also { length = it } > 0) {
                        outputStream.write(buffer, 0, length)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("FileCopyError", "Failed to copy asset file: ${e.message}")
        }
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
