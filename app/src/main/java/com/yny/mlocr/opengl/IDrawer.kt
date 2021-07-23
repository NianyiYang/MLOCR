package com.yny.mlocr.opengl

/**
 * 渲染接口
 *
 * @author nianyi.yang
 * create on 2021/7/22 17:12
 */
interface IDrawer {
    fun draw()
    fun setTextureID(id:Int)
    fun release()
}