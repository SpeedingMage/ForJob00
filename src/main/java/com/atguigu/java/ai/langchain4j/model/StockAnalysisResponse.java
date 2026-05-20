package com.atguigu.java.ai.langchain4j.model;

public class StockAnalysisResponse {

    private String summary;
    private String sentiment;
    private String riskLevel;

    public StockAnalysisResponse() {}

    public StockAnalysisResponse(String summary, String sentiment, String riskLevel) {
        this.summary = summary;
        this.sentiment = sentiment;
        this.riskLevel = riskLevel;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getSentiment() {
        return sentiment;
    }

    public void setSentiment(String sentiment) {
        this.sentiment = sentiment;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }
}
