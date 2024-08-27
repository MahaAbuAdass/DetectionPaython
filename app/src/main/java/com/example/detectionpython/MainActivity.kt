package com.example.detectionpython

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.example.detectionpython.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var resultTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>

    private val CAMERA_PERMISSION_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        resultTextView = binding.resultTextView
        progressBar = findViewById(R.id.progressBar)

        // Initialize ActivityResultLauncher
        cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleCameraResult(result)
        }

        binding.takePictureButton.setOnClickListener {
            if (isCameraPermissionGranted()) {
                openCamera()
            } else {
                requestCameraPermission()
            }
        }

        binding.btnRegister.setOnClickListener {
            val intent = Intent(this, RegistrationActivity::class.java)
            startActivity(intent)
        }
    }

    private fun isCameraPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(this, "Camera permission is required to take pictures.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openCamera() {
        val intent = Intent(this, CameraActivity::class.java)
        cameraLauncher.launch(intent)
    }

    private fun handleCameraResult(result: ActivityResult) {
        if (result.resultCode == RESULT_OK) {
            val data: Intent? = result.data
            val imagePath = data?.getStringExtra("capturedImagePath") ?: return
            val imageFile = File(imagePath)

            if (imageFile.exists()) {
                val tempImageFile = File(cacheDir, "captured_image.jpg")
                imageFile.copyTo(tempImageFile, overwrite = true)

                correctImageOrientationAndSave(tempImageFile)
                CoroutineScope(Dispatchers.IO).launch {
                    processImage(tempImageFile)
                }
            } else {
                Log.e("FileError", "Image file does not exist at $imagePath")
            }
        }
    }

    private fun correctImageOrientationAndSave(imageFile: File) {
        try {
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            val correctedBitmap = correctImageOrientation(bitmap, imageFile.absolutePath)

            FileOutputStream(imageFile).use { out ->
                correctedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            Log.d("ImageCorrection", "Image successfully corrected and saved at ${imageFile.absolutePath}")

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ImageCorrection", "Failed to correct and save image: ${e.message}")
        }
    }

    private fun correctImageOrientation(bitmap: Bitmap, imagePath: String): Bitmap {
        val exif = ExifInterface(imagePath)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private suspend fun processImage(imageFile: File) {
        runOnUiThread {
            progressBar.visibility = ProgressBar.VISIBLE
        }

        val python = Python.getInstance()
        val pythonModule = python.getModule("emotion")

        if (pythonModule == null) {
            Log.e("PythonError", "Failed to load Python module")
            runOnUiThread {
                progressBar.visibility = ProgressBar.GONE
                Toast.makeText(this, "Failed to load Python module", Toast.LENGTH_LONG).show()
            }
            return
        }

        val encodingFile = File(filesDir, "encodings.pkl")
        if (!encodingFile.exists()) {
            copyAssetToFile("encodings.pkl", encodingFile)
        }

        if (encodingFile.canRead() && encodingFile.canWrite()) {
            Log.d("FileCheck", "Encoding file permissions are OK.")
        } else {
            Log.e("FileCheck", "Encoding file permissions are not sufficient.")
            runOnUiThread {
                progressBar.visibility = ProgressBar.GONE
                resultTextView.text = "Error: Insufficient permissions for encoding file."
            }
            return
        }

        if (imageFile.exists()) {
            Log.d("FileCheck", "Image file exists at ${imageFile.absolutePath}")
        } else {
            Log.e("FileCheck", "Image file does not exist at ${imageFile.absolutePath}")
            runOnUiThread {
                progressBar.visibility = ProgressBar.GONE
                resultTextView.text = "Error: Image file does not exist."
            }
            return
        }

        val fileSize = imageFile.length()
        Log.d("FileCheck", "Image file size: $fileSize bytes")
        if (fileSize == 0L) {
            Log.e("FileCheck", "Image file is empty")
            runOnUiThread {
                progressBar.visibility = ProgressBar.GONE
                resultTextView.text = "Error: Image file is empty."
            }
            return
        }

        try {
            Log.d("PythonExecution", "Starting Python function execution")
            val result: PyObject = withContext(Dispatchers.IO) {
                pythonModule.callAttr("process_image", imageFile.absolutePath, encodingFile.absolutePath)
            }

            val resultJson = result.toString() // Ensure the result is a JSON string
            Log.d("PythonResult", "Received result: $resultJson")

            runOnUiThread {
                try {
                    val jsonObject = JSONObject(resultJson)
                    val status = jsonObject.optString("status", "unknown")
                    val message = jsonObject.optString("message", "No message")
                    val timeAttendance = jsonObject.optString("attendance_time", "No time attendance")
                    val lightThreshold = jsonObject.optDouble("light_threshold", 0.0)
                    val recognitionThreshold = jsonObject.optDouble("recognition_threshold", 0.0)

                    // Adding log for time attendance
                    Log.d("PythonResult", "Time Attendance: $timeAttendance")
                    Log.d("PythonResult", "Light Threshold: $lightThreshold")
                    Log.d("PythonResult", "Recognition Threshold: $recognitionThreshold")

                    resultTextView.text = when (status) {
                        "error" -> "Message: $message\nLight Threshold: $lightThreshold\nRecognition Threshold: $recognitionThreshold"
                        "success" -> "Message: $message\nTime Attendance: $timeAttendance\nLight Threshold: $lightThreshold\nRecognition Threshold: $recognitionThreshold"
                        else -> "Unknown status: $status\nLight Threshold: $lightThreshold\nRecognition Threshold: $recognitionThreshold"
                    }
                } catch (e: Exception) {
                    Log.e("JsonParsingError", "Failed to parse JSON result: ${e.message}")
                    resultTextView.text = "Error: Failed to parse JSON result."
                }
                progressBar.visibility = ProgressBar.GONE
            }

            Log.d("PythonExecution", "Python function execution completed")

        } catch (e: Exception) {
            Log.e("PythonError", "Python function execution failed: ${e.message}")




            runOnUiThread {
                progressBar.visibility = ProgressBar.GONE
                resultTextView.text = "Error: Python function execution failed."
            }
        }
    }

    private fun copyAssetToFile(assetName: String, file: File) {
        try {
            assets.open(assetName).use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    val buffer = ByteArray(1024)
                    var length: Int
                    while (inputStream.read(buffer).also { length = it } > 0) {
                        outputStream.write(buffer, 0, length)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("FileCopy", "Failed to copy asset: ${e.message}")
        }
    }
}