package org.tarar.FieldPulse

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        findViewById<Button>(R.id.cameraButton).setOnClickListener {
            if (checkPermissions()) {
                openCamera()
            } else {
                requestPermissions()
            }
        }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            REQUEST_CODE_PERMISSIONS
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(
                    this,
                    "Camera permission is required to take photos",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun openCamera() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            takePictureIntent.resolveActivity(packageManager)?.also {
                val photoFile = try {
                    createImageFile()
                } catch (ex: IOException) {
                    Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show()
                    null
                }
                photoFile?.also {
                    val photoURI = FileProvider.getUriForFile(
                        this,
                        "${packageName}.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            val location = (application as MainApplication).positionProvider.getLastKnownLocation()
            if (location != null) {
                addLocationToImage(location)
                Toast.makeText(this, "Photo saved with location data", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Photo saved (no location available)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addLocationToImage(location: Location) {
        try {
            val exif = ExifInterface(currentPhotoPath)
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, convertToDegreeMinuteSeconds(location.latitude))
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (location.latitude >= 0) "N" else "S")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, convertToDegreeMinuteSeconds(location.longitude))
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (location.longitude >= 0) "E" else "W")
            exif.saveAttributes()
        } catch (e: IOException) {
            Log.e("HomeActivity", "Error writing EXIF data", e)
        }
    }

    private fun convertToDegreeMinuteSeconds(coordinate: Double): String {
        val absolute = Math.abs(coordinate)
        val degrees = absolute.toInt()
        val minutes = (absolute - degrees) * 60
        val seconds = (minutes - minutes.toInt()) * 60
        return "$degrees/1,${minutes.toInt()}/1,${seconds.toInt()}/1"
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 100
        private const val REQUEST_IMAGE_CAPTURE = 200
        private lateinit var currentPhotoPath: String
    }
}
