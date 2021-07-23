package com.yny.mlocr.opengl

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * 使用 OpenGL ES 绘制一个简单的三角形
 *
 * @author nianyi.yang
 * create on 2021/7/22 17:13
 */
class TriangleDrawer(private var textureId: Int = -1) : IDrawer {

    // 顶点坐标
    private val vertexCoors = floatArrayOf(
        -1f, -1f,
        1f, -1f,
        0f, 1f
    )

    // 纹理坐标
    private val textureCoors = floatArrayOf(
        0f, 1f,
        1f, 1f,
        0.5f, 0f
    )

    // OpenGL程序ID
    private var program: Int = -1

    // 顶点坐标接收者
    private var vertexPosHandler: Int = -1

    // 纹理坐标接收者
    private var texturePosHandler: Int = -1

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var textureBuffer: FloatBuffer

    init {
        // step 1 初始化顶点坐标：创建一个 ByteBuffer 对象然后将坐标数据传入
        val b = ByteBuffer.allocateDirect(vertexCoors.size * 4)
        b.order(ByteOrder.nativeOrder())

        vertexBuffer = b.asFloatBuffer()
        vertexBuffer.put(vertexCoors)
        vertexBuffer.position(0)

        val c = ByteBuffer.allocateDirect(textureCoors.size * 4)
        c.order(ByteOrder.nativeOrder())
        textureBuffer = c.asFloatBuffer()
        textureBuffer.put(textureCoors)
        textureBuffer.position(0)
    }

    override fun draw() {
        if (textureId != -1) {
            // step 2 创建、编译、启动着色器
            create()
            // step 3 渲染
            render()
        }
    }

    override fun setTextureID(id: Int) {
        textureId = id
    }

    override fun release() {
        GLES20.glDisableVertexAttribArray(vertexPosHandler)
        GLES20.glDisableVertexAttribArray(texturePosHandler)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        GLES20.glDeleteProgram(program)
    }

    private fun create() {
        if (program == -1) {
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, getVertexShader())
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, getFragmentShader())

            // 创建OpenGL ES程序，注意：需要在OpenGL渲染线程中创建，否则无法渲染
            program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)

            vertexPosHandler = GLES20.glGetAttribLocation(program, "aPosition")
            texturePosHandler = GLES20.glGetAttribLocation(program, "aCoordinate")
        }
        GLES20.glUseProgram(program)
    }

    private fun render() {
        // 启用句柄
        GLES20.glEnableVertexAttribArray(vertexPosHandler)
        GLES20.glEnableVertexAttribArray(texturePosHandler)
        // 设置着色器参数
        GLES20.glVertexAttribPointer(vertexPosHandler, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glVertexAttribPointer(texturePosHandler, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)
        // 开始绘制
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 3)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        // 根据 type 创建顶点着色器或者片元着色器
        val shader = GLES20.glCreateShader(type)
        // 将资源加入到着色器中，并编译
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        return shader
    }

    // GLSL 语言
    private fun getVertexShader(): String {
        return "attribute vec4 aPosition;" +
                "void main() {" +
                "  gl_Position = aPosition;" +
                "}"
    }

    // GLSL 语言
    private fun getFragmentShader(): String {
        return "precision mediump float;" +
                "void main() {" +
                "  gl_FragColor = vec4(1.0, 1.0, 0.0, 1.0);" +
                "}"
    }
}