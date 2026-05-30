package com.example.model_app

import android.content.Context
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

/**
 * DentalModelViewFactory
 *
 * Registered with Flutter's PlatformViewRegistry so that Flutter's
 * `AndroidView(viewType: 'com.example.model_app/dental_model_view')` resolves
 * to DentalModelPlatformView.
 */
class DentalModelViewFactory(
    private val messenger: BinaryMessenger
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {

    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        @Suppress("UNCHECKED_CAST")
        val creationParams = args as? Map<String, Any>
        return DentalModelPlatformView(context, viewId, messenger, creationParams)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DentalAppPlugin  –  registers the PlatformView factory with Flutter
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Drop this into your FlutterActivity (or FlutterFragmentActivity) plugin list,
 * or register it manually in MainActivity:
 *
 *   flutterEngine.plugins.add(DentalAppPlugin())
 *
 * For automatic registration add to:
 *   android/app/src/main/res/values/flutter_plugins.xml  (legacy)
 * or simply extend FlutterActivity in MainActivity (v2 embedding handles it).
 */
class DentalAppPlugin : FlutterPlugin {

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        binding.platformViewRegistry.registerViewFactory(
            VIEW_TYPE_ID,
            DentalModelViewFactory(binding.binaryMessenger)
        )
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        // nothing to tear down; individual views handle their own cleanup
    }

    companion object {
        const val VIEW_TYPE_ID = "com.example.model_app/dental_model_view"
    }
}
