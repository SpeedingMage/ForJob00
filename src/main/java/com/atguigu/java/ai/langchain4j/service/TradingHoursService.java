package com.atguigu.java.ai.langchain4j.service;

import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;

@Service
public class TradingHoursService {

    private static final ZoneId CHINA_ZONE = ZoneId.of("Asia/Shanghai");

    public enum MarketStatus {
        PRE_MARKET,     // 交易日 00:00-9:30
        OPEN,           // 交易日 9:30-11:30 或 13:00-15:00
        LUNCH_BREAK,    // 交易日 11:30-13:00
        AFTER_MARKET,   // 交易日 15:00-24:00
        WEEKEND         // 周六/周日
    }

    private static final LocalTime MORNING_OPEN = LocalTime.of(9, 30);
    private static final LocalTime MORNING_CLOSE = LocalTime.of(11, 30);
    private static final LocalTime AFTERNOON_OPEN = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_CLOSE = LocalTime.of(15, 0);

    public boolean isTradingDay() {
        DayOfWeek day = LocalDate.now(CHINA_ZONE).getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    public MarketStatus getMarketStatus() {
        if (!isTradingDay()) {
            return MarketStatus.WEEKEND;
        }
        LocalTime now = LocalTime.now(CHINA_ZONE);
        if (now.isBefore(MORNING_OPEN)) {
            return MarketStatus.PRE_MARKET;
        }
        if (now.isBefore(MORNING_CLOSE)) {
            return MarketStatus.OPEN;
        }
        if (now.isBefore(AFTERNOON_OPEN)) {
            return MarketStatus.LUNCH_BREAK;
        }
        if (now.isBefore(AFTERNOON_CLOSE)) {
            return MarketStatus.OPEN;
        }
        return MarketStatus.AFTER_MARKET;
    }

    public LocalTime getSessionStart() {
        LocalTime now = LocalTime.now(CHINA_ZONE);
        if (now.isBefore(MORNING_CLOSE)) {
            return MORNING_OPEN;
        }
        return AFTERNOON_OPEN;
    }

    /** 获取北京时间的当前日期字符串 */
    public static String todayInChina() {
        return LocalDate.now(CHINA_ZONE).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }
}
