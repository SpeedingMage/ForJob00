package com.atguigu.java.ai.langchain4j.service;

import com.atguigu.java.ai.langchain4j.model.StockQuote;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;

@Service
public class MockStockApiService
{

    private final Random random = new Random ( );

    /**
     * 模拟调用第三方股票行情 API，随机生成该股票代码的行情数据。
     * <p>
     * 后续接入真实 API 时，只需替换此方法内部的实现即可，
     * 调用方 StockAnalysisService 无需任何改动。
     */
    public StockQuote fetchQuote ( String stockCode )
    {
        // 根据股票代码生成一个"稳定"的基准价格（纯本地模拟，非真实价格）
        double basePrice = generateBasePrice ( stockCode );

        // 当日波动幅度（-5% ~ +5%）
        double changePercent = ( random.nextDouble ( ) * 10 - 5 );
        double change = basePrice * changePercent / 100;

        BigDecimal price = BigDecimal.valueOf ( basePrice + change ).setScale ( 2 , RoundingMode.HALF_UP );
        BigDecimal changeAmount = BigDecimal.valueOf ( change ).setScale ( 2 , RoundingMode.HALF_UP );
        BigDecimal open = BigDecimal.valueOf ( basePrice + random.nextDouble ( ) * 2 - 1 ).setScale ( 2 , RoundingMode.HALF_UP );
        BigDecimal previousClose = BigDecimal.valueOf ( basePrice ).setScale ( 2 , RoundingMode.HALF_UP );

        double dayHigh = Math.max ( price.doubleValue ( ) , open.doubleValue ( ) ) * ( 1 + random.nextDouble ( ) * 0.02 );
        double dayLow = Math.min ( price.doubleValue ( ) , open.doubleValue ( ) ) * ( 1 - random.nextDouble ( ) * 0.02 );

        StockQuote quote = new StockQuote ( );
        quote.setSymbol ( stockCode.toUpperCase ( ) );
        quote.setPrice ( price );
        quote.setChange ( changeAmount );
        quote.setChangePercent ( String.format ( "%+.2f%%" , changePercent ) );
        quote.setVolume ( 10_000_000L + random.nextLong ( 90_000_000L ) );
        quote.setHigh ( BigDecimal.valueOf ( dayHigh ).setScale ( 2 , RoundingMode.HALF_UP ) );
        quote.setLow ( BigDecimal.valueOf ( dayLow ).setScale ( 2 , RoundingMode.HALF_UP ) );
        quote.setOpen ( open );
        quote.setPreviousClose ( previousClose );
        quote.setRawData ( "Mock data for " + stockCode.toUpperCase ( ) );
        return quote;
    }

    /**
     * 根据股票代码的 hashCode 生成一个落在合理区间的基准价格。
     * 同一股票代码每次运行基准价格相同，但当日涨跌随机。
     */
    public static double generateBasePrice(String stockCode)
    {
        int hash = Math.abs ( stockCode.toUpperCase ( ).hashCode ( ) );
        // 映射到 10 ~ 3000 的区间，覆盖低价股到高价股
        return 10 + ( hash % 2991 );
    }
}
