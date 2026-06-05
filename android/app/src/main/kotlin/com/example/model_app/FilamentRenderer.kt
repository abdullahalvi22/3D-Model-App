package com.example.model_app

import android.content.Context
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.*
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.*
import com.google.android.filament.utils.Utils   // filament-utils-android
import java.nio.ByteBuffer

/**
 * FilamentRenderer  (Filament 1.71.x-compatible)
 *
 * Key API notes for 1.32+:
 *  • Gltfio.init() / Utils.init() must be called before any Filament objects are created.
 *  • UbershaderProvider(engine) constructor is still valid in 1.71; the archive-stream
 *    constructor is an alternative — the no-arg form uses the bundled archive.
 *  • FilamentHelper was removed; use Utils.init() instead.
 *  - Camera.setProjection(fovInDegrees, aspect, near, far, Camera.Fov.VERTICAL) is used for FOV.
 *  - AmbientOcclusionOptions enables SSAO with enabled=true; it has no ambientOcclusion field.
 *  • scene.addEntities() / scene.removeEntities() accept IntArray.
 *  • FilamentAsset.getName(entity) returns String? per entity.
 *  • ResourceLoader constructor: ResourceLoader(engine) — normalizeWeights /
 *    recomputeBoundingBoxes params removed in recent versions; use the simple ctor.
 */
class FilamentRenderer(
    private val context: Context,
    private val surfaceView: SurfaceView
) {

    // ── Filament core ──────────────────────────────────────────────────────────
    private lateinit var engine: Engine
    private lateinit var renderer: Renderer
    private lateinit var scene: Scene
    private lateinit var view: View
    private lateinit var camera: Camera
    @Entity private var cameraEntity: Int = 0

    // ── Surface / swap-chain ──────────────────────────────────────────────────
    private var swapChain: SwapChain? = null
    private lateinit var uiHelper: UiHelper
    private lateinit var displayHelper: DisplayHelper

    // ── glTF / GLB loading ────────────────────────────────────────────────────
    private lateinit var assetLoader: AssetLoader
    private lateinit var resourceLoader: ResourceLoader
    private lateinit var materialProvider: MaterialProvider
    private var filamentAsset: FilamentAsset? = null

    // ── Layer entity map  (layer name → set of @Entity Int) ───────────────────
    private val layerEntities: MutableMap<String, MutableSet<Int>> = mutableMapOf(
        LAYER_BONES       to mutableSetOf(),
        LAYER_IMPLANTS    to mutableSetOf(),
        LAYER_PROSTHETICS to mutableSetOf(),
        LAYER_FACE        to mutableSetOf()
    )

    private val layerVisibility: MutableMap<String, Boolean> = mutableMapOf(
        LAYER_BONES       to true,
        LAYER_IMPLANTS    to true,
        LAYER_PROSTHETICS to true,
        LAYER_FACE        to true
    )

    // ── Choreographer render loop ─────────────────────────────────────────────
    private val choreographer = Choreographer.getInstance()
    private var isRendering = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRendering) return
            choreographer.postFrameCallback(this)
            renderFrame()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Initialisation
    // ─────────────────────────────────────────────────────────────────────────

    fun init() {
        // REQUIRED in 1.32+: loads Filament's native .so before JNI calls.
        // Utils.init() covers both filament-android and filament-utils-android.
        Utils.init()

        engine   = Engine.create()
        renderer = engine.createRenderer()
        scene    = engine.createScene()
        view     = engine.createView()

        // Camera entity
        cameraEntity = EntityManager.get().create()
        camera = engine.createCamera(cameraEntity)
        setupCamera()

        view.scene  = scene
        view.camera = camera

        // SSAO (ambient occlusion)
        view.ambientOcclusionOptions = View.AmbientOcclusionOptions().apply {
            enabled = true
            radius = 0.3f
            intensity = 1.0f
            quality = View.QualityLevel.MEDIUM
            lowPassFilter = View.QualityLevel.MEDIUM
            upsampling = View.QualityLevel.LOW
        }

        // Renderer clear colour (dark background)
        val clearOptions = Renderer.ClearOptions()
        clearOptions.clearColor = floatArrayOf(0.08f, 0.08f, 0.10f, 1.0f)
        clearOptions.clear = true
        renderer.clearOptions = clearOptions

        setupLighting()

        // glTF infrastructure
        // UbershaderProvider(engine) — uses the bundled ubershader archive.
        materialProvider = UbershaderProvider(engine)
        assetLoader      = AssetLoader(engine, materialProvider, EntityManager.get())
        // Simple ResourceLoader ctor (params removed in 1.32+)
        resourceLoader   = ResourceLoader(engine)

        // UiHelper bridges SurfaceView ↔ SwapChain lifecycle
        displayHelper = DisplayHelper(context)
        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply {
            renderCallback = surfaceCallback
            attachTo(surfaceView)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GLB Loading  (call from a background thread)
    // ─────────────────────────────────────────────────────────────────────────

    fun loadGlbAsset(assetName: String = "patient_model.glb") {
        val buffer = context.assets.open(assetName).use { stream ->
            val bytes = stream.readBytes()
            ByteBuffer.allocateDirect(bytes.size).put(bytes).also { it.rewind() }
        }

        val asset = assetLoader.createAsset(buffer)
            ?: throw IllegalStateException("AssetLoader.createAsset() returned null for: $assetName")

        // loadResources is synchronous for embedded (GLB) resources
        resourceLoader.loadResources(asset)

        // Keep source animation data when the GLB contains animations.
        val hasAnimations = asset.instance.animator.animationCount > 0
        if (!hasAnimations) {
            asset.releaseSourceData()
        }

        filamentAsset = asset

        // Add all entities to the scene
        scene.addEntities(asset.entities)

        // Map node names → layer buckets
        resolveLayerEntities(asset)

        // Fit orbit radius to model AABB
        fitCameraToAsset(asset)
    }

    /**
     * Walk all entities in the asset and bucket them by node-name substring.
     *
     * FilamentAsset.getName(entity) returns the glTF node name, which matches
     * the hierarchy defined in your GLB:  Scene / bones / implants / …
     */
    private fun resolveLayerEntities(asset: FilamentAsset) {
        layerEntities.values.forEach { it.clear() }

        for (entity in asset.entities) {
            val name = asset.getName(entity)?.lowercase() ?: continue
            when {
                name.contains(LAYER_BONES)       -> layerEntities[LAYER_BONES]!!.add(entity)
                name.contains(LAYER_IMPLANTS)    -> layerEntities[LAYER_IMPLANTS]!!.add(entity)
                name.contains(LAYER_PROSTHETICS) -> layerEntities[LAYER_PROSTHETICS]!!.add(entity)
                name.contains(LAYER_FACE)        -> layerEntities[LAYER_FACE]!!.add(entity)
            }
        }

        android.util.Log.d(TAG, "Resolved layers: " +
            layerEntities.entries.joinToString { "${it.key}=${it.value.size} entities" })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Layer visibility
    // ─────────────────────────────────────────────────────────────────────────

    @Synchronized
    fun setLayerVisible(layerName: String, visible: Boolean) {
        val entities = layerEntities[layerName] ?: run {
            android.util.Log.w(TAG, "setLayerVisible: unknown layer '$layerName'")
            return
        }
        layerVisibility[layerName] = visible

        val intArray = entities.toIntArray()
        if (visible) {
            scene.addEntities(intArray)
        } else {
            scene.removeEntities(intArray)
        }
    }

    fun getLayerVisibility(): Map<String, Boolean> = layerVisibility.toMap()

    // ─────────────────────────────────────────────────────────────────────────
    // Camera
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupCamera() {
        // Initial projection; updated on every resize via surfaceCallback.onResized
        camera.setProjection(FOV_DEGREES, 1.0, NEAR_PLANE, FAR_PLANE, Camera.Fov.VERTICAL)
        camera.lookAt(
            0.0, 0.15, 0.5,
            0.0, 0.0, 0.0,
            0.0, 1.0, 0.0
        )
    }

    fun resetCamera() {
        orbitTheta = 0.0
        orbitPhi   = 15.0
        applyCameraOrbit()
    }

    private var orbitTheta  = 0.0   // horizontal angle (degrees)
    private var orbitPhi    = 15.0  // vertical angle   (degrees)
    private var orbitRadius = 0.5

    fun orbitCamera(deltaTheta: Double, deltaPhi: Double) {
        orbitTheta = (orbitTheta + deltaTheta) % 360.0
        orbitPhi   = (orbitPhi + deltaPhi).coerceIn(-85.0, 85.0)
        applyCameraOrbit()
    }

    fun zoomCamera(delta: Double) {
        orbitRadius = (orbitRadius - delta * 0.1).coerceIn(0.05, 5.0)
        applyCameraOrbit()
    }

    private fun applyCameraOrbit() {
        val tRad = Math.toRadians(orbitTheta)
        val pRad = Math.toRadians(orbitPhi)
        val ex = orbitRadius * Math.cos(pRad) * Math.sin(tRad)
        val ey = orbitRadius * Math.sin(pRad)
        val ez = orbitRadius * Math.cos(pRad) * Math.cos(tRad)
        camera.lookAt(
            ex, ey, ez,
            0.0, 0.0, 0.0,
            0.0, 1.0, 0.0
        )
    }

    private fun fitCameraToAsset(asset: FilamentAsset) {
        val he = asset.boundingBox.halfExtent
        orbitRadius = (maxOf(he[0], he[1], he[2]) * 2).toDouble().coerceAtLeast(0.3)
        applyCameraOrbit()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lighting
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupLighting() {
        // Indirect light — fallback with no IBL texture; loads an actual .ktx in production
        scene.indirectLight = IndirectLight.Builder()
            .intensity(30_000f)
            .build(engine)

        // Primary directional light
        val sun = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1.0f, 0.98f, 0.92f)
            .intensity(100_000f)
            .direction(0f, -1f, -0.8f)
            .castShadows(true)
            .build(engine, sun)
        scene.addEntity(sun)

        // Soft fill from opposite side
        val fill = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(0.6f, 0.7f, 1.0f)
            .intensity(20_000f)
            .direction(0f, 0.5f, 1f)
            .castShadows(false)
            .build(engine, fill)
        scene.addEntity(fill)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Render loop
    // ─────────────────────────────────────────────────────────────────────────

    private fun renderFrame() {
        if (!uiHelper.isReadyToRender) return
        val sc = swapChain ?: return

        // Tick glTF animations (if present)
        filamentAsset?.instance?.animator?.let { animator ->
            if (animator.animationCount > 0) {
                val timeSeconds = System.nanoTime() / 1_000_000_000f
                animator.applyAnimation(0, timeSeconds)
                animator.updateBoneMatrices()
            }
        }

        if (renderer.beginFrame(sc, System.nanoTime())) {
            renderer.render(view)
            renderer.endFrame()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Surface lifecycle callbacks (UiHelper.RendererCallback)
    // ─────────────────────────────────────────────────────────────────────────

    private val surfaceCallback = object : UiHelper.RendererCallback {
        override fun onNativeWindowChanged(surface: Surface) {
            swapChain?.let { engine.destroySwapChain(it) }
            swapChain = engine.createSwapChain(surface)
            displayHelper.attach(renderer, surfaceView.display)
        }

        override fun onDetachedFromSurface() {
            displayHelper.detach()
            swapChain?.let {
                engine.destroySwapChain(it)
                swapChain = null
            }
        }

        override fun onResized(width: Int, height: Int) {
            view.viewport = Viewport(0, 0, width, height)
            val aspect = width.toDouble() / height.toDouble()
            camera.setProjection(FOV_DEGREES, aspect, NEAR_PLANE, FAR_PLANE, Camera.Fov.VERTICAL)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Start / stop rendering
    // ─────────────────────────────────────────────────────────────────────────

    fun startRendering() {
        if (isRendering) return
        isRendering = true
        choreographer.postFrameCallback(frameCallback)
    }

    fun stopRendering() {
        isRendering = false
        choreographer.removeFrameCallback(frameCallback)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cleanup
    // ─────────────────────────────────────────────────────────────────────────

    fun destroy() {
        stopRendering()
        uiHelper.detach()

        filamentAsset?.let { asset ->
            resourceLoader.asyncCancelLoad()
            scene.removeEntities(asset.entities)
            assetLoader.destroyAsset(asset)
            filamentAsset = null
        }

        resourceLoader.destroy()
        assetLoader.destroy()
        materialProvider.destroyMaterials()
        materialProvider.destroy()

        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyRenderer(renderer)
        swapChain?.let { engine.destroySwapChain(it) }
        engine.destroy()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Constants
    // ─────────────────────────────────────────────────────────────────────────

    companion object {
        private const val TAG             = "FilamentRenderer"
        private const val NEAR_PLANE      = 0.01
        private const val FAR_PLANE       = 100.0
        private const val FOV_DEGREES     = 45.0

        const val LAYER_BONES             = "bones"
        const val LAYER_IMPLANTS          = "implants"
        const val LAYER_PROSTHETICS       = "prosthetics"
        const val LAYER_FACE              = "smile_face"
    }
}
