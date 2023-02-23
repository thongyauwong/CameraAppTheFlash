package com.android.example.theFlash.fragments

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.Nullable
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.android.example.theFlash.ArActivity
import com.android.example.theFlash.MainActivity
import com.android.example.theFlash.R
import com.android.example.theFlash.databinding.FragmentMainPageBinding
import com.dsphotoeditor.sdk.activity.DsPhotoEditorActivity
import com.dsphotoeditor.sdk.utils.DsPhotoEditorConstants
import org.apache.commons.io.FileUtils.copyInputStreamToFile
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


class MainPageFragment : Fragment() {

    private var _fragmentMainPageBinding: FragmentMainPageBinding? = null
    private val fragmentMainPageBinding get() = _fragmentMainPageBinding!!
    private val PICK_IMAGE = 1
    private val SELECTED_PICTURE = 10
    private val internalStorageFile = "/storage/emulated/0/Android/media/com.android.example.theFlash/The Flash!"
    private val cameraPermissionRequestCode = 11
    private val storagePermissionRequestCode = 12
    private val AR_IMAGE = 3

    private val cameraPermissionRequest =
        mutableListOf(
            Manifest.permission.CAMERA,
        ).toTypedArray()

    private val storagePermissionRequest =
        mutableListOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentMainPageBinding = FragmentMainPageBinding.inflate(inflater, container, false)
        return fragmentMainPageBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //Exit App in the mainPage if back button is pressed twice
        var count = 0
        requireActivity().onBackPressedDispatcher.addCallback(this) {
            if (count == 0) {
                Toast.makeText(
                    context,
                    "Press again to exit",
                    Toast.LENGTH_SHORT
                ).show()
                count += 1
            } else {
                activity?.finish()
            }
        }

        //Animation in Main Page
        fragmentMainPageBinding.appName.setAlpha(0f)
        fragmentMainPageBinding.appName.setTranslationY(-70F)
        fragmentMainPageBinding.appName.animate().alpha(1f).translationYBy(70F).setDuration(2000)

        fragmentMainPageBinding.appImage.setAlpha(0f)
        fragmentMainPageBinding.appImage.setTranslationY(-70F)
        fragmentMainPageBinding.appImage.animate().alpha(1f).translationYBy(70F).setDuration(2000)

        fragmentMainPageBinding.openCameraButton.setAlpha(0f)
        fragmentMainPageBinding.openCameraButton.setTranslationY(70F)
        fragmentMainPageBinding.openCameraButton.animate().alpha(1f).translationYBy(-70F)
            .setDuration(2000)


        fragmentMainPageBinding.openAlbumButton.setAlpha(0f)
        fragmentMainPageBinding.openAlbumButton.setTranslationY(70F)
        fragmentMainPageBinding.openAlbumButton.animate().alpha(1f).translationYBy(-70F)
            .setDuration(2000)


        fragmentMainPageBinding.openCameraButton.setOnClickListener {
            if (!hasCameraPermissions(requireContext())){
                requestPermissions(
                    cameraPermissionRequest,
                    cameraPermissionRequestCode
                )
            } else {
                Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                    MainPageFragmentDirections
                        .actionMainToCamera()
                )
            }
        }

        fragmentMainPageBinding.openAlbumButton.setOnClickListener {
            if (!hasStoragePermissions(requireContext())){
                requestPermissions(
                    storagePermissionRequest,
                    storagePermissionRequestCode
                )
            } else {
                val intent = Intent()
                intent.type = "image/*"
                intent.action = Intent.ACTION_GET_CONTENT
                startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE)
            }
        }

        fragmentMainPageBinding.openArButton.setOnClickListener {
            val arActivityIntent =
                Intent(
                    this@MainPageFragment.context, ArActivity::class.java
                )
            startActivityForResult(
                Intent.createChooser(
                    arActivityIntent,
                    "Open AR"
                ), AR_IMAGE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            when (requestCode) {
                cameraPermissionRequestCode ->
                    if (PackageManager.PERMISSION_GRANTED == grantResults.firstOrNull()) {
                        Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                            MainPageFragmentDirections
                                .actionMainToCamera()
                        )
                    } else {
                        Toast.makeText(context, "Camera Permission is not granted. Please enable it in Settings >> Privacy protection.", Toast.LENGTH_LONG).show()
                    }

                storagePermissionRequestCode ->
                    if (PackageManager.PERMISSION_GRANTED == grantResults.firstOrNull()) {
                        val intent = Intent()
                        intent.type = "image/*"
                        intent.action = Intent.ACTION_GET_CONTENT
                        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE)
                    } else {
                        Toast.makeText(context, "Storage Permission is not granted. Please enable it in Settings >> Privacy protection.", Toast.LENGTH_LONG).show()
                    }
            }
    }

    private fun hasCameraPermissions(context: Context) = cameraPermissionRequest.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasStoragePermissions(context: Context) = storagePermissionRequest.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, @Nullable data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                PICK_IMAGE ->
                    if ((data != null) && (data.data != null)) {
                        val selectedImageUri: Uri? = data.data
                        val dsPhotoEditorIntent =
                            Intent(
                                this@MainPageFragment.context, DsPhotoEditorActivity::class.java
                            )
                        dsPhotoEditorIntent.data = selectedImageUri
                        dsPhotoEditorIntent.putExtra(
                            DsPhotoEditorConstants.DS_PHOTO_EDITOR_OUTPUT_DIRECTORY,
                            "The Flash!"
                        )
                        startActivityForResult(
                            Intent.createChooser(
                                dsPhotoEditorIntent,
                                "Open PhotoDSK"
                            ), SELECTED_PICTURE
                        )
                    }
                SELECTED_PICTURE ->
                    if ((data != null) && (data.data != null)) {
                        createFileFromUri(data.data!!)
                        Navigation.findNavController(
                            requireActivity(), R.id.fragment_container
                        ).navigate(
                            MainPageFragmentDirections
                                .actionMainToGallery(internalStorageFile)
                        )
                    }
            }
        }
    }

    private fun createFileFromUri(uri: Uri): File? {
        return try {
            val appContext = context?.applicationContext
            val stream = context?.contentResolver?.openInputStream(uri)
            val mediaDir = context?.externalMediaDirs?.firstOrNull()?.let {
                File(
                    it,
                    appContext?.resources?.getString(R.string.app_name)
                ).apply { mkdirs() }
            }
            val filename = SimpleDateFormat(CameraFragment.FILENAME, Locale.US)
                .format(System.currentTimeMillis())
            val file =
                File.createTempFile(
                    filename,
                    ".jpg",
                    mediaDir
                )
            copyInputStreamToFile(
                stream,
                file
            )  // Use this one import org.apache.commons.io.FileUtils
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
