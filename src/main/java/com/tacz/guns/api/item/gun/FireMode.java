package com.tacz.guns.api.item.gun;

import com.google.gson.annotations.SerializedName;

public enum FireMode {
    /**
     * 全自动
     */
    @SerializedName("auto")
    AUTO,
    /**
     * 半自动
     */
    @SerializedName("semi")
    SEMI,
    /**
     * 多连发
     */
    @SerializedName("burst")
    BURST,
    /**
     * 保险模式 (Safe)
     */
    @SerializedName("safe")
    SAFE,
    /**
     * 未知的其他情况？
     */
    @SerializedName("unknown")
    UNKNOWN
}
