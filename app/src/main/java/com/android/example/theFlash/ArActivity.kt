package com.android.example.theFlash

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.AugmentedFace
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.Texture
import com.google.ar.sceneform.ux.AugmentedFaceNode
import java.util.function.Consumer
import java.util.function.Function

class ArActivity : AppCompatActivity() {
    private var modelRenderable: ModelRenderable? = null
    private var texture: Texture? = null
    private var isAdded = false
    private val faceNodeMap = HashMap<AugmentedFace, AugmentedFaceNode>()
    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar)
        val customArFragment: CustomArFragment? =
            supportFragmentManager.findFragmentById(R.id.arFragment) as CustomArFragment?
        ModelRenderable.builder()
            .setSource(this, R.raw.fox_face)
            .build()
            .thenAccept(Consumer { endurable: ModelRenderable? ->
                modelRenderable = endurable
                modelRenderable!!.isShadowCaster = false
                modelRenderable!!.isShadowReceiver = false
            })
            .exceptionally(Function<Throwable, Void?> { throwable: Throwable? ->
                Toast.makeText(this, "error loading model", Toast.LENGTH_SHORT).show()
                null
            })
        Texture.builder()
            .setSource(this, R.drawable.fox_face_mesh_texture)
            .build()
            .thenAccept(Consumer { textureModel: Texture? ->
                texture = textureModel
            })
            .exceptionally(Function<Throwable, Void?> { throwable: Throwable? ->
                Toast.makeText(this, "cannot load texture", Toast.LENGTH_SHORT).show()
                null
            })
        assert(customArFragment != null)
        if (customArFragment != null) {
            customArFragment.getArSceneView()
                .setCameraStreamRenderPriority(Renderable.RENDER_PRIORITY_FIRST)
        }
        if (customArFragment != null) {
            customArFragment.getArSceneView().getScene().addOnUpdateListener { frameTime ->
                if (modelRenderable == null || texture == null) {
                    return@addOnUpdateListener
                }
                val frame: Frame = customArFragment.getArSceneView().getArFrame()!!
                val augmentedFaces =
                    frame.getUpdatedTrackables(
                        AugmentedFace::class.java
                    )
                for (augmentedFace in augmentedFaces) {
                    if (isAdded) return@addOnUpdateListener
                    val augmentedFaceMode = AugmentedFaceNode(augmentedFace)
                    augmentedFaceMode.setParent(customArFragment.getArSceneView().getScene())
                    augmentedFaceMode.faceRegionsRenderable = modelRenderable
                    augmentedFaceMode.faceMeshTexture = texture
                    faceNodeMap[augmentedFace] = augmentedFaceMode
                    isAdded = true

                    // Remove any AugmentedFaceNodes associated with an AugmentedFace that stopped tracking.
                    val iterator: MutableIterator<Map.Entry<AugmentedFace, AugmentedFaceNode>> =
                        faceNodeMap.entries.iterator()
                    val (face, node) = iterator.next()
                    while (face.trackingState == TrackingState.STOPPED) {
                        node.setParent(null)
                        iterator.remove()
                    }
                }
            }
        }
    }
}