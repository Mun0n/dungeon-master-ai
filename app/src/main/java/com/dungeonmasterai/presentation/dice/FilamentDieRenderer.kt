package com.dungeonmasterai.presentation.dice

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.core.content.res.ResourcesCompat
import com.dungeonmasterai.R
import android.util.Log
import com.google.android.filament.Box
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.Filament
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.VertexBuffer
import com.google.android.filament.Viewport
import com.google.android.filament.android.TextureHelper
import com.google.android.filament.filamat.MaterialBuilder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import android.os.Handler
import android.os.HandlerThread

internal class FilamentDieRenderer(
    private val context: Context,
) : TextureView.SurfaceTextureListener {
    @Volatile
    var pose: DiePose = D20Choreography.idle(0f)

    private val thread = HandlerThread("valdumbria-die").also { it.start() }
    private val handler = Handler(thread.looper)
    private val alive = AtomicBoolean(true)
    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var filamentView: com.google.android.filament.View? = null
    private var swapChain: SwapChain? = null
    private var windowSurface: Surface? = null
    private var windowTexture: SurfaceTexture? = null
    private var dieEntity: Int = 0
    private var cameraEntity: Int = 0
    private val lightEntities = ArrayList<Int>(2)
    private var dieMaterial: Material? = null
    private var dieInstance: MaterialInstance? = null
    private var dieVertices: VertexBuffer? = null
    private var dieIndices: IndexBuffer? = null
    private var numberAtlas: Texture? = null
    private var indirectLight: IndirectLight? = null
    private var scheduled = false

    fun destroy() {
        if (!alive.compareAndSet(true, false)) return
        handler.post {
            destroyScene()
            thread.quitSafely()
        }
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        windowTexture = texture
        handler.post {
            if (!alive.get()) {
                releaseWindow()
                return@post
            }
            try {
                ensureScene()
                attach(width, height)
                drawSoon()
            } catch (error: Throwable) {
                Log.e(TAG, "The die renderer failed to start.", error)
            }
        }
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
        handler.post {
            val camera = filamentView?.camera ?: return@post
            applyProjection(camera, width, height)
            filamentView?.viewport = Viewport(0, 0, width, height)
        }
    }

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        handler.post { releaseWindow() }
        return false
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    private fun ensureScene() {
        if (engine != null) return
        // Filament.init loads filament-jni. MaterialBuilder loads filamat first,
        // and that order leaves Engine.create without its native methods.
        Filament.init()
        val materials = compileMaterials()
        val created = Engine.create()
        engine = created
        val dieMaterial = Material.Builder().payload(materials, materials.remaining()).build(created)
        val rendered = created.createRenderer().also { renderer ->
            renderer.clearOptions = Renderer.ClearOptions().apply {
                clear = true
                clearColor = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
            }
        }
        renderer = rendered
        val viewed = created.createView().also { view ->
            view.blendMode = com.google.android.filament.View.BlendMode.TRANSLUCENT
        }
        filamentView = viewed
        val shown = created.createScene()
        scene = shown
        viewed.scene = shown
        val entities = EntityManager.get()
        val cameraId = entities.create()
        cameraEntity = cameraId
        val camera = created.createCamera(cameraId)
        viewed.camera = camera
        camera.lookAt(
            D20Model.camera.x.toDouble(),
            D20Model.camera.y.toDouble(),
            D20Model.camera.z.toDouble(),
            0.0,
            0.0,
            0.0,
            0.0,
            1.0,
            0.0,
        )
        val sun = entities.create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(0.90f, 0.88f, 1f)
            .intensity(95_000f)
            .direction(0.25f, -0.88f, -0.4f)
            .build(created, sun)
        shown.addEntity(sun)
        lightEntities.add(sun)
        val fill = entities.create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(0.48f, 0.36f, 0.82f)
            .intensity(32_000f)
            .direction(-0.55f, -0.25f, 0.45f)
            .build(created, fill)
        shown.addEntity(fill)
        lightEntities.add(fill)
        val light = IndirectLight.Builder()
            .irradiance(1, floatArrayOf(0.16f, 0.12f, 0.28f))
            .intensity(36_000f)
            .build(created)
        indirectLight = light
        shown.indirectLight = light
        dieEntity = entities.create()
        this.dieMaterial = dieMaterial
        buildDie(created, shown, dieMaterial, dieEntity)
    }

    private fun attach(width: Int, height: Int) {
        val created = engine ?: return
        val texture = windowTexture ?: return
        releaseSwapChain()
        val surface = Surface(texture)
        windowSurface = surface
        swapChain = created.createSwapChain(surface, SwapChain.CONFIG_TRANSPARENT)
        val camera = filamentView?.camera ?: return
        applyProjection(camera, width, height)
        filamentView?.viewport = Viewport(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
    }

    private fun releaseWindow() {
        releaseSwapChain()
        val texture = windowTexture
        windowTexture = null
        if (texture != null && !texture.isReleased) texture.release()
    }

    private fun releaseSwapChain() {
        val chain = swapChain
        val created = engine
        swapChain = null
        if (created != null && chain != null) {
            created.destroySwapChain(chain)
            // Filament 1.80 returns from destroySwapChain before the driver finishes.
            created.flushAndWait()
        }
        windowSurface?.release()
        windowSurface = null
    }

    private fun applyProjection(camera: Camera, width: Int, height: Int) {
        val aspect = width.toDouble() / height.coerceAtLeast(1).toDouble()
        camera.setProjection(32.0, aspect, 0.08, 20.0, Camera.Fov.VERTICAL)
    }

    private fun drawSoon() {
        if (scheduled || !alive.get()) return
        scheduled = true
        handler.post { frame() }
    }

    private fun frame() {
        scheduled = false
        val created = engine ?: return
        val chain = swapChain ?: return
        val rendered = renderer ?: return
        val viewed = filamentView ?: return
        if (!alive.get()) return
        val transform = created.transformManager
        val instance = transform.getInstance(dieEntity)
        transform.setTransform(instance, poseMatrix(pose))
        if (rendered.beginFrame(chain, System.nanoTime())) {
            rendered.render(viewed)
            rendered.endFrame()
        }
        if (alive.get() && swapChain != null) {
            scheduled = true
            handler.postDelayed({ frame() }, 16)
        }
    }

    private fun destroyScene() {
        releaseWindow()
        val created = engine ?: return
        val shown = scene
        val entities = EntityManager.get()
        listOf(dieEntity).plus(lightEntities).forEach { entity ->
            if (entity != 0) {
                created.destroyEntity(entity)
                entities.destroy(entity)
            }
        }
        lightEntities.clear()
        dieInstance?.let { created.destroyMaterialInstance(it) }
        dieInstance = null
        dieMaterial?.let { created.destroyMaterial(it) }
        dieMaterial = null
        dieVertices?.let { created.destroyVertexBuffer(it) }
        dieIndices?.let { created.destroyIndexBuffer(it) }
        numberAtlas?.let { created.destroyTexture(it) }
        dieVertices = null
        dieIndices = null
        numberAtlas = null
        indirectLight?.let {
            shown?.indirectLight = null
            created.destroyIndirectLight(it)
        }
        indirectLight = null
        if (cameraEntity != 0) {
            created.destroyCameraComponent(cameraEntity)
            entities.destroy(cameraEntity)
            cameraEntity = 0
        }
        renderer?.let { created.destroyRenderer(it) }
        filamentView?.let { created.destroyView(it) }
        shown?.let { created.destroyScene(it) }
        created.destroy()
        engine = null
        renderer = null
        filamentView = null
        scene = null
        dieEntity = 0
    }

    private fun compileMaterials(): ByteBuffer {
        MaterialBuilder.init()
        try {
            return material(
                """
                void material(inout MaterialInputs material) {
                    prepareMaterial(material);
                    material.baseColor = texture(materialParams_albedo, getUV0());
                    material.metallic = 0.04;
                    material.roughness = 0.16;
                    material.reflectance = 0.5;
                    material.clearCoat = 1.0;
                    material.clearCoatRoughness = 0.06;
                }
                """.trimIndent(),
            )
        } finally {
            MaterialBuilder.shutdown()
        }
    }

    private fun material(code: String): ByteBuffer {
        val builder = MaterialBuilder()
            .platform(MaterialBuilder.Platform.MOBILE)
            .name("ValdumbriaDie")
            .shading(MaterialBuilder.Shading.LIT)
            .doubleSided(false)
            .material(code)
            .require(MaterialBuilder.VertexAttribute.UV0)
        builder.samplerParameter(
            MaterialBuilder.SamplerType.SAMPLER_2D,
            MaterialBuilder.SamplerFormat.FLOAT,
            MaterialBuilder.ParameterPrecision.DEFAULT,
            "albedo",
        )
        builder.flipUV(false)
        val compiled = builder.build()
        check(compiled.isValid) { "The die material did not compile." }
        val payload = compiled.buffer
        payload.rewind()
        val copy = ByteBuffer.allocateDirect(payload.remaining()).order(ByteOrder.nativeOrder())
        copy.put(payload)
        copy.flip()
        return copy
    }

    private fun buildDie(engine: Engine, scene: Scene, material: Material, entity: Int) {
        val mesh = D20Model.mesh
        val vertexBuffer = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(mesh.vertices.size)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, STRIDE)
            .attribute(VertexBuffer.VertexAttribute.TANGENTS, 0, VertexBuffer.AttributeType.FLOAT4, 12, STRIDE)
            .attribute(VertexBuffer.VertexAttribute.UV0, 0, VertexBuffer.AttributeType.FLOAT2, 28, STRIDE)
            .build(engine)
        dieVertices = vertexBuffer
        val bytes = ByteBuffer.allocateDirect(mesh.vertices.size * STRIDE).order(ByteOrder.nativeOrder())
        mesh.vertices.forEach { vertex ->
            bytes.putFloat(vertex.position.x)
            bytes.putFloat(vertex.position.y)
            bytes.putFloat(vertex.position.z)
            bytes.putFloat(vertex.orientation.x)
            bytes.putFloat(vertex.orientation.y)
            bytes.putFloat(vertex.orientation.z)
            bytes.putFloat(vertex.orientation.w)
            bytes.putFloat(vertex.uv.x)
            bytes.putFloat(vertex.uv.y)
        }
        bytes.flip()
        vertexBuffer.setBufferAt(engine, 0, bytes)
        val indexBuffer = IndexBuffer.Builder()
            .indexCount(mesh.indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        dieIndices = indexBuffer
        val indices = ByteBuffer.allocateDirect(mesh.indices.size * 2).order(ByteOrder.nativeOrder())
        mesh.indices.forEach { indices.putShort(it) }
        indices.flip()
        indexBuffer.setBuffer(engine, indices)
        val texture = numberTexture(engine)
        numberAtlas = texture
        val instance = material.createInstance()
        dieInstance = instance
        instance.setParameter(
            "albedo",
            texture,
            TextureSampler(
                TextureSampler.MinFilter.LINEAR,
                TextureSampler.MagFilter.LINEAR,
                TextureSampler.WrapMode.CLAMP_TO_EDGE,
            ),
        )
        RenderableManager.Builder(1)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer)
            .material(0, instance)
            .boundingBox(Box(0f, 0f, 0f, 1.2f, 1.2f, 1.2f))
            .castShadows(false)
            .receiveShadows(false)
            .build(engine, entity)
        scene.addEntity(entity)
    }

    private fun numberTexture(engine: Engine): Texture {
        val bitmap = numberAtlas()
        val texture = Texture.Builder()
            .width(bitmap.width)
            .height(bitmap.height)
            .levels(1)
            .sampler(Texture.Sampler.SAMPLER_2D)
            .format(Texture.InternalFormat.SRGB8_A8)
            .build(engine)
        TextureHelper.setBitmap(engine, texture, 0, bitmap)
        bitmap.recycle()
        return texture
    }

    private fun numberAtlas(): Bitmap {
        val size = 1024
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(198, 194, 214))
        val face = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(46, 38, 78) }
        val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(236, 232, 246)
            textAlign = Paint.Align.CENTER
            typeface = ResourcesCompat.getFont(context, R.font.playfair_display)
        }
        glyph.textSize = textSizeThatFits(glyph, size.toFloat())
        for (value in 1..20) {
            val corners = D20Model.faceCorners(value)
            val centerX = corners.sumOf { it.x.toDouble() }.toFloat() / corners.size
            val centerY = corners.sumOf { it.y.toDouble() }.toFloat() / corners.size
            val path = Path()
            corners.forEachIndexed { index, corner ->
                val x = (centerX + (corner.x - centerX) * 1.18f) * size
                val y = (centerY + (corner.y - centerY) * 1.18f) * size
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            canvas.drawPath(path, face)
            val anchor = D20Model.numberAnchor(value)
            val baseline = anchor.y * size - (glyph.ascent() + glyph.descent()) / 2f
            canvas.drawText(value.toString(), anchor.x * size, baseline, glyph)
        }
        return bitmap
    }

    /**
     * One size for every face, chosen so the widest numeral stays inside the
     * triangle. A fixed size ran past the bevel and the digit was cut off.
     */
    private fun textSizeThatFits(glyph: Paint, atlas: Float): Float {
        var low = 12f
        var high = atlas / 6f
        repeat(14) {
            val mid = (low + high) * 0.5f
            if (widestNumeralFits(glyph, mid, atlas)) low = mid else high = mid
        }
        return low
    }

    private fun widestNumeralFits(glyph: Paint, textSize: Float, atlas: Float): Boolean {
        glyph.textSize = textSize
        val width = glyph.measureText(WIDEST_NUMERAL)
        val top = -(glyph.ascent() + glyph.descent()) / 2f + glyph.ascent()
        val bottom = -(glyph.ascent() + glyph.descent()) / 2f + glyph.descent()
        return (1..20).all { value ->
            val corners = D20Model.faceCorners(value)
            val centerX = corners.sumOf { it.x.toDouble() }.toFloat() / corners.size * atlas
            val centerY = corners.sumOf { it.y.toDouble() }.toFloat() / corners.size * atlas
            val triangle = corners.map { corner ->
                val x = corner.x * atlas
                val y = corner.y * atlas
                centerX + (x - centerX) * FACE_MARGIN to centerY + (y - centerY) * FACE_MARGIN
            }
            listOf(
                centerX - width / 2f to centerY + top,
                centerX + width / 2f to centerY + top,
                centerX - width / 2f to centerY + bottom,
                centerX + width / 2f to centerY + bottom,
            ).all { (x, y) -> insideTriangle(x, y, triangle) }
        }
    }

    private fun insideTriangle(x: Float, y: Float, triangle: List<Pair<Float, Float>>): Boolean {
        val (ax, ay) = triangle[0]
        val (bx, by) = triangle[1]
        val (cx, cy) = triangle[2]
        val dx = x - cx
        val dy = y - cy
        val dx21 = cx - bx
        val dy12 = by - cy
        val denom = dy12 * (ax - cx) + dx21 * (ay - cy)
        val s = dy12 * dx + dx21 * dy
        val t = (cy - ay) * dx + (ax - cx) * dy
        return if (denom < 0f) {
            s <= 0f && t <= 0f && s + t >= denom
        } else {
            s >= 0f && t >= 0f && s + t <= denom
        }
    }

    private companion object {
        const val TAG: String = "FilamentDie"
        const val STRIDE: Int = 36
        const val WIDEST_NUMERAL: String = "20"
        const val FACE_MARGIN: Float = 0.82f
    }
}
