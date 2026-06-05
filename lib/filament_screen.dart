import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class FilamentScreen extends StatefulWidget {
  const FilamentScreen({super.key});

  @override
  State<FilamentScreen> createState() => _FilamentScreenState();
}

class _FilamentScreenState extends State<FilamentScreen> {
  MethodChannel? _channel;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Filament 3D Model')),
      body: AndroidView(
        // This name must match the Kotlin view factory name.
        viewType: 'filament_model_view',

        // Called when the native Android view is ready.
        onPlatformViewCreated: (int id) async {
          // Each native view gets its own channel.
          _channel = MethodChannel('filament_model_view_$id');

          // Tell Kotlin which Flutter asset to load.
          await _channel!.invokeMethod('loadGlb', {
            'asset': 'assets/scene_4089.glb',
          });
        },
      ),
    );
  }
}
