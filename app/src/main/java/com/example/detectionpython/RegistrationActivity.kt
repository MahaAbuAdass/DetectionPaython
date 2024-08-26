package com.example.detectionpython

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.detectionpython.databinding.ActivityRegistrationBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.media.ExifInterface
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import org.json.JSONObject

class RegistrationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegistrationBinding
    private val REQUEST_CAMERA_PERMISSION = 100
    private val REQUEST_IMAGE_CAPTURE = 200
    private var photoCaptured: Boolean = false
    private var userName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegistrationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Request camera permission if not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        }

        // Button to take a picture
        binding.btnTakeImage.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(this, CameraActivity::class.java)
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
            }
        }

        // Button to register
        binding.btnRegister.setOnClickListener {
            userName = binding.etName.text.toString().trim()
            if (userName.isEmpty()) {
                Toast.makeText(this, "Please enter your name.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!photoCaptured) {
                Toast.makeText(this, "Please take a picture.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val imageFilePath = File(filesDir, "$userName.jpg").absolutePath
            saveImageToPath(imageFilePath)

            // Show loader
            binding.progressBar.visibility = android.view.View.VISIBLE

            CoroutineScope(Dispatchers.IO).launch {
                val updateMessage = updateFaceEncodings(imageFilePath)
                withContext(Dispatchers.Main) {
                    // Hide loader
                    binding.progressBar.visibility = android.view.View.GONE

                    Toast.makeText(this@RegistrationActivity, updateMessage, Toast.LENGTH_LONG).show()

                    // Proceed to the next activity only if the update was successful
                    if (updateMessage.contains("Registration", ignoreCase = true)) {
                        val intent = Intent(this@RegistrationActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish()  // Optional: Close RegistrationActivity
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            val imagePath = data?.getStringExtra("capturedImagePath")
            if (imagePath != null) {
                photoCaptured = true
                correctImageOrientationAndSave(imagePath)
            }
        }
    }

    private fun correctImageOrientationAndSave(photoPath: String) {
        try {
            val bitmap = BitmapFactory.decodeFile(photoPath)
            val correctedBitmap = correctImageOrientation(bitmap, photoPath)
            FileOutputStream(photoPath).use { out ->
                correctedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            Log.d("ImageCorrection", "Image successfully saved at $photoPath")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("ImageCorrection", "Failed to correct and save image: ${e.message}")
        }
    }

    private fun correctImageOrientation(bitmap: Bitmap, photoPath: String): Bitmap {
        val exif = ExifInterface(photoPath)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun saveImageToPath(path: String) {
        try {
            val tempImageFile = File(filesDir, "temp_image.jpg")
            val resizedBitmap = BitmapFactory.decodeFile(tempImageFile.absolutePath)
            FileOutputStream(File(path)).use { out ->
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            Log.d("ImageSave", "Image successfully saved at $path")
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("ImageSave", "Failed to save image: ${e.message}")
        }
    }

    private suspend fun updateFaceEncodings(photoPath: String): String {
        val encodingFile = File(filesDir, "encodings.pkl")
        if (!encodingFile.exists() || encodingFile.length() == 0L) {
            copyAssetToFile("encodings.pkl", encodingFile)
        }

        val python = Python.getInstance()
        val pythonModule = python.getModule("updateencoding")

        return try {
            val result: PyObject = withContext(Dispatchers.IO) {
                pythonModule.callAttr("update_face_encodings", photoPath, encodingFile.absolutePath)
            }

            val response = result.toString()  // Convert the response to a String
            val jsonResponse = JSONObject(response)
            val message = jsonResponse.optString("message", "No message field in response")

            Log.d("UpdateFaceEncodings", "Response from Python: $message")
            message
        } catch (e: Exception) {
            e.printStackTrace()
            "Failed to update face encodings"
        }
    }

    private fun copyAssetToFile(assetFileName: String, outFile: File) {
        try {
            assets.open(assetFileName).use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(1024)
                    var length: Int
                    while (input.read(buffer).also { length = it } > 0) {
                        output.write(buffer, 0, length)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(this, CameraActivity::class.java)
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
