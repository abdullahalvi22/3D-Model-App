// lib/widgets/dental_model_view.dart

import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:model_app/dental_model_channel.dart';

/// [DentalModelView]
///
/// Flutter widget that embeds the native Android Filament view via
/// [AndroidView] / [PlatformViewLink].
///
/// Exposes [onViewCreated] so the parent screen can obtain the
/// [DentalModelChannel] and drive layer/camera control.
///
/// Gesture handling:
///   - Single-finger drag → orbit camera
///   - Pinch (scale) gesture → zoom
class DentalModelView extends StatefulWidget {
  const DentalModelView({
    super.key,
    this.assetName = 'patient_model.glb',
    this.onViewCreated,
  });

  final String assetName;
  final void Function(DentalModelChannel channel)? onViewCreated;

  @override
  State<DentalModelView> createState() => _DentalModelViewState();
}

class _DentalModelViewState extends State<DentalModelView> {
  DentalModelChannel? _channel;

  // Gesture tracking
  // Offset? _lastPanPosition;
  // double? _lastScale;
  double _lastScale = 1.0;
  int _lastPointerCount = 0;

  static const String _viewType = 'com.example.model_app/dental_model_view';

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,

      // Scale handles both:
      // 1 finger  -> orbit
      // 2 fingers -> pinch zoom
      onScaleStart: (details) {
        _lastScale = 1.0;
        _lastPointerCount = details.pointerCount;
      },

      onScaleUpdate: (details) {
        const orbitSensitivity = 0.3;
        const zoomSensitivity = 5.0;

        // Reset scale baseline when the number of fingers changes.
        // This avoids a zoom jump when the second finger lands.
        if (details.pointerCount != _lastPointerCount) {
          _lastPointerCount = details.pointerCount;
          _lastScale = details.scale;
          return;
        }

        // ── One finger drag → orbit ─────────────────────────────────────────
        if (details.pointerCount == 1) {
          final delta = details.focalPointDelta;

          _channel?.orbitCamera(
            delta.dx * orbitSensitivity,
            -delta.dy * orbitSensitivity,
          );

          return;
        }

        // ── Two or more fingers → pinch zoom ────────────────────────────────
        if (details.pointerCount >= 2) {
          final scaleDelta = details.scale - _lastScale;
          _lastScale = details.scale;

          _channel?.zoomCamera(scaleDelta * zoomSensitivity);
        }
      },

      onScaleEnd: (_) {
        _lastScale = 1.0;
        _lastPointerCount = 0;
      },

      child: _buildPlatformView(),
    );
  }

  Widget _buildPlatformView() {
    // Use PlatformViewLink for Hybrid Composition (best for Filament)
    return PlatformViewLink(
      viewType: _viewType,
      surfaceFactory: (context, controller) {
        return AndroidViewSurface(
          controller: controller as AndroidViewController,
          gestureRecognizers: const <Factory<OneSequenceGestureRecognizer>>{},
          hitTestBehavior: PlatformViewHitTestBehavior.transparent,
        );
      },
      onCreatePlatformView: (params) {
        return PlatformViewsService.initExpensiveAndroidView(
            id: params.id,
            viewType: _viewType,
            layoutDirection: TextDirection.ltr,
            creationParams: {'assetName': widget.assetName},
            creationParamsCodec: const StandardMessageCodec(),
            onFocus: () => params.onFocusChanged(true),
          )
          ..addOnPlatformViewCreatedListener((id) {
            params.onPlatformViewCreated(id);
            _onPlatformViewCreated(id);
          })
          ..create();
      },
    );
  }

  void _onPlatformViewCreated(int viewId) {
    final channel = DentalModelChannel(viewId);
    _channel = channel;
    widget.onViewCreated?.call(channel);
  }
}
