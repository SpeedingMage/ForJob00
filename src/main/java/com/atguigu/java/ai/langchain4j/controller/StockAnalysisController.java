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

    private static final java.util.regex.Pattern STOCK_CODE = java.util.regex.Pattern.compile("^[A-Z]{1,5}$|^\\d{5,6}$");

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestBody StockAnalysisRequest request) {
        String code = request.getStockCode();
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body("股票代码不能为空");
        }
        if (!STOCK_CODE.matcher(code.toUpperCase()).matches()) {
            return ResponseEntity.badRequest().body("股票代码格式不正确（1-10位字母或数字）");
        }
        StockAnalysisResponse response = stockAnalysisService.analyzeStock(code);
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
