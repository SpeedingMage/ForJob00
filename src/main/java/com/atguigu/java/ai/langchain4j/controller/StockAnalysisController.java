package com.atguigu.java.ai.langchain4j.controller;

import com.atguigu.java.ai.langchain4j.model.StockAnalysisRequest;
import com.atguigu.java.ai.langchain4j.model.StockAnalysisResponse;
import com.atguigu.java.ai.langchain4j.model.StockDetailResponse;
import com.atguigu.java.ai.langchain4j.service.StockAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/stock")
public class StockAnalysisController {

    @Autowired
    private StockAnalysisService stockAnalysisService;

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestBody StockAnalysisRequest request) {
        if (request.getStockCode() == null || request.getStockCode().isBlank()) {
            return ResponseEntity.badRequest().body("股票代码不能为空");
        }
        StockAnalysisResponse response = stockAnalysisService.analyzeStock(request.getStockCode());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/detail")
    public ResponseEntity<?> detail(@RequestParam("stockCode") String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return ResponseEntity.badRequest().body("股票代码不能为空");
        }
        StockDetailResponse detail = stockAnalysisService.getStockDetail(stockCode);
        return ResponseEntity.ok(detail);
    }

    @GetMapping("/realtime")
    public ResponseEntity<?> realtime(@RequestParam("stockCode") String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return ResponseEntity.badRequest().body("股票代码不能为空");
        }
        return ResponseEntity.ok(stockAnalysisService.getRealtimeData(stockCode));
    }
}
