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
        queueEvent {
            val result = rendererImpl.replaceFragmentShader(source)
            post { onResult(result.first, result.second) }
            requestRender()
        }
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
            this.linearFiltering = linearFiltering
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
            program = buildProgram(VERTEX_SHADER, decorateFragmentShader(fragmentSource)).first
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            viewportWidth = width.coerceAtLeast(1)
            viewportHeight = height.coerceAtLeast(1)
            GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            if (program == 0 || textureId == 0) return

            uploadLatestFrameIfNeeded()
            val bitmap = frameBitmap ?: return

            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            val filter = if (linearFiltering) GLES20.GL_LINEAR else GLES20.GL_NEAREST
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, filter)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filter)

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
            val pos = GLES20.glGetAttribLocation(program, "aPosition")
            val tex = GLES20.glGetAttribLocation(program, "aTexCoord")
            GLES20.glEnableVertexAttribArray(pos)
            GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, stride, vertexBuffer)
            vertexBuffer.position(2)
            GLES20.glEnableVertexAttribArray(tex)
            GLES20.glVertexAttribPointer(tex, 2, GLES20.GL_FLOAT, false, stride, vertexBuffer)

            GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uTextureSize"), bitmap.width.toFloat(), bitmap.height.toFloat())
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uOutputSize"), viewportWidth.toFloat(), viewportHeight.toFloat())
            val elapsed = (System.nanoTime() - startedAtNanos) / 1_000_000_000f
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uTime"), elapsed)
            val colorMatrixLocation = GLES20.glGetUniformLocation(program, "uRetraColorMatrix")
            if (colorMatrixLocation >= 0) {
                GLES20.glUniformMatrix4fv(colorMatrixLocation, 1, false, colorMatrix, 0)
            }
            val colorOffsetLocation = GLES20.glGetUniformLocation(program, "uRetraColorOffset")
            if (colorOffsetLocation >= 0) {
                GLES20.glUniform4fv(colorOffsetLocation, 1, colorOffset, 0)
            }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(pos)
            GLES20.glDisableVertexAttribArray(tex)
        }

        fun replaceFragmentShader(source: String): Pair<Boolean, String?> {
            val (newProgram, error) = buildProgram(VERTEX_SHADER, decorateFragmentShader(source))
            if (newProgram == 0) return false to error
            val old = program
            program = newProgram
            fragmentSource = source
            if (old != 0) GLES20.glDeleteProgram(old)
            return true to null
        }

        private fun decorateFragmentShader(source: String): String {
            if ("uRetraColorMatrix" in source || "retraUserMain" in source) return source
            val match = MAIN_FUNCTION.find(source) ?: return source
            val renamed = source.replaceRange(
                match.range,
                match.value.replaceFirst(Regex("main\\s*\\("), "retraUserMain(")
            )
            val uniforms = "\nuniform mat4 uRetraColorMatrix;\nuniform vec4 uRetraColorOffset;\n"
            val withUniforms = VERSION_LINE.find(renamed)?.let { version ->
                renamed.substring(0, version.range.last + 1) + uniforms + renamed.substring(version.range.last + 1)
            } ?: (uniforms + renamed)
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
            val error = if (status[0] == 0) GLES20.glGetProgramInfoLog(p) else null
            GLES20.glDeleteShader(v.first)
            GLES20.glDeleteShader(f.first)
            if (status[0] == 0) {
                GLES20.glDeleteProgram(p)
                return 0 to (error ?: "Shader link failed")
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
                val error = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                return 0 to (error ?: "Shader compile failed")
            }
            return shader to null
        }

        companion object {
            private val MAIN_FUNCTION = Regex("""void\s+main\s*\(\s*(?:void)?\s*\)\s*\{""")
            private val VERSION_LINE = Regex("""(?m)^\s*#version[^\n]*""")

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
