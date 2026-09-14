package com.retra.emulator

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.util.AttributeSet
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.min

/**
 * On-demand OpenGL ES 2.0 game-screen renderer used only when a shader is selected.
 * RENDERMODE_WHEN_DIRTY means idle/low-end devices do no continuous GPU work.
 */
class ShaderGameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {
    private val rendererImpl = GameRenderer()

    init {
        setEGLContextClientVersion(2)
        setRenderer(rendererImpl)
        renderMode = RENDERMODE_WHEN_DIRTY
        preserveEGLContextOnPause = true
    }

    fun submitFrame(pixels: IntArray, width: Int, height: Int) {
        rendererImpl.submitFrame(pixels, width, height)
        requestRender()
    }

    fun configure(stretch: Boolean, linearFiltering: Boolean) {
        rendererImpl.configure(stretch, linearFiltering)
        requestRender()
    }

    fun setColorTransform(androidColorMatrix: FloatArray) {
        queueEvent {
            rendererImpl.setColorTransform(androidColorMatrix)
            requestRender()
        }
    }

    fun setFragmentShader(source: String, onResult: (Boolean, String?) -> Unit) {
        // Stage the request and let onDrawFrame compile it. onDrawFrame is only
        // called with a current EGL context, unlike queueEvent while a hidden
        // SurfaceView is between surface-destroy/create transitions on some OEMs.
        rendererImpl.stageFragmentShader(source) { ok, detail ->
            post { onResult(ok, detail) }
        }
        requestRender()
    }

    private class GameRenderer : GLSurfaceView.Renderer {
        private val frameLock = Any()
        private var pendingPixels = IntArray(0)
        private var frameWidth = 0
        private var frameHeight = 0
        private var frameDirty = false
        private var frameBitmap: Bitmap? = null

        private var viewportWidth = 1
        private var viewportHeight = 1
        private var stretch = false
        private var linearFiltering = false
        private var textureId = 0
        private var textureWidth = 0
        private var textureHeight = 0
        private var program = 0
        private var fragmentSource = PASSTHROUGH_FRAGMENT
        private val shaderRequestLock = Any()
        private var pendingFragmentSource: String? = null
        private var pendingFragmentResult: ((Boolean, String?) -> Unit)? = null
        private var positionLocation = -1
        private var texCoordLocation = -1
        private var textureLocation = -1
        private var textureSizeLocation = -1
        private var outputSizeLocation = -1
        private var timeLocation = -1
        private var colorMatrixLocation = -1
        private var colorOffsetLocation = -1
        private var appliedTextureFilter = -1
        private val colorMatrix = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )
        private val colorOffset = floatArrayOf(0f, 0f, 0f, 0f)
        private val startedAtNanos = System.nanoTime()
        private val vertexData = FloatArray(16)
        private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertexData.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        fun submitFrame(pixels: IntArray, width: Int, height: Int) {
            if (width <= 0 || height <= 0) return
            synchronized(frameLock) {
                val size = width * height
                if (pendingPixels.size != size) pendingPixels = IntArray(size)
                System.arraycopy(pixels, 0, pendingPixels, 0, min(size, pixels.size))
                frameWidth = width
                frameHeight = height
                frameDirty = true
            }
        }

        fun configure(stretch: Boolean, linearFiltering: Boolean) {
            this.stretch = stretch
            if (this.linearFiltering != linearFiltering) {
                this.linearFiltering = linearFiltering
                appliedTextureFilter = -1
            }
        }

        fun setColorTransform(androidMatrix: FloatArray) {
            if (androidMatrix.size < 20) return
            // Android ColorMatrix is row-major 4x5. GLSL mat4 uniforms are
            // column-major, with the translation carried separately in 0..1.
            colorMatrix[0] = androidMatrix[0]
            colorMatrix[1] = androidMatrix[5]
            colorMatrix[2] = androidMatrix[10]
            colorMatrix[3] = androidMatrix[15]
            colorMatrix[4] = androidMatrix[1]
            colorMatrix[5] = androidMatrix[6]
            colorMatrix[6] = androidMatrix[11]
            colorMatrix[7] = androidMatrix[16]
            colorMatrix[8] = androidMatrix[2]
            colorMatrix[9] = androidMatrix[7]
            colorMatrix[10] = androidMatrix[12]
            colorMatrix[11] = androidMatrix[17]
            colorMatrix[12] = androidMatrix[3]
            colorMatrix[13] = androidMatrix[8]
            colorMatrix[14] = androidMatrix[13]
            colorMatrix[15] = androidMatrix[18]
            colorOffset[0] = androidMatrix[4] / 255f
            colorOffset[1] = androidMatrix[9] / 255f
            colorOffset[2] = androidMatrix[14] / 255f
            colorOffset[3] = androidMatrix[19] / 255f
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            textureId = createTexture()
            program = buildCompatibleProgram(fragmentSource).first
            cacheProgramLocations()
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            viewportWidth = width.coerceAtLeast(1)
            viewportHeight = height.coerceAtLeast(1)
            GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            applyPendingFragmentShaderIfNeeded()
            if (program == 0 || textureId == 0) return

            uploadLatestFrameIfNeeded()
            val bitmap = frameBitmap ?: return

            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            val filter = if (linearFiltering) GLES20.GL_LINEAR else GLES20.GL_NEAREST
            if (filter != appliedTextureFilter) {
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter)
                appliedTextureFilter = filter
            }

            val xScale: Float
            val yScale: Float
            if (stretch) {
                xScale = 1f
                yScale = 1f
            } else {
                val sourceAspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
                val outputAspect = viewportWidth.toFloat() / viewportHeight.coerceAtLeast(1).toFloat()
                if (outputAspect > sourceAspect) {
                    xScale = sourceAspect / outputAspect
                    yScale = 1f
                } else {
                    xScale = 1f
                    yScale = outputAspect / sourceAspect
                }
            }

