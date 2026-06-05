package com.example.model_app

import android.content.Context
import android.view.Choreographer
import android.view.SurfaceView
import android.view.View
import com.google.android.filament.Skybox
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import io.flutter.FlutterInjector
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory
import java.nio.ByteBuffer

class MainActivity : FlutterActivity() {

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Required before using Filament utilities.
        Utils.init()

        // Register a native Android view that Flutter can display.
        flutterEngine
            .platformViewsController
            .registry
            .registerViewFactory(
                "filament_model_view",
                FilamentViewFactory(flutterEngine.dartExecutor.binaryMessenger)
            )
    }
}

/**
 * Factory that creates one native Filament view for each Flutter AndroidView.
 */
class FilamentViewFactory(
    private val messenger: BinaryMessenger
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {

    override fun create(
        context: Context,
        viewId: Int,
        args: Any?
    ): PlatformView {
        return FilamentPlatformView(context, messenger, viewId)
    }
}

/**
 * A native Android view that renders a 3D model using Filament.
 */
class FilamentPlatformView(
    private val context: Context,
    messenger: BinaryMessenger,
    viewId: Int
) : PlatformView {

    // SurfaceView is where Filament draws the 3D scene.
    private val surfaceView = SurfaceView(context)

    // ModelViewer is a helper from filament-utils that simplifies GLB loading and rendering.
    private val modelViewer = ModelViewer(surfaceView)

    // Choreographer gives us a frame callback, similar to a render loop.
    private val choreographer = Choreographer.getInstance()

    // MethodChannel used by Flutter to tell Kotlin which model to load.
    private val channel = MethodChannel(
        messenger,
        "filament_model_view_$viewId"
    )

    // Called every frame to render the scene.
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)

            // Render the current Filament frame.
            modelViewer.render(frameTimeNanos)
        }
    }

    init {
        setupScene()

        // Start the render loop.
        choreographer.postFrameCallback(frameCallback)

        // Listen for Flutter method calls.
        channel.setMethodCallHandler { call, result ->
            when (call.method) {
                "loadGlb" -> {
                    val assetPath = call.argument<String>("asset")

                    if (assetPath == null) {
                        result.error(
                            "INVALID_ASSET",
                            "Asset path is required",
                            null
                        )
                        return@setMethodCallHandler
                    }

                    try {
                        loadGlbFromFlutterAssets(assetPath)
                        result.success(true)
                    } catch (e: Exception) {
                        result.error(
                            "LOAD_ERROR",
                            e.message,
                            null
                        )
                    }
                }

                else -> result.notImplemented()
            }
        }
    }

    /**
     * Basic scene setup.
     */
    private fun setupScene() {
        // Add a simple background color.
        modelViewer.scene.skybox = Skybox.Builder()
            .color(0.08f, 0.08f, 0.1f, 1.0f)
            .build(modelViewer.engine)
    }

    /**
     * Loads a .glb file from Flutter assets and displays it.
     */
    // with shadow

    // private fun loadGlbFromFlutterAssets(assetPath: String) {
    //     // Convert Flutter asset path to the real Android asset path.
    //     // Example:
    //     // assets/models/duck.glb
    //     // becomes:
    //     // flutter_assets/assets/models/duck.glb
    //     val flutterAssetPath = FlutterInjector
    //         .instance()
    //         .flutterLoader()
    //         .getLookupKeyForAsset(assetPath)

    //     // Read the asset bytes.
    //     val bytes = context.assets.open(flutterAssetPath).use { input ->
    //         input.readBytes()
    //     }

    //     // Filament expects model data as a ByteBuffer.
    //     val buffer = ByteBuffer.allocateDirect(bytes.size)
    //     buffer.put(bytes)
    //     buffer.flip()

    //     // Remove any previously loaded model.
    //     modelViewer.destroyModel()

    //     // Load the GLB model.
    //     modelViewer.loadModelGlb(buffer)

    //     // Scale and center the model so it fits nicely in view.
    //     modelViewer.transformToUnitCube()
    // }

    private fun loadGlbFromFlutterAssets(assetPath: String) {
        // Convert Flutter asset path to the real Android asset path.
        val flutterAssetPath = FlutterInjector
            .instance()
            .flutterLoader()
            .getLookupKeyForAsset(assetPath)

        // Read the asset bytes.
        val bytes = context.assets.open(flutterAssetPath).use { input ->
            input.readBytes()
        }

        // Filament expects model data as a ByteBuffer.
        val buffer = ByteBuffer.allocateDirect(bytes.size)
        buffer.put(bytes)
        buffer.flip()

        // Remove any previously loaded model.
        modelViewer.destroyModel()

        // Load the GLB model.
        modelViewer.loadModelGlb(buffer)

        // Scale and center the model so it fits nicely in view.
        modelViewer.transformToUnitCube()

        // Disable all shadows for this model.
        // This prevents the model from casting shadows onto anything else.
        // It also prevents the model from receiving shadows from lights.
        modelViewer.asset?.entities?.forEach { entity ->
            val renderableManager = modelViewer.engine.renderableManager
            val instance = renderableManager.getInstance(entity)

            if (instance != 0) {
                // Stop this object from casting shadows.
                renderableManager.setCastShadows(instance, false)

                // Stop this object from receiving shadows.
                renderableManager.setReceiveShadows(instance, false)
            }
        }

        // Move the camera closer to make the model appear zoomed in.
        // Smaller Z value = more zoomed in.
        // Try 2.0, 1.8, or 1.5 depending on your model size.
        modelViewer.camera.lookAt(
            0.0, 0.0, 2.0,   // Camera position: closer to the model
            0.0, 0.0, 0.0,   // Look at the center of the model
            0.0, 1.0, 0.0    // Up direction
        )
    }


    /**
     * Returns the native Android view to Flutter.
     */
    override fun getView(): View {
        return surfaceView
    }

    /**
     * Clean up when Flutter removes this native view.
     */
    override fun dispose() {
        channel.setMethodCallHandler(null)
        choreographer.removeFrameCallback(frameCallback)
        modelViewer.destroyModel()
    }
}


// import com.google.android.filament.gltfio.Gltfio
// import com.google.android.filament.utils.Utils
// import io.flutter.embedding.android.FlutterActivity
// import io.flutter.embedding.engine.FlutterEngine

// class MainActivity : FlutterActivity() {

//     companion object {
//         init {
//             Utils.init()   // must be first — loads libfilament.so
//             Gltfio.init()  // must follow Utils.init() — loads libgltfio-jni.so
//         }
//     }

//     override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
//         super.configureFlutterEngine(flutterEngine)

//         flutterEngine.platformViewsController.registry.registerViewFactory(
//             DentalModelPlatformView.VIEW_TYPE_ID,
//             DentalModelViewFactory(flutterEngine.dartExecutor.binaryMessenger)
//         )
//     }
// }