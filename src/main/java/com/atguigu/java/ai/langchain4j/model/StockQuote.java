package com.atguigu.java.ai.langchain4j.model;

import java.math.BigDecimal;

public class StockQuote {

    private String symbol;
    private BigDecimal price;
    private BigDecimal change;
    private String changePercent;
    private Long volume;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal open;
    private BigDecimal previousClose;
    private String rawData;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getChange() { return change; }
    public void setChange(BigDecimal change) { this.change = change; }

    public String getChangePercent() { return changePercent; }
    public void setChangePercent(String changePercent) { this.changePercent = changePercent; }

    public Long getVolume() { return volume; }
    public void setVolume(Long volume) { this.volume = volume; }

    public BigDecimal getHigh() { return high; }
    public void setHigh(BigDecimal high) { this.high = high; }

    public BigDecimal getLow() { return low; }
    public void setLow(BigDecimal low) { this.low = low; }

    public BigDecimal getOpen() { return open; }
    public void setOpen(BigDecimal open) { this.open = open; }

    public BigDecimal getPreviousClose() { return previousClose; }
    public void setPreviousClose(BigDecimal previousClose) { this.previousClose = previousClose; }

    public String getRawData() { return rawData; }
    public void setRawData(String rawData) { this.rawData = rawData; }
}
