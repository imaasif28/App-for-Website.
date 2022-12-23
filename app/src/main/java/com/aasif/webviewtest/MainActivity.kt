package com.aasif.webviewtest

import android.Manifest.permission.*
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.webkit.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.checkSelfPermission
import com.aasif.webviewtest.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mCameraPhotoPath: String? = null
    private var size: Long = 0

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_EXTERNAL_STORAGE = 1
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        verifyStoragePermissions(this, true)
        binding.webView.apply {
            settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            settings.javaScriptEnabled = true
            settings.loadWithOverviewMode = true
            settings.allowFileAccess = true
            settings.loadsImagesAutomatically = true
            webViewClient = PQClient()
            webChromeClient = PQChromeClient()
            //if SDK version is greater of 19 then activate hardware acceleration otherwise activate software acceleration
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
//        loadUrl("https://react-dropzone.js.org/#section-basic-example/")
            loadUrl("https://www.dropzone.dev/")
//        loadUrl("https://grouppolicy-uat.iiflinsurance.com:5008/raise-request/")
//        loadUrl("https://en.imgbb.com/")
        }
    }

    @SuppressLint("SimpleDateFormat")
    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp =
            SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val imageFileName = "JPEG_" + timeStamp + "_"
        val storageDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            /* prefix = */ imageFileName,
            /* suffix = */ ".jpg",
            /* directory = */ storageDir
        )
    }

    inner class PQChromeClient : WebChromeClient() {
        override fun onShowFileChooser(
            view: WebView,
            filePath: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            Log.e("FileCooserParams => ", filePath.toString())
            var takePictureIntent: Intent? = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            if (takePictureIntent!!.resolveActivity(packageManager) != null) {
                // Create the File where the photo should go
                var photoFile: File? = null
                try {
                    photoFile = createImageFile()
                    takePictureIntent.putExtra("PhotoPath", mCameraPhotoPath)
                } catch (ex: IOException) {
                    // Error occurred while creating the File
                    Log.e(TAG, "Unable to create Image File", ex)
                }

                // Continue only if the File was successfully created
                if (photoFile != null) {
                    mCameraPhotoPath = "file:" + photoFile.absolutePath
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile))
                } else {
                    takePictureIntent = null
                }
            }
            val contentSelectionIntent = Intent(Intent.ACTION_GET_CONTENT)
            contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE)
            contentSelectionIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            contentSelectionIntent.type = "image/*"
            val intentArray: Array<Intent?> =
                takePictureIntent?.let { arrayOf(it) } ?: arrayOfNulls(2)
            val chooserIntent = Intent(Intent.ACTION_CHOOSER)
            chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
            chooserIntent.putExtra(Intent.EXTRA_TITLE, "Image Chooser")
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray)
            if (!verifyStoragePermissions(this@MainActivity, false)) {
                Snackbar.make(
                    binding.root,
                    getString(R.string.camera_access_required),
                    Snackbar.LENGTH_LONG
                ).also { snackBar ->
                    snackBar.setAction("ok") {
                        println("SnackBar shows")
                        startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", this@MainActivity.packageName, null)
                            )
                        )
                        snackBar.dismiss()
                    }
                }.show()
                fileChooserValueCallback?.onReceiveValue(null)
                fileChooserValueCallback = null
            }
            try {
                val filepath = mCameraPhotoPath!!.replace("file:", "")
                val file = File(filepath)
                size = file.length()
            } catch (e: Exception) {
                Log.e("Error!", "Error while opening image file" + e.localizedMessage)
            }
            try {
                fileChooserValueCallback = filePath
                fileChooserResultLauncher.launch(chooserIntent)
            } catch (e: ActivityNotFoundException) {
                // You may handle "No activity found to handle intent" error
            }
//            startActivityForResult(Intent.createChooser(chooserIntent, "Select images"), 1)
            return true
        }
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) binding.webView.goBack()
        else super.onBackPressed()
    }

    inner class PQClient : WebViewClient() {

        //Show loader on url load
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            // Then show progress  Dialog
            binding.progressBar.visibility = View.VISIBLE
        }

        // Called when all page resources loaded
        override fun onPageFinished(view: WebView, url: String) {
            binding.progressBar.visibility = View.GONE
            binding.webView.loadUrl("javascript:(function(){ " +
                    "document.getElementById('android-app').style.display='none';})()")
        }
    }


    private fun verifyStoragePermissions(activity: Activity, showDialog: Boolean): Boolean {
        // Check if we have read or write permission
        var writePermission =
            checkSelfPermission(activity.applicationContext, WRITE_EXTERNAL_STORAGE)
        var readPermission = checkSelfPermission(activity, READ_EXTERNAL_STORAGE)
        var cameraPermission = checkSelfPermission(activity, CAMERA)

        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            writePermission = PERMISSION_GRANTED
            readPermission = PERMISSION_GRANTED
        } else {
            permissions.add(READ_EXTERNAL_STORAGE)
            permissions.add(WRITE_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            cameraPermission = PERMISSION_GRANTED
        else
            permissions.add(CAMERA)

        if (writePermission != PERMISSION_GRANTED || readPermission != PERMISSION_GRANTED || cameraPermission != PERMISSION_GRANTED) {
            if (showDialog) {
                ActivityCompat.requestPermissions(activity,
                    permissions.toTypedArray(),
                    REQUEST_EXTERNAL_STORAGE)
            }
            return false
        }
        return true
    }

    private var fileChooserResultLauncher = createFileChooserResultLauncher()
    private var fileChooserValueCallback: ValueCallback<Array<Uri>>? = null


    private fun createFileChooserResultLauncher(): ActivityResultLauncher<Intent> {
        return registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
//                fileChooserValueCallback?.onReceiveValue(arrayOf(Uri.parse(result?.data?.dataString)))
                var count: Int? = 0 //fix fby https://github.com/nnian
                var images: ClipData? = null
                try {
                    images = result.data?.clipData
                } catch (e: Exception) {
                    e.localizedMessage?.let { Log.e("Error!", it) }
                }
                if (images == null && result.data != null && result.data!!.dataString != null) {
                    count = result.data!!.dataString!!.length
                } else if (images != null) {
                    count = images.itemCount
                }
                var results = arrayOfNulls<Uri>(count!!)
                // Check that the response is a good one

                if (size != 0L) {
                    // If there is not it.data, then we may have taken a photo
                    if (mCameraPhotoPath != null) {
                        results = arrayOf(Uri.parse(mCameraPhotoPath))
                    }
                } else if (result.data?.clipData == null) {
                    results = arrayOf(Uri.parse(result.data?.dataString))
                } else {
                    for (i in 0 until images!!.itemCount) {
                        results[i] = images.getItemAt(i).uri
                    }
                }
                fileChooserValueCallback?.onReceiveValue(results as Array<Uri>)
            }
        }
    }
}