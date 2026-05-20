package com.atguigu.java.ai.langchain4j.model;

import java.util.List;
import java.util.Map;

public class StockDetailResponse {

    private String stockCode;
    private StockAnalysisResponse analysis;
    private List<Map<String, Object>> priceHistory; // [{date, open, high, low, close, volume}, ...]

    public String getStockCode() { return stockCode; }
    public void setStockCode(String stockCode) { this.stockCode = stockCode; }

    public StockAnalysisResponse getAnalysis() { return analysis; }
    public void setAnalysis(StockAnalysisResponse analysis) { this.analysis = analysis; }

    public List<Map<String, Object>> getPriceHistory() { return priceHistory; }
    public void setPriceHistory(List<Map<String, Object>> priceHistory) { this.priceHistory = priceHistory; }
}
