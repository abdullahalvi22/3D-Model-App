package com.example.model_app

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.view.View
import android.view.SurfaceView
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView

class DentalModelPlatformView(
    private val context: Context,
    viewId: Int,
    messenger: BinaryMessenger,
    creationParams: Map<String, Any>?
) : PlatformView, MethodChannel.MethodCallHandler {

    companion object {
        const val VIEW_TYPE_ID  = "com.example.model_app/dental_model_view"
        private const val CHANNEL_BASE = "com.example.model_app/filament"
    }

    private val surfaceView   = SurfaceView(context)
    private val renderer      = FilamentRenderer(context, surfaceView)
    private val channel       = MethodChannel(messenger, "${CHANNEL_BASE}_$viewId")

    // private val loaderThread  = HandlerThread("GlbLoader").also { it.start() }
    // private val loaderHandler = Handler(loaderThread.looper)

    init {
        channel.setMethodCallHandler(this)
        renderer.init()
        val assetName = creationParams?.get("assetName") as? String ?: "patient_model.glb"
        // loaderHandler.post { loadAssetSafe(assetName) }
      loadAssetSafe(assetName) 
        renderer.startRendering()
    }

    override fun getView(): View = surfaceView

    override fun dispose() {
        renderer.stopRendering()
        renderer.destroy()
        // loaderThread.quitSafely()
        channel.setMethodCallHandler(null)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "setLayerVisible" -> {
                val layer   = call.argument<String>("layer")   ?: return result.error("BAD_ARGS", "layer required", null)
                val visible = call.argument<Boolean>("visible") ?: return result.error("BAD_ARGS", "visible required", null)
                renderer.setLayerVisible(layer, visible)
                result.success(null)
            }
            "getLayerVisibility" -> result.success(renderer.getLayerVisibility())
            "orbitCamera" -> {
                renderer.orbitCamera(
                    call.argument<Double>("dx") ?: 0.0,
                    call.argument<Double>("dy") ?: 0.0
                )
                result.success(null)
            }
            "zoomCamera" -> {
                renderer.zoomCamera(call.argument<Double>("delta") ?: 0.0)
                result.success(null)
            }
            "resetCamera" -> {
                renderer.resetCamera()
                result.success(null)
            }
            "loadAsset" -> {
                val name = call.argument<String>("assetName") ?: "patient_model.glb"
                // loaderHandler.post {
                //     val ok = loadAssetSafe(name)
                //     Handler(context.mainLooper).post {
                //         if (ok) result.success(true)
                //         else result.error("LOAD_ERROR", "Failed to load $name", null)
                //     }
                // }
                val ok = loadAssetSafe(name)

                if (ok) {
                    result.success(true)
                } else {
                    result.error(
                        "LOAD_ERROR",
                        "Failed to load $name",
                        null
                    )
                }
            }
            else -> result.notImplemented()
        }
    }

    private fun loadAssetSafe(assetName: String): Boolean {
        return try {
            renderer.loadGlbAsset(assetName)
            true
        } catch (e: Exception) {
            android.util.Log.e("DentalView", "GLB load failed: $assetName", e)
            false
        }
    }
}
