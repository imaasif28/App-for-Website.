package com.aasif.webviewtest

import android.Manifest.permission.*
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.View
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.checkSelfPermission
import com.aasif.webviewtest.databinding.ActivityMainBinding
import com.aasif.webviewtest.utils.showSnackbar
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mUploadMessage: ValueCallback<Array<Uri>>? = null
    private var mCameraPhotoPath: String? = null
    private var size: Long = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        verifyStoragePermissions(this)
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
//            loadUrl("https://github.com/imaasif28/")
//        loadUrl("https://grouppolicy-uat.iiflinsurance.com:5008/raise-request/")
//        loadUrl("https://en.imgbb.com/")
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != INPUT_FILE_REQUEST_CODE || mUploadMessage == null) {
            super.onActivityResult(requestCode, resultCode, data)
            return
        }
        try {
            val filepath = mCameraPhotoPath!!.replace("file:", "")
            val file = File(filepath)
            size = file.length()
        } catch (e: Exception) {
            Log.e("Error!", "Error while opening image file" + e.localizedMessage)
        }
        if (data != null || mCameraPhotoPath != null) {
            var count: Int? = 0 //fix fby https://github.com/nnian
            var images: ClipData? = null
            try {
                images = data?.clipData
            } catch (e: Exception) {
                e.localizedMessage?.let { Log.e("Error!", it) }
            }
            if (images == null && data != null && data.dataString != null) {
                count = data.dataString!!.length
            } else if (images != null) {
                count = images.itemCount
            }
            var results = arrayOfNulls<Uri>(count!!)
            // Check that the response is a good one
            if (resultCode == RESULT_OK) {
                if (size != 0L) {
                    // If there is not data, then we may have taken a photo
                    if (mCameraPhotoPath != null) {
                        results = arrayOf(Uri.parse(mCameraPhotoPath))
                    }
                } else if (data?.clipData == null) {
                    results = arrayOf(Uri.parse(data?.dataString))
                } else {
                    for (i in 0 until images!!.itemCount) {
                        results[i] = images.getItemAt(i).uri
                    }
                }
            }
            mUploadMessage!!.onReceiveValue(results as Array<Uri>)
            mUploadMessage = null
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
        override fun onPermissionRequest(request: PermissionRequest?) {
            request?.grant(request.resources)
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest?) {
            request?.grant(request.resources)
        }

        override fun onShowFileChooser(
            view: WebView,
            filePath: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            /* try {
                 fileChooserValueCallback = filePath;
                 fileChooserResultLauncher.launch(fileChooserParams.createIntent())
             } catch (e: ActivityNotFoundException) {
                 // You may handle "No activity found to handle intent" error
             }*/
            // Double check that we don't have any existing callbacks
            if (mUploadMessage != null) {
                mUploadMessage!!.onReceiveValue(null)
            }
            mUploadMessage = filePath
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
                    verifyStoragePermissions(this@MainActivity)
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
            startActivityForResult(Intent.createChooser(chooserIntent, "Select images"), 1)
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

    companion object {
        private const val INPUT_FILE_REQUEST_CODE = 1
        private const val TAG = "MainActivity"

        // Storage Permissions variables
        private const val REQUEST_EXTERNAL_STORAGE = 1
        private val PERMISSIONS_STORAGE = arrayOf(
            READ_EXTERNAL_STORAGE,
            WRITE_EXTERNAL_STORAGE,
            CAMERA
        )
    }

    private fun verifyStoragePermissions(activity: Activity) {
        // Check if we have read or write permission
        val writePermission =
            checkSelfPermission(activity.applicationContext, WRITE_EXTERNAL_STORAGE)
        val readPermission = checkSelfPermission(activity, READ_EXTERNAL_STORAGE)
        val cameraPermission = checkSelfPermission(activity, CAMERA)
        if (writePermission != PERMISSION_GRANTED || readPermission != PERMISSION_GRANTED || cameraPermission != PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                activity,
                PERMISSIONS_STORAGE,
                REQUEST_EXTERNAL_STORAGE
            )
        } else binding.root.showSnackbar(getString(R.string.camera_access_required),
            Snackbar.LENGTH_LONG,
            getString(R.string.ok)) {
            println("SnackBar shows")
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", this.packageName, null)
                )
            )
        }
    }
/*

    private var fileChooserResultLauncher = createFileChooserResultLauncher()
    private var fileChooserValueCallback: ValueCallback<Array<Uri>>? = null


    private fun createFileChooserResultLauncher(): ActivityResultLauncher<Intent> {
        return registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                fileChooserValueCallback?.onReceiveValue(arrayOf(Uri.parse(it?.data?.dataString)));
            } else {
                fileChooserValueCallback?.onReceiveValue(null)
            }
        }
    }
*/

/*
    private fun initialize() = binding.webView.apply {
        verifyStoragePermissions(this@MainActivity, 1)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.allowFileAccess = true
        settings.loadWithOverviewMode = true
//        webChromeClient = WebChromeClient()
        webViewClient = MyWebClient()
        webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                return super.onShowFileChooser(webView, filePathCallback, fileChooserParams)
            }
        }
        this.settings.loadsImagesAutomatically = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
//        loadUrl("https://en.imgbb.com/")
        loadUrl("https://www.dropzone.dev/")
//        loadUrl("https://react-dropzone.js.org/#section-basic-example/")


    private fun createFileChooserResultLauncher(): ActivityResultLauncher<Intent> {
        return registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                fileChooserValueCallback?.onReceiveValue(arrayOf(Uri.parse(it?.data?.dataString)));
            } else {
                fileChooserValueCallback?.onReceiveValue(null)
            }
        }
    }

    class MyWebClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            println(view?.url)
            return super.shouldOverrideUrlLoading(view, request)
        }



    private fun createFileChooserResultLauncher(): ActivityResultLauncher<Intent> {
        return registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                fileChooserValueCallback?.onReceiveValue(arrayOf(Uri.parse(it?.data?.dataString)));
            } else {
                fileChooserValueCallback?.onReceiveValue(null)
            }
        }
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) binding.webView.goBack()
        else super.onBackPressed()
    }

    private fun verifyStoragePermissions(activity: Activity, requestCode: Int): Boolean {
        // Check if we have read or write permission
        val writePermission = checkSelfPermission(activity, WRITE_EXTERNAL_STORAGE)
        val readPermission = checkSelfPermission(activity, READ_EXTERNAL_STORAGE)
        val cameraPermission = checkSelfPermission(activity, CAMERA)

        if (writePermission != PackageManager.PERMISSION_GRANTED || readPermission != PackageManager.PERMISSION_GRANTED || cameraPermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    CAMERA,
                    READ_EXTERNAL_STORAGE,
                    WRITE_EXTERNAL_STORAGE
                ),
                requestCode
            )
            return false
        }
        return true
    }*/
}