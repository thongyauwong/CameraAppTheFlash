/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.example.theFlash.fragments

import android.app.Activity
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import androidx.annotation.Nullable
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import com.android.example.theFlash.BuildConfig
import com.android.example.theFlash.R
import com.android.example.theFlash.databinding.FragmentGalleryBinding
import com.android.example.theFlash.fragments.CameraFragment.Companion.FILENAME
import com.android.example.theFlash.utils.padWithDisplayCutout
import com.android.example.theFlash.utils.showImmersive
import com.dsphotoeditor.sdk.activity.DsPhotoEditorActivity
import com.dsphotoeditor.sdk.utils.DsPhotoEditorConstants
import org.apache.commons.io.FileUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

val EXTENSION_WHITELIST = arrayOf("JPG")

/** Fragment used to present the user with a gallery of photos taken */
class GalleryFragment internal constructor() : Fragment() {

    /** Android ViewBinding */
    private var _fragmentGalleryBinding: FragmentGalleryBinding? = null

    private val fragmentGalleryBinding get() = _fragmentGalleryBinding!!

    /** AndroidX navigation arguments */
    private val args: GalleryFragmentArgs by navArgs()

    private lateinit var mediaList: MutableList<File>

    private val PICK_IMAGE = 1

    private val internalStorageFile = "/storage/emulated/0/Android/media/com.android.example.theFlash/The Flash!"

    private val sdkStorageFile = "/storage/emulated/0/Pictures/The Flash!"

    /** Adapter class used to present a fragment containing one photo or video as a page */
    inner class MediaPagerAdapter(fm: FragmentManager) :
        FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        override fun getCount(): Int = mediaList.size
        override fun getItem(position: Int): Fragment = PhotoFragment.create(mediaList[position])
        override fun getItemPosition(obj: Any): Int = POSITION_NONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mark this as a retain fragment, so the lifecycle does not get restarted on config change
        retainInstance = true

        // Get root directory of media from navigation arguments
        val rootDirectory = File(args.rootDirectory)
        //val test = args.rootDirectory

        // Walk through all files in the root directory
        // We reverse the order of the list to present the last photos first
        mediaList = rootDirectory.listFiles { file ->
            EXTENSION_WHITELIST.contains(file.extension.uppercase(Locale.ROOT))
        }?.sortedDescending()?.toMutableList() ?: mutableListOf()

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentGalleryBinding = FragmentGalleryBinding.inflate(inflater, container, false)
        return fragmentGalleryBinding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //Checking media files list
        if (mediaList.isEmpty()) {
            fragmentGalleryBinding.deleteButton.isEnabled = false
            fragmentGalleryBinding.shareButton.isEnabled = false
        }

        // Populate the ViewPager and implement a cache of two media items
        fragmentGalleryBinding.photoViewPager.apply {
            offscreenPageLimit = 2
            adapter = MediaPagerAdapter(childFragmentManager)
        }

        // Make sure that the cutout "safe area" avoids the screen notch if any
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Use extension method to pad "inside" view containing UI using display cutout's bounds
            fragmentGalleryBinding.cutoutSafeArea.padWithDisplayCutout()
        }

        // Handle back button press
        fragmentGalleryBinding.backButton.setOnClickListener {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigateUp()
        }

        //Handle edit button press
        fragmentGalleryBinding.editButton.setOnClickListener {
            mediaList.getOrNull(fragmentGalleryBinding.photoViewPager.currentItem)
                ?.let { mediaFile ->

                    val uri = FileProvider.getUriForFile(
                        view.context, BuildConfig.APPLICATION_ID + ".provider", mediaFile
                    )
                    val dsPhotoEditorIntent =
                        Intent(
                            this@GalleryFragment.context, DsPhotoEditorActivity::class.java
                        )
                    dsPhotoEditorIntent.data = uri
                    dsPhotoEditorIntent.putExtra(
                        DsPhotoEditorConstants.DS_PHOTO_EDITOR_OUTPUT_DIRECTORY,
                        "The Flash!"
                    )
                    startActivityForResult(
                        Intent.createChooser(
                            dsPhotoEditorIntent,
                            "Open PhotoDSK"
                        ), PICK_IMAGE
                    )
                }
        }

        // Handle share button press
        fragmentGalleryBinding.shareButton.setOnClickListener {

            mediaList.getOrNull(fragmentGalleryBinding.photoViewPager.currentItem)
                ?.let { mediaFile ->

                    // Create a sharing intent
                    val intent = Intent().apply {
                        // Infer media type from file extension
                        val mediaType = MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(mediaFile.extension)
                        // Get URI from our FileProvider implementation
                        val uri = FileProvider.getUriForFile(
                            view.context, BuildConfig.APPLICATION_ID + ".provider", mediaFile
                        )
                        // Set the appropriate intent extra, type, action and flags
                        putExtra(Intent.EXTRA_STREAM, uri)
                        type = mediaType
                        action = Intent.ACTION_SEND
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }

                    // Launch the intent letting the user choose which app to share with
                    startActivity(Intent.createChooser(intent, getString(R.string.share_hint)))
                }
        }

        // Handle delete button press
        fragmentGalleryBinding.deleteButton.setOnClickListener {

            mediaList.getOrNull(fragmentGalleryBinding.photoViewPager.currentItem)
                ?.let { mediaFile ->

                    AlertDialog.Builder(view.context, android.R.style.Theme_Material_Dialog)
                        .setTitle(getString(R.string.delete_title))
                        .setMessage(getString(R.string.delete_dialog))
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes) { _, _ ->

                            // Delete current photo
                            val mddir =
                                File(internalStorageFile)
                            val listAllFiles = mddir.listFiles()?.sortedDescending()
                            if ((listAllFiles != null) && listAllFiles.isNotEmpty()) {
                                var n = 0

                                for (currentFile in listAllFiles) {
                                    val fullName = currentFile.name
                                    val fileName = fullName.substringBeforeLast(".")
                                    val fileNameShort = fileName.substringAfterLast("-")
                                    val length = fileNameShort.length
                                    if (length > 2 && currentFile == mediaFile) {
                                        val mddir2 = File(sdkStorageFile)
                                        val listAllFiles2 = mddir2.listFiles()?.sortedDescending()
                                        if ((listAllFiles2 != null) && listAllFiles2.isNotEmpty()) {
                                            listAllFiles2[n].delete()
                                        }
                                    }
                                    n += 1
                                }
                            }

                            mediaFile.delete()

                            // Send relevant broadcast to notify other apps of deletion
                            MediaScannerConnection.scanFile(
                                view.context, arrayOf(mediaFile.absolutePath), null, null
                            )

                            // Notify our view pager
                            mediaList.removeAt(fragmentGalleryBinding.photoViewPager.currentItem)
                            fragmentGalleryBinding.photoViewPager.adapter?.notifyDataSetChanged()

                            // If all photos have been deleted, return to camera
                            if (mediaList.isEmpty()) {
                                Navigation.findNavController(
                                    requireActivity(),
                                    R.id.fragment_container
                                ).navigateUp()
                            }

                        }

                        .setNegativeButton(android.R.string.no, null)
                        .create().showImmersive()
                }
        }
    }

    override fun onDestroyView() {
        _fragmentGalleryBinding = null
        super.onDestroyView()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, @Nullable data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                PICK_IMAGE ->
                    if ((data != null) && (data.data != null)) {
                        createFileFromUri(data.data!!)

                        Navigation.findNavController(
                            requireActivity(), R.id.fragment_container
                        ).navigate(
                            GalleryFragmentDirections
                                .actionGalleryFragmentSelf(internalStorageFile)
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
            val filename = SimpleDateFormat(FILENAME, Locale.US)
                .format(System.currentTimeMillis())
            val file =
                File.createTempFile(
                    filename,
                    ".jpg",
                    mediaDir
                )
            FileUtils.copyInputStreamToFile(
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
