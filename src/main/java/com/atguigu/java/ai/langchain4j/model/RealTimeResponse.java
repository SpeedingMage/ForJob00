package com.atguigu.java.ai.langchain4j.model;

import java.util.List;
import java.util.Map;

public class RealTimeResponse {

    private String status;
    private List<Map<String, Object>> data;

    public RealTimeResponse() {}

    public RealTimeResponse(String status, List<Map<String, Object>> data) {
        this.status = status;
        this.data = data;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<Map<String, Object>> getData() { return data; }
    public void setData(List<Map<String, Object>> data) { this.data = data; }
}
