package com.example.model_app

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.view.SurfaceView
import android.view.View
import com.example.model_app.FilamentRenderer
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView

/**
 * DentalModelPlatformView
 *
 * The Android-side PlatformView that Flutter embeds via AndroidView.
 * Owns the SurfaceView and FilamentRenderer, and responds to MethodChannel calls.
 *
 * Lifecycle:
 *   onCreate  → init Filament
 *   onResume  → start render loop
 *   onPause   → stop render loop
 *   onDestroy → destroy Filament resources
 */
class DentalModelPlatformView(
    private val context: Context,
    viewId: Int,
    messenger: BinaryMessenger,
    creationParams: Map<String, Any>?
) : PlatformView, MethodChannel.MethodCallHandler {

    private val surfaceView = SurfaceView(context)
    private val renderer    = FilamentRenderer(context, surfaceView)
    private val channel     = MethodChannel(messenger, "${CHANNEL_NAME}_$viewId")

    // Background thread for GLB asset I/O (avoid blocking the main thread)
    private val glbLoaderThread = HandlerThread("GlbLoader").also { it.start() }
    private val glbLoaderHandler = Handler(glbLoaderThread.looper)

    init {
        channel.setMethodCallHandler(this)
        renderer.init()
        loadDefaultAsset(creationParams)
        renderer.startRendering()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PlatformView
    // ─────────────────────────────────────────────────────────────────────────

    override fun getView(): View = surfaceView

    override fun dispose() {
        renderer.stopRendering()
        renderer.destroy()
        glbLoaderThread.quitSafely()
        channel.setMethodCallHandler(null)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MethodChannel handler
    // ─────────────────────────────────────────────────────────────────────────

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {

            METHOD_SET_LAYER_VISIBLE -> {
                val layer   = call.argument<String>("layer")   ?: return result.error("BAD_ARGS", "layer required", null)
                val visible = call.argument<Boolean>("visible") ?: return result.error("BAD_ARGS", "visible required", null)
                renderer.setLayerVisible(layer, visible)
                result.success(null)
            }

            METHOD_GET_LAYER_VISIBILITY -> {
                result.success(renderer.getLayerVisibility())
            }

            METHOD_ORBIT_CAMERA -> {
                val dx = call.argument<Double>("dx") ?: 0.0
                val dy = call.argument<Double>("dy") ?: 0.0
                renderer.orbitCamera(dx, dy)
                result.success(null)
            }

            METHOD_ZOOM_CAMERA -> {
                val delta = call.argument<Double>("delta") ?: 0.0
                renderer.zoomCamera(delta)
                result.success(null)
            }

            METHOD_RESET_CAMERA -> {
                renderer.orbitCamera(0.0, 0.0)
                result.success(null)
            }

            METHOD_LOAD_ASSET -> {
                val assetName = call.argument<String>("assetName") ?: "patient_model.glb"
                loadAssetAsync(assetName, result)
            }

            else -> result.notImplemented()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Asset loading
    // ─────────────────────────────────────────────────────────────────────────

    private fun loadDefaultAsset(params: Map<String, Any>?) {
        val assetName = params?.get("assetName") as? String ?: "patient_model.glb"
        glbLoaderHandler.post {
            try {
                renderer.loadGlbAsset(assetName)
            } catch (e: Exception) {
                android.util.Log.e("DentalView", "Failed to load $assetName", e)
            }
        }
    }

    private fun loadAssetAsync(assetName: String, result: MethodChannel.Result) {
        glbLoaderHandler.post {
            try {
                renderer.loadGlbAsset(assetName)
                Handler(context.mainLooper).post { result.success(true) }
            } catch (e: Exception) {
                Handler(context.mainLooper).post {
                    result.error("LOAD_ERROR", e.message, null)
                }
            }
        }
    }

    companion object {
        const val CHANNEL_NAME            = "com.example.model_app/filament"
        const val METHOD_SET_LAYER_VISIBLE   = "setLayerVisible"
        const val METHOD_GET_LAYER_VISIBILITY= "getLayerVisibility"
        const val METHOD_ORBIT_CAMERA        = "orbitCamera"
        const val METHOD_ZOOM_CAMERA         = "zoomCamera"
        const val METHOD_RESET_CAMERA        = "resetCamera"
        const val METHOD_LOAD_ASSET          = "loadAsset"
    }
}
