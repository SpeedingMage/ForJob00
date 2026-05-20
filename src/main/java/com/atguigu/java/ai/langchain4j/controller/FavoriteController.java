package com.atguigu.java.ai.langchain4j.controller;

import com.atguigu.java.ai.langchain4j.model.ApiResponse;
import com.atguigu.java.ai.langchain4j.model.FavoriteRequest;
import com.atguigu.java.ai.langchain4j.service.SupabaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/favorites")
public class FavoriteController {

    @Autowired
    private SupabaseService supabaseService;

    @GetMapping("/list")
    public ApiResponse<List<String>> list(@RequestParam("phone") String phone) {
        return ApiResponse.ok(supabaseService.getFavorites(phone));
    }

    @PostMapping("/add")
    public ApiResponse<Void> add(@RequestBody FavoriteRequest request) {
        boolean ok = supabaseService.addFavorite(request.getPhone(), request.getStockCode());
        return ok ? ApiResponse.ok(null) : ApiResponse.fail("收藏失败");
    }

    @PostMapping("/remove")
    public ApiResponse<Void> remove(@RequestBody FavoriteRequest request) {
        boolean ok = supabaseService.removeFavorite(request.getPhone(), request.getStockCode());
        return ok ? ApiResponse.ok(null) : ApiResponse.fail("取消收藏失败");
    }
}
