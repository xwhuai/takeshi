package com.takeshi.config.properties;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.redisson.api.RateIntervalUnit;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RateLimitProperties
 *
 * @author 七濑武【Nanase Takeshi】
 */
@Data
@AutoConfiguration
@ConfigurationProperties(prefix = "takeshi.rate")
public class RateLimitProperties {

    /**
     * 接口header里传递的timestamp最多只能早于系统当前时间{maxTimeDiff}秒<br/>
     * 设置0则不校验
     */
    @Min(0)
    private int maxTimeDiff = 60;

    /**
     * 接口header里传递的nonce限制
     */
    private NonceRate nonce = new NonceRate();

    /**
     * 同个IP对接口请求的限制，超过限制将会把IP拉入黑名单直至当天结束时间（例如：2023-04-23 23:59:59）
     */
    private IpRate ip = new IpRate();

    /**
     * 接口header里传递的nonce限制
     */
    @Data
    public static class NonceRate {

        /**
         * 率
         */
        @Min(1)
        private int rate = 1;

        /**
         * 速率时间间隔，设置0则不对nonce限制
         */
        @Min(0)
        private int rateInterval = 60;

        /**
         * 速率时间间隔单位
         */
        private RateIntervalUnit rateIntervalUnit = RateIntervalUnit.SECONDS;

    }

    /**
     * 同个IP对接口请求的限制，超过限制将会把IP拉入黑名单直至当天结束时间（例如：2023-04-23 23:59:59）
     */
    @Data
    public static class IpRate {

        /**
         * 率
         */
        @Min(1)
        private int rate = 5;

        /**
         * 速率时间间隔，设置0则不对接口IP限制
         */
        @Min(0)
        private int rateInterval = 1;

        /**
         * 速率时间间隔单位
         */
        private RateIntervalUnit rateIntervalUnit = RateIntervalUnit.SECONDS;

        /**
         * 是否开启IP黑名单
         */
        private boolean openBlacklist = true;

    }

}