            vertexData[0] = -xScale; vertexData[1] = -yScale; vertexData[2] = 0f; vertexData[3] = 1f
            vertexData[4] =  xScale; vertexData[5] = -yScale; vertexData[6] = 1f; vertexData[7] = 1f
            vertexData[8] = -xScale; vertexData[9] =  yScale; vertexData[10] = 0f; vertexData[11] = 0f
            vertexData[12] = xScale; vertexData[13] = yScale; vertexData[14] = 1f; vertexData[15] = 0f
            vertexBuffer.clear()
            vertexBuffer.put(vertexData)
            vertexBuffer.position(0)
            val stride = 4 * 4
            val pos = positionLocation
            val tex = texCoordLocation
            if (pos < 0 || tex < 0) return
            GLES20.glEnableVertexAttribArray(pos)
            GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, stride, vertexBuffer)
            vertexBuffer.position(2)
            GLES20.glEnableVertexAttribArray(tex)
            GLES20.glVertexAttribPointer(tex, 2, GLES20.GL_FLOAT, false, stride, vertexBuffer)

            if (textureLocation >= 0) GLES20.glUniform1i(textureLocation, 0)
            if (textureSizeLocation >= 0) {
                GLES20.glUniform2f(textureSizeLocation, bitmap.width.toFloat(), bitmap.height.toFloat())
            }
            if (outputSizeLocation >= 0) {
                GLES20.glUniform2f(outputSizeLocation, viewportWidth.toFloat(), viewportHeight.toFloat())
            }
            if (timeLocation >= 0) {
                val elapsed = (System.nanoTime() - startedAtNanos) / 1_000_000_000f
                GLES20.glUniform1f(timeLocation, elapsed)
            }
            if (colorMatrixLocation >= 0) {
                GLES20.glUniformMatrix4fv(colorMatrixLocation, 1, false, colorMatrix, 0)
            }
            if (colorOffsetLocation >= 0) {
                GLES20.glUniform4fv(colorOffsetLocation, 1, colorOffset, 0)
            }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(pos)
            GLES20.glDisableVertexAttribArray(tex)
        }

        fun stageFragmentShader(source: String, onResult: (Boolean, String?) -> Unit) {
            synchronized(shaderRequestLock) {
                // Only the most recent selection matters. Settings closes the
                // picker immediately, so replacing an older uncompiled request
                // prevents unnecessary GPU work when users tap presets quickly.
                pendingFragmentSource = source
                pendingFragmentResult = onResult
            }
        }

        private fun applyPendingFragmentShaderIfNeeded() {
            val request = synchronized(shaderRequestLock) {
                val source = pendingFragmentSource ?: return
                val callback = pendingFragmentResult
                pendingFragmentSource = null
                pendingFragmentResult = null
                source to callback
            }

            val result = replaceFragmentShader(request.first)
            request.second?.invoke(result.first, result.second)
        }

        private fun replaceFragmentShader(source: String): Pair<Boolean, String?> {
            val result = buildCompatibleProgram(source)
            val newProgram = result.first
            if (newProgram == 0) {
                return false to (result.second?.trim().takeUnless { it.isNullOrEmpty() }
                    ?: "shader compiler rejected this preset")
            }

            val old = program
            program = newProgram
            fragmentSource = source
            cacheProgramLocations()
            if (old != 0) GLES20.glDeleteProgram(old)
            return true to result.second
        }

        private fun buildCompatibleProgram(source: String): Pair<Int, String?> {
            val safeSource = ensureFragmentPrecision(source)
            val decorated = decorateFragmentShader(safeSource)
            val primary = buildProgram(VERTEX_SHADER, decorated)
            if (primary.first != 0) return primary.first to null

            // Retra's color-transform wrapper is standards-compliant GLES2, but
            // a small number of vendor compilers are stricter around rewritten
            // entry points. Retry the original preset verbatim before rejecting
            // it. Built-in presets are authored as standalone GLES2 shaders, so
            // this is a safe device-compatibility path. The same path is used
            // after EGL/context recreation, not only on the first selection.
            if (decorated != safeSource) {
                val fallback = buildProgram(VERTEX_SHADER, safeSource)
                if (fallback.first != 0) return fallback.first to COMPATIBILITY_MODE

                val primaryDetail = primary.second?.trim().orEmpty()
                val fallbackDetail = fallback.second?.trim().orEmpty()
                val detail = listOf(primaryDetail, fallbackDetail).firstOrNull { it.isNotEmpty() }
                    ?: "shader compiler rejected this preset"
                return 0 to detail
            }

            return 0 to (primary.second?.trim().takeUnless { it.isNullOrEmpty() }
                ?: "shader compiler rejected this preset")
        }

        private fun cacheProgramLocations() {
            if (program == 0) {
                positionLocation = -1
                texCoordLocation = -1
                textureLocation = -1
                textureSizeLocation = -1
                outputSizeLocation = -1
                timeLocation = -1
                colorMatrixLocation = -1
                colorOffsetLocation = -1
                return
            }
            positionLocation = GLES20.glGetAttribLocation(program, "aPosition")
            texCoordLocation = GLES20.glGetAttribLocation(program, "aTexCoord")
            textureLocation = GLES20.glGetUniformLocation(program, "uTexture")
            textureSizeLocation = GLES20.glGetUniformLocation(program, "uTextureSize")
            outputSizeLocation = GLES20.glGetUniformLocation(program, "uOutputSize")
            timeLocation = GLES20.glGetUniformLocation(program, "uTime")
            colorMatrixLocation = GLES20.glGetUniformLocation(program, "uRetraColorMatrix")
            colorOffsetLocation = GLES20.glGetUniformLocation(program, "uRetraColorOffset")
        }

        private fun ensureFragmentPrecision(source: String): String {
            if (PRECISION_LINE.containsMatchIn(source)) return source
            val version = VERSION_LINE.find(source)
            return if (version != null) {
                val end = version.range.last + 1
                source.substring(0, end) + "\nprecision mediump float;\n" + source.substring(end)
            } else {
                "precision mediump float;\n" + source
            }
        }

        private fun decorateFragmentShader(source: String): String {
            val safeSource = ensureFragmentPrecision(source)
            if ("uRetraColorMatrix" in safeSource || "retraUserMain" in safeSource) return safeSource
            val match = MAIN_FUNCTION.find(safeSource) ?: return safeSource
            val renamed = safeSource.replaceRange(
                match.range,
                match.value.replaceFirst(Regex("main\\s*\\("), "retraUserMain(")
            )
            val uniforms = "\nuniform mat4 uRetraColorMatrix;\nuniform vec4 uRetraColorOffset;\n"
            val precision = PRECISION_LINE.findAll(renamed).lastOrNull()
            val version = VERSION_LINE.find(renamed)
            val insertionEnd = precision?.range?.last ?: version?.range?.last
            val withUniforms = if (insertionEnd != null) {
                renamed.substring(0, insertionEnd + 1) + uniforms + renamed.substring(insertionEnd + 1)
            } else {
                uniforms + renamed
            }
            return withUniforms + """

                void main() {
                    retraUserMain();
                    gl_FragColor = clamp(uRetraColorMatrix * gl_FragColor + uRetraColorOffset, vec4(0.0), vec4(1.0));
                }
            """
        }

        private fun uploadLatestFrameIfNeeded() {
            val bitmap = synchronized(frameLock) {
                if (!frameDirty || frameWidth <= 0 || frameHeight <= 0) return
                val reusable = frameBitmap?.takeIf { it.width == frameWidth && it.height == frameHeight && !it.isRecycled }
                    ?: Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888).also {
                        frameBitmap?.recycle()
                        frameBitmap = it
                    }
                reusable.setPixels(pendingPixels, 0, frameWidth, 0, 0, frameWidth, frameHeight)
                frameDirty = false
                reusable
            }
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            if (textureWidth != bitmap.width || textureHeight != bitmap.height) {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
                textureWidth = bitmap.width
                textureHeight = bitmap.height
            } else {
                GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap)
            }
        }

        private fun createTexture(): Int {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            return ids[0]
        }

        private fun buildProgram(vertex: String, fragment: String): Pair<Int, String?> {
            val v = compileShader(GLES20.GL_VERTEX_SHADER, vertex)
            if (v.first == 0) return 0 to v.second
            val f = compileShader(GLES20.GL_FRAGMENT_SHADER, fragment)
            if (f.first == 0) {
                GLES20.glDeleteShader(v.first)
                return 0 to f.second
            }
            val p = GLES20.glCreateProgram()
            GLES20.glAttachShader(p, v.first)
            GLES20.glAttachShader(p, f.first)
            GLES20.glLinkProgram(p)
            val status = IntArray(1)
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
            val error = if (status[0] == 0) GLES20.glGetProgramInfoLog(p)?.trim().takeUnless { it.isNullOrEmpty() } else null
            GLES20.glDeleteShader(v.first)
            GLES20.glDeleteShader(f.first)
            if (status[0] == 0) {
                GLES20.glDeleteProgram(p)
                return 0 to (error ?: "Shader link failed on this GPU")
            }
            return p to null
        }

        private fun compileShader(type: Int, source: String): Pair<Int, String?> {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                val error = GLES20.glGetShaderInfoLog(shader)?.trim().takeUnless { it.isNullOrEmpty() }
                GLES20.glDeleteShader(shader)
                return 0 to (error ?: "Shader compile failed on this GPU")
            }
            return shader to null
        }

        companion object {
            private const val COMPATIBILITY_MODE = "compatibility-mode"
            private val MAIN_FUNCTION = Regex("""void\s+main\s*\(\s*(?:void)?\s*\)\s*\{""")
            private val VERSION_LINE = Regex("""(?m)^\s*#version[^\n]*""")
            private val PRECISION_LINE = Regex("""(?m)^\s*precision\s+\w+\s+\w+\s*;\s*$""")

            private const val VERTEX_SHADER = """
                attribute vec2 aPosition;
                attribute vec2 aTexCoord;
                varying vec2 vTexCoord;
                void main() {
                    gl_Position = vec4(aPosition, 0.0, 1.0);
                    vTexCoord = aTexCoord;
                }
            """
            private const val PASSTHROUGH_FRAGMENT = """
                precision mediump float;
                uniform sampler2D uTexture;
                uniform vec2 uTextureSize;
                uniform vec2 uOutputSize;
                uniform float uTime;
                varying vec2 vTexCoord;
                void main() { gl_FragColor = texture2D(uTexture, vTexCoord); }
            """
        }
    }
}
