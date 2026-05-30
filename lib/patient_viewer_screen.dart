// lib/screens/patient_viewer_screen.dart

import 'package:flutter/material.dart';
import 'package:model_app/dental_model_channel.dart';
import 'package:model_app/dental_model_view.dart';

/// [PatientViewerScreen]
///
/// Full patient education UI:
///   ┌─────────────────────────────────────┐
///   │        3D Dental Model              │
///   │     (Filament PlatformView)         │
///   │                                     │
///   │  [⟲ Reset]              [? Info]   │
///   ├─────────────────────────────────────┤
///   │   LAYER CONTROLS                    │
///   │   ○ Bones        ●────  ON          │
///   │   ○ Implants     ●────  ON          │
///   │   ○ Prosthetics  ●────  ON          │
///   │   ○ Face         ●────  ON          │
///   └─────────────────────────────────────┘
class PatientViewerScreen extends StatefulWidget {
  const PatientViewerScreen({super.key, this.patientName = 'Patient'});
  final String patientName;

  @override
  State<PatientViewerScreen> createState() => _PatientViewerScreenState();
}

class _PatientViewerScreenState extends State<PatientViewerScreen>
    with TickerProviderStateMixin {
  DentalModelChannel? _channel;
  bool _panelExpanded = true;
  bool _isLoading = true;

  // Layer visibility state — drives both the UI and MethodChannel calls
  final Map<DentalLayer, bool> _layerVisibility = {
    for (final l in DentalLayer.values) l: true,
  };

  // Animation controller for the bottom panel slide
  late final AnimationController _panelAnim;
  late final Animation<Offset> _panelSlide;

  @override
  void initState() {
    super.initState();
    _panelAnim = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 280),
      value: 1.0,
    );
    _panelSlide = Tween<Offset>(
      begin: const Offset(0, 1),
      end: Offset.zero,
    ).animate(CurvedAnimation(parent: _panelAnim, curve: Curves.easeOutCubic));
  }

  @override
  void dispose() {
    _panelAnim.dispose();
    super.dispose();
  }

  // ── Channel callback ─────────────────────────────────────────────────────

  void _onViewCreated(DentalModelChannel channel) {
    _channel = channel;
    setState(() => _isLoading = false);
  }

  // ── Layer toggle ─────────────────────────────────────────────────────────

  Future<void> _toggleLayer(DentalLayer layer) async {
    final newValue = !(_layerVisibility[layer] ?? true);
    setState(() => _layerVisibility[layer] = newValue);
    await _channel?.setLayerVisible(layer, visible: newValue);
  }

  // ── Panel toggle ─────────────────────────────────────────────────────────

  void _togglePanel() {
    setState(() => _panelExpanded = !_panelExpanded);
    if (_panelExpanded) {
      _panelAnim.forward();
    } else {
      _panelAnim.reverse();
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Build
  // ─────────────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;

    return Scaffold(
      backgroundColor: const Color(0xFF0D0F14),
      appBar: _buildAppBar(cs),
      body: Column(
        children: [
          // ── 3-D viewport ─────────────────────────────────────────────
          Expanded(
            child: Stack(
              children: [
                DentalModelView(
                  assetName: 'patient_model.glb',
                  onViewCreated: _onViewCreated,
                ),
                if (_isLoading) _buildLoadingOverlay(),
                _buildViewportControls(cs),
              ],
            ),
          ),

          // ── Layer panel ───────────────────────────────────────────────
          SlideTransition(position: _panelSlide, child: _buildLayerPanel(cs)),
        ],
      ),
    );
  }

  // ── AppBar ────────────────────────────────────────────────────────────────

  PreferredSizeWidget _buildAppBar(ColorScheme cs) {
    return AppBar(
      backgroundColor: const Color(0xFF111318),
      foregroundColor: Colors.white,
      elevation: 0,
      title: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            widget.patientName,
            style: const TextStyle(
              fontSize: 17,
              fontWeight: FontWeight.w600,
              color: Colors.white,
            ),
          ),
          const Text(
            'Interactive Dental Model',
            style: TextStyle(fontSize: 11, color: Color(0xFF7A8494)),
          ),
        ],
      ),
      actions: [
        IconButton(
          icon: Icon(
            _panelExpanded ? Icons.tune : Icons.tune_outlined,
            color: _panelExpanded ? const Color(0xFF4FC3F7) : Colors.white54,
          ),
          tooltip: 'Layer controls',
          onPressed: _togglePanel,
        ),
        const SizedBox(width: 4),
      ],
    );
  }

  // ── Loading overlay ───────────────────────────────────────────────────────

  Widget _buildLoadingOverlay() {
    return Container(
      color: const Color(0xFF0D0F14),
      child: const Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            CircularProgressIndicator(
              valueColor: AlwaysStoppedAnimation(Color(0xFF4FC3F7)),
              strokeWidth: 2,
            ),
            SizedBox(height: 16),
            Text(
              'Initialising 3D model…',
              style: TextStyle(color: Color(0xFF7A8494), fontSize: 13),
            ),
          ],
        ),
      ),
    );
  }

  // ── Viewport overlay controls (reset, etc.) ───────────────────────────────

  Widget _buildViewportControls(ColorScheme cs) {
    return Positioned(
      right: 12,
      top: 12,
      child: Column(
        children: [
          _ViewportButton(
            icon: Icons.refresh_rounded,
            tooltip: 'Reset camera',
            onTap: () => _channel?.resetCamera(),
          ),
          const SizedBox(height: 8),
          _ViewportButton(
            icon: Icons.zoom_in_rounded,
            tooltip: 'Zoom in',
            onTap: () => _channel?.zoomCamera(1.0),
          ),
          const SizedBox(height: 8),
          _ViewportButton(
            icon: Icons.zoom_out_rounded,
            tooltip: 'Zoom out',
            onTap: () => _channel?.zoomCamera(-1.0),
          ),
        ],
      ),
    );
  }

  // ── Layer control panel ───────────────────────────────────────────────────

  Widget _buildLayerPanel(ColorScheme cs) {
    return Container(
      decoration: const BoxDecoration(
        color: Color(0xFF151820),
        border: Border(top: BorderSide(color: Color(0xFF252A35), width: 1)),
      ),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // Panel header
              Row(
                children: [
                  const Icon(
                    Icons.layers_outlined,
                    size: 15,
                    color: Color(0xFF4FC3F7),
                  ),
                  const SizedBox(width: 6),
                  const Text(
                    'ANATOMY LAYERS',
                    style: TextStyle(
                      fontSize: 11,
                      letterSpacing: 1.4,
                      fontWeight: FontWeight.w600,
                      color: Color(0xFF4FC3F7),
                    ),
                  ),
                  const Spacer(),
                  // Show all / Hide all
                  _SmallChip(
                    label: 'ALL',
                    onTap: () async {
                      for (final layer in DentalLayer.values) {
                        if (!(_layerVisibility[layer] ?? true)) {
                          await _toggleLayer(layer);
                        }
                      }
                    },
                  ),
                  const SizedBox(width: 6),
                  _SmallChip(
                    label: 'NONE',
                    onTap: () async {
                      for (final layer in DentalLayer.values) {
                        if (_layerVisibility[layer] ?? true) {
                          await _toggleLayer(layer);
                        }
                      }
                    },
                  ),
                ],
              ),
              const SizedBox(height: 10),
              // Layer rows
              ...DentalLayer.values.map(
                (layer) => _LayerToggleRow(
                  layer: layer,
                  isVisible: _layerVisibility[layer] ?? true,
                  onToggle: () => _toggleLayer(layer),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-widgets
// ─────────────────────────────────────────────────────────────────────────────

class _LayerToggleRow extends StatelessWidget {
  const _LayerToggleRow({
    required this.layer,
    required this.isVisible,
    required this.onToggle,
  });

  final DentalLayer layer;
  final bool isVisible;
  final VoidCallback onToggle;

  static const _layerMeta = {
    DentalLayer.bones: (
      icon: Icons.settings_outlined,
      color: Color(0xFFE8D5B7),
      label: 'Bones',
    ),
    DentalLayer.implants: (
      icon: Icons.hardware_outlined,
      color: Color(0xFF90CAF9),
      label: 'Implants',
    ),
    DentalLayer.prosthetics: (
      icon: Icons.blur_circular_outlined,
      color: Color(0xFFA5D6A7),
      label: 'Prosthetics',
    ),
    DentalLayer.smileFace: (
      icon: Icons.face_outlined,
      color: Color(0xFFFFAB91),
      label: 'Face',
    ),
  };

  @override
  Widget build(BuildContext context) {
    final meta = _layerMeta[layer]!;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: InkWell(
        onTap: onToggle,
        borderRadius: BorderRadius.circular(10),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          decoration: BoxDecoration(
            color: isVisible
                ? const Color(0xFF1E2330)
                : const Color(0xFF13151A),
            borderRadius: BorderRadius.circular(10),
            border: Border.all(
              color: isVisible
                  ? meta.color.withOpacity(0.3)
                  : const Color(0xFF252A35),
              width: 1,
            ),
          ),
          child: Row(
            children: [
              AnimatedOpacity(
                opacity: isVisible ? 1.0 : 0.3,
                duration: const Duration(milliseconds: 200),
                child: Icon(meta.icon, size: 18, color: meta.color),
              ),
              const SizedBox(width: 12),
              Text(
                meta.label,
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w500,
                  color: isVisible ? Colors.white : const Color(0xFF4A5060),
                ),
              ),
              const Spacer(),
              // Custom toggle pill
              _TogglePill(isOn: isVisible, color: meta.color),
            ],
          ),
        ),
      ),
    );
  }
}

class _TogglePill extends StatelessWidget {
  const _TogglePill({required this.isOn, required this.color});
  final bool isOn;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 200),
      width: 40,
      height: 22,
      decoration: BoxDecoration(
        color: isOn ? color.withOpacity(0.25) : const Color(0xFF252A35),
        borderRadius: BorderRadius.circular(11),
        border: Border.all(
          color: isOn ? color.withOpacity(0.6) : const Color(0xFF353B48),
        ),
      ),
      child: Stack(
        children: [
          AnimatedAlign(
            duration: const Duration(milliseconds: 200),
            curve: Curves.easeOut,
            alignment: isOn ? Alignment.centerRight : Alignment.centerLeft,
            child: Container(
              width: 16,
              height: 16,
              margin: const EdgeInsets.all(2),
              decoration: BoxDecoration(
                color: isOn ? color : const Color(0xFF4A5060),
                borderRadius: BorderRadius.circular(8),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _ViewportButton extends StatelessWidget {
  const _ViewportButton({
    required this.icon,
    required this.onTap,
    this.tooltip = '',
  });
  final IconData icon;
  final VoidCallback onTap;
  final String tooltip;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: tooltip,
      child: GestureDetector(
        onTap: onTap,
        child: Container(
          width: 36,
          height: 36,
          decoration: BoxDecoration(
            color: const Color(0xCC151820),
            borderRadius: BorderRadius.circular(8),
            border: Border.all(color: const Color(0xFF252A35)),
          ),
          child: Icon(icon, size: 18, color: Colors.white70),
        ),
      ),
    );
  }
}

class _SmallChip extends StatelessWidget {
  const _SmallChip({required this.label, required this.onTap});
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
        decoration: BoxDecoration(
          color: const Color(0xFF1E2330),
          borderRadius: BorderRadius.circular(4),
          border: Border.all(color: const Color(0xFF353B48)),
        ),
        child: Text(
          label,
          style: const TextStyle(
            fontSize: 10,
            fontWeight: FontWeight.w600,
            color: Color(0xFF7A8494),
            letterSpacing: 0.8,
          ),
        ),
      ),
    );
  }
}
