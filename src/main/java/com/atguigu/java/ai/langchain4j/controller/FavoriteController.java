package com.atguigu.java.ai.langchain4j.controller;

import com.atguigu.java.ai.langchain4j.model.ApiResponse;
import com.atguigu.java.ai.langchain4j.model.FavoriteRequest;
import com.atguigu.java.ai.langchain4j.service.StockAnalysisService;
import com.atguigu.java.ai.langchain4j.service.SupabaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/favorites")
public class FavoriteController {

    @Autowired
    private SupabaseService supabaseService;

    @Autowired
    private StockAnalysisService stockAnalysisService;

    @GetMapping("/list")
    public ApiResponse<List<String>> list(@RequestParam("phone") String phone) {
        return ApiResponse.ok(supabaseService.getFavorites(phone));
    }

    private static final java.util.regex.Pattern STOCK_CODE = java.util.regex.Pattern.compile("^[A-Z]{1,5}$|^\\d{5,6}$");

    @PostMapping("/add")
    public ApiResponse<Void> add(@RequestBody FavoriteRequest request) {
        String code = request.getStockCode();
        if (code == null || !STOCK_CODE.matcher(code.toUpperCase()).matches()) {
            return ApiResponse.fail("股票代码格式不正确（1-10位字母或数字）");
        }
        boolean ok = supabaseService.addFavorite(request.getPhone(), request.getStockCode());
        if (ok) {
            stockAnalysisService.ensureStockHistory(request.getStockCode());
            return ApiResponse.ok(null);
        }
        return ApiResponse.fail("收藏失败");
    }

    @PostMapping("/remove")
    public ApiResponse<Void> remove(@RequestBody FavoriteRequest request) {
        boolean ok = supabaseService.removeFavorite(request.getPhone(), request.getStockCode());
        return ok ? ApiResponse.ok(null) : ApiResponse.fail("取消收藏失败");
    }
}
