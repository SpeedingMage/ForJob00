package com.atguigu.java.ai.langchain4j.controller;

import com.atguigu.java.ai.langchain4j.model.ApiResponse;
import com.atguigu.java.ai.langchain4j.model.LoginRequest;
import com.atguigu.java.ai.langchain4j.model.RegisterRequest;
import com.atguigu.java.ai.langchain4j.model.SendCodeRequest;
import com.atguigu.java.ai.langchain4j.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/send-code")
    public ApiResponse<Void> sendCode(@RequestBody SendCodeRequest request) {
        return authService.sendCode(request.getPhone());
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@RequestBody RegisterRequest request) {
        return authService.register(request.getPhone(), request.getPassword(), request.getCode());
    }

    @PostMapping("/login")
    public ApiResponse<String> login(@RequestBody LoginRequest request) {
        return authService.login(request.getPhone(), request.getPassword());
    }
}
