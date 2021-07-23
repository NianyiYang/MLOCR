package com.yny.mlocr.opengl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.yny.mlocr.R
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * 简单的 OpenGL 渲染器
 *
 * @author nianyi.yang
 * create on 2021/7/22 17:05
 */
class SimpleRenderActivity : AppCompatActivity() {

    private var drawer: IDrawer = TriangleDrawer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_render)
        val glSurface = findViewById<GLSurfaceView>(R.id.gl_surface)
        glSurface.setEGLContextClientVersion(2)
        glSurface.setRenderer(SimpleRender(drawer))

    }

    override fun onDestroy() {
        drawer.release()
        super.onDestroy()
    }

    class SimpleRender(private val drawer: IDrawer) : GLSurfaceView.Renderer {
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0F, 0F, 0F, 0F)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            drawer.setTextureID(createTextureIds(1)[0])
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            drawer.draw()
        }

        fun createTextureIds(count: Int): IntArray {
            val texture = IntArray(count)
            GLES20.glGenTextures(count, texture, 0) //生成纹理
            return texture
        }
    }
}