package com.yny.mlocr.paddlelite

/**
 * pplite constants
 *
 * @author nianyi.yang
 * create on 2021/7/16 14:41
 */
object Config {
    const val MODEL_DIR_PATH = "models"
    const val LABEL_FILE_PATH = "labels/ppocr_keys_v1.txt"
    const val CPU_THREAD_NUM = 4
    const val CPU_POWER_MODE = "LITE_POWER_HIGH"
    const val INPUT_COLOR_FORMAT = "BGR"

    val INPUT_SHAPE = longArrayOf(1, 3, 960)
    val INPUT_MEAN = floatArrayOf(0.485F, 0.456F, 0.406F)
    val INPUT_STD = floatArrayOf(0.229F, 0.224F, 0.225F)

    const val SCORE_THRESHOLD = 0.1F
}