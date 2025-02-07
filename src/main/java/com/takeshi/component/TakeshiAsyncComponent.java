package com.takeshi.component;

import cn.hutool.core.net.Ipv4Util;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.db.DbUtil;
import cn.hutool.db.Entity;
import cn.hutool.http.Header;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.takeshi.annotation.TakeshiLog;
import com.takeshi.constants.TakeshiCode;
import com.takeshi.constants.TakeshiConstants;
import com.takeshi.pojo.basic.ResponseData;
import com.takeshi.pojo.basic.TbSysLog;
import com.takeshi.pojo.bo.ParamBO;
import com.takeshi.util.GsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

/**
 * TakeshiAsyncComponent
 *
 * @author 七濑武【Nanase Takeshi】
 */
@Slf4j
@Async
@Component
@RequiredArgsConstructor
public class TakeshiAsyncComponent {

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    /**
     * 排除敏感属性字段
     */
    public static final String[] EXCLUSION_FIELD_NAME = {"password", "oldPassword", "newPassword", "confirmPassword"};

    /**
     * 新增一条接口请求相关信息到数据库
     *
     * @param paramBO         paramBO
     * @param startTimeMillis 请求时间
     * @param totalTimeMillis 接口总耗时
     * @param responseData    接口响应数据
     */
    public void insertSysLog(ParamBO paramBO, long startTimeMillis, long totalTimeMillis, String responseData) {
        try {
            if (ObjUtil.isNotNull(paramBO)) {
                TakeshiLog takeshiLog = paramBO.getTakeshiLog();
                if (ObjUtil.isNotNull(takeshiLog)) {
                    String[] exclusionFieldName = Stream.of(EXCLUSION_FIELD_NAME, takeshiLog.exclusionFieldName()).flatMap(Arrays::stream).toArray(String[]::new);
                    ObjectNode paramObjectNode = paramBO.getParamObjectNode(exclusionFieldName);
                    TbSysLog tbSysLog = new TbSysLog();
                    tbSysLog.setLogType(takeshiLog.logType().name());
                    tbSysLog.setLoginId(paramBO.getLoginId());
                    tbSysLog.setClientIp(Ipv4Util.ipv4ToLong(paramBO.getClientIp()));
                    Map<String, String> headerParam = paramBO.getHeaderParam();
                    tbSysLog.setClientIpAddress(paramBO.getClientIpAddress());
                    tbSysLog.setUserAgent(headerParam.get(Header.USER_AGENT.getValue()));
                    tbSysLog.setHttpMethod(paramBO.getHttpMethod());
                    tbSysLog.setMethodName(paramBO.getMethodName());
                    tbSysLog.setRequestUrl(paramBO.getRequestUrl());
                    tbSysLog.setRequestHeader(GsonUtil.toJson(headerParam));
                    tbSysLog.setRequestParams(paramObjectNode.toString());
                    tbSysLog.setResponseData(StrUtil.emptyToNull(responseData));
                    tbSysLog.setTraceId(MDC.get(TakeshiConstants.TRACE_ID_KEY));
                    tbSysLog.setSuccessful(this.successful(responseData));
                    tbSysLog.setRequestTime(startTimeMillis);
                    tbSysLog.setCostTime(totalTimeMillis);
                    long epochMilli = Instant.now().toEpochMilli();
                    tbSysLog.setCreateTime(epochMilli);
                    tbSysLog.setUpdateTime(epochMilli);
                    DbUtil.use(dataSource).insert(Entity.parseWithUnderlineCase(tbSysLog));
                }
            }
        } catch (Exception e) {
            log.error("TakeshiAsyncComponent.insertSysLog --> e: ", e);
        }
    }

    /**
     * 判断返回结果是否是成功的
     *
     * @param responseData 接口响应数据
     * @return boolean
     */
    private boolean successful(String responseData) {
        try {
            if (StrUtil.isNotEmpty(responseData)) {
                ResponseData<?> data = objectMapper.readValue(responseData, ResponseData.class);
                return data.getCode() == TakeshiCode.SUCCESS.getCode();
            }
        } catch (JsonProcessingException ignored) {
        }
        return true;
    }

}
