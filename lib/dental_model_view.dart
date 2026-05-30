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
  Offset? _lastPanPosition;
  double? _lastScale;

  static const String _viewType = 'com.example.model_app/dental_model_view';

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      // ── Pan → orbit ────────────────────────────────────────────────────
      onPanStart: (details) {
        _lastPanPosition = details.localPosition;
      },
      onPanUpdate: (details) {
        final last = _lastPanPosition;
        if (last == null) return;
        final delta = details.localPosition - last;
        _lastPanPosition = details.localPosition;

        // Scale pixel delta to orbit degrees
        const sensitivity = 0.3;
        _channel?.orbitCamera(delta.dx * sensitivity, -delta.dy * sensitivity);
      },
      onPanEnd: (_) => _lastPanPosition = null,

      // ── Scale → zoom ───────────────────────────────────────────────────
      onScaleStart: (details) {
        // _lastScale = details.scale;
      },
      onScaleUpdate: (details) {
        final last = _lastScale ?? 1.0;
        final delta = details.scale - last;
        _lastScale = details.scale;
        _channel?.zoomCamera(delta * 5.0);
      },
      onScaleEnd: (_) => _lastScale = null,

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
