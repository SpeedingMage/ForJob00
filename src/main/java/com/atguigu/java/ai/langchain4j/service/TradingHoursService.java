package com.atguigu.java.ai.langchain4j.service;

import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@Service
public class TradingHoursService {

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
        DayOfWeek day = LocalDate.now().getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    public MarketStatus getMarketStatus() {
        if (!isTradingDay()) {
            return MarketStatus.WEEKEND;
        }
        LocalTime now = LocalTime.now();
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

    /**
     * 返回当前交易时段的开始时间，用于回填历史数据。
     * 仅在 OPEN 状态下有意义。
     */
    public LocalTime getSessionStart() {
        LocalTime now = LocalTime.now();
        if (now.isBefore(MORNING_CLOSE)) {
            return MORNING_OPEN;
        }
        return AFTERNOON_OPEN;
    }
}
