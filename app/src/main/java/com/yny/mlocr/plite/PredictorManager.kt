package com.yny.mlocr.plite

import android.content.Context
import android.graphics.Bitmap

/**
 * PaddlePaddle 预测模型管理类
 *
 * @author nianyi.yang
 * create on 2021/7/16 14:52
 */
object PredictorManager {
    private var predictor: Predictor = Predictor()

    fun init(context: Context): Boolean {
        return predictor.init(
            context,
            Config.MODEL_DIR_PATH,
            Config.LABEL_FILE_PATH,
            Config.CPU_THREAD_NUM,
            Config.CPU_POWER_MODE,
            Config.INPUT_COLOR_FORMAT,
            Config.INPUT_SHAPE,
            Config.INPUT_MEAN,
            Config.INPUT_STD,
            Config.SCORE_THRESHOLD
        )
    }

    fun runModel(): Boolean {
        return predictor.isLoaded() && predictor.runModel()
    }

    fun onImageChanged(image: Bitmap?): Boolean {
        // Rerun model if users pick test image from gallery or camera
        if (image != null && predictor.isLoaded()) {
            predictor.setInputImage(image)
            return runModel()
        }

        return false
    }

    @Synchronized
    fun getOutputResult(): String = predictor.outputResult
}