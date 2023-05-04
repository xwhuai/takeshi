package com.takeshi.enums;

import com.takeshi.constants.TakeshiRedisKeyFormat;

/**
 * 存储一些redis使用的key，可以调用格式化方法给key加对应前缀
 *
 * @author 七濑武【Nanase Takeshi】
 */
public enum TakeshiRedisKeyEnum implements TakeshiRedisKeyFormat {

    /**
     * 私钥
     */
    PRIVATE_KEY_BASE64("privateKeyBase64"),
    /**
     * 公钥
     */
    PUBLIC_KEY_BASE64("publicKeyBase64"),
    /**
     * IP黑名单{clientIp}
     */
    IP_BLACKLIST("ipBlacklist:{}"),
    /**
     * nonce限制{clientIp}{servletPath}
     */
    NONCE_RATE_LIMIT("rate:nonce:{}:{}"),
    /**
     * ip限制{clientIp}{servletPath}
     */
    IP_RATE_LIMIT("rate:ip:{}:{}"),
    /**
     * 重复提交{MD5后的值}
     */
    REPEAT_SUBMIT("repeatSubmit:{}");

    private final String key;

    TakeshiRedisKeyEnum(String key) {
        this.key = key;
    }

    /**
     * 获取key值，格式化文本, {} 表示占位符
     * <br/>
     * 示例："this is {} for {}"
     *
     * @return key
     */
    @Override
    public String getKey() {
        return this.key;
    }

}
