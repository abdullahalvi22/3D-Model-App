package com.example.model_app

import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.utils.Utils
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {

    companion object {
        init {
            Utils.init()   // must be first — loads libfilament.so
            Gltfio.init()  // must follow Utils.init() — loads libgltfio-jni.so
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        flutterEngine.platformViewsController.registry.registerViewFactory(
            DentalModelPlatformView.VIEW_TYPE_ID,
            DentalModelViewFactory(flutterEngine.dartExecutor.binaryMessenger)
        )
    }
}
