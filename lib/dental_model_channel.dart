// lib/platform/dental_model_channel.dart

import 'package:flutter/services.dart';

/// Layer identifiers — must match Kotlin companion object constants.
enum DentalLayer {
  bones('bones'),
  implants('implants'),
  prosthetics('prosthetics'),
  smileFace('smile_face');

  const DentalLayer(this.key);
  final String key;
}

/// [DentalModelChannel]
///
/// Typed Dart bridge over the raw MethodChannel.
/// All public methods are `async` and return strongly-typed results.
///
/// The channel name includes the [viewId] suffix so that multiple
/// PlatformViews can coexist without collisions.
class DentalModelChannel {
  DentalModelChannel(int viewId)
    : _channel = MethodChannel('com.example.model_app/filament_$viewId');

  final MethodChannel _channel;

  // ── Layer visibility ────────────────────────────────────────────────────

  /// Show or hide a named [layer].
  Future<void> setLayerVisible(DentalLayer layer, {required bool visible}) {
    return _channel.invokeMethod('setLayerVisible', {
      'layer': layer.key,
      'visible': visible,
    });
  }

  /// Returns the current visibility state for all layers.
  Future<Map<DentalLayer, bool>> getLayerVisibility() async {
    final raw = await _channel.invokeMapMethod<String, bool>(
      'getLayerVisibility',
    );
    if (raw == null) return {};
    return {
      for (final layer in DentalLayer.values)
        if (raw.containsKey(layer.key)) layer: raw[layer.key]!,
    };
  }

  // ── Camera controls ─────────────────────────────────────────────────────

  /// Rotate the orbit camera by [dx] (horizontal) and [dy] (vertical) degrees.
  Future<void> orbitCamera(double dx, double dy) {
    return _channel.invokeMethod('orbitCamera', {'dx': dx, 'dy': dy});
  }

  /// Zoom in (positive) or out (negative) by [delta] units.
  Future<void> zoomCamera(double delta) {
    return _channel.invokeMethod('zoomCamera', {'delta': delta});
  }

  /// Reset the camera to its default position.
  Future<void> resetCamera() {
    return _channel.invokeMethod('resetCamera');
  }

  // ── Asset management ─────────────────────────────────────────────────────

  /// Load a named GLB asset from the Android assets folder.
  Future<bool> loadAsset(String assetName) async {
    final result = await _channel.invokeMethod<bool>('loadAsset', {
      'assetName': assetName,
    });
    return result ?? false;
  }
}
