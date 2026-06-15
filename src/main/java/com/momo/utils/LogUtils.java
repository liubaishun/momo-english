package com.momo.utils;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * @author ming.li1@yintech.cn
 * @version 1.0
 * @description 日志工具
 * @Company: yintech
 * @date 2021/6/22 5:34 下午
 */
@Slf4j
public class LogUtils {
    private static final String UNIQUE_ID = "traceId";

    /**
     * 获取日志追踪id格式
     */
    public static String getTraceId() {
        return System.currentTimeMillis() + ((int) (Math.random() * 900) + 100) + "";
    }

    /**
     * 插入traceId
     */
    public static boolean insert() {
        try {
            MDC.put(UNIQUE_ID, getTraceId());
        } catch (Exception e) {
            //log.error("log insert mdc error,{}", e);
            return false;
        }
        return true;
    }

    /**
     * 移除traceId
     */
    public static boolean remove() {
        try {
            MDC.remove(UNIQUE_ID);
        } catch (Exception e) {
            //log.error("log remove mdc error,{}", e);
        }
        return true;
    }
}
