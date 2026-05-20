package com.atguigu.java.ai.langchain4j.service;

import com.atguigu.java.ai.langchain4j.model.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** 中国手机号正则 */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /** 内存中暂存验证码：phone -> code */
    private final Map<String, String> codeStore = new ConcurrentHashMap<>();

    @Autowired
    private SupabaseService supabaseService;

    /**
     * 发送验证码（模拟），打印到控制台
     */
    public ApiResponse<Void> sendCode(String phone) {
        if (!isValidPhone(phone)) {
            return ApiResponse.fail("手机号格式不正确");
        }

        String code = String.format("%06d", new Random().nextInt(1000000));
        codeStore.put(phone, code);

        log.info("========================================");
        log.info("  验证码已发送到 {}: {}", phone, code);
        log.info("========================================");

        return ApiResponse.ok("验证码已发送（请查看控制台日志）", null);
    }

    /**
     * 注册
     */
    public ApiResponse<Void> register(String phone, String password, String code) {
        if (!isValidPhone(phone)) {
            return ApiResponse.fail("手机号格式不正确");
        }
        if (password == null || password.length() < 6) {
            return ApiResponse.fail("密码至少 6 位");
        }

        // 检查验证码
        String savedCode = codeStore.get(phone);
        if (savedCode == null) {
            return ApiResponse.fail("请先获取验证码");
        }
        if (!savedCode.equals(code)) {
            return ApiResponse.fail("验证码错误");
        }

        // 检查手机号是否已注册
        JsonNode existing = supabaseService.findUserByPhone(phone);
        if (existing != null) {
            return ApiResponse.fail("该手机号已注册");
        }

        // MD5 加密密码
        String md5 = md5(password);
        boolean ok = supabaseService.insertUser(phone, md5);
        if (!ok) {
            return ApiResponse.fail("注册失败，请稍后重试");
        }

        codeStore.remove(phone);
        log.info("注册成功: {}", phone);
        return ApiResponse.ok("注册成功", null);
    }

    /**
     * 登录
     */
    public ApiResponse<String> login(String phone, String password) {
        if (!isValidPhone(phone)) {
            return ApiResponse.fail("手机号格式不正确");
        }

        JsonNode user = supabaseService.findUserByPhone(phone);
        if (user == null) {
            return ApiResponse.fail("手机号未注册");
        }

        String storedMd5 = user.get("password_md5").asText();
        String inputMd5 = md5(password);

        if (!storedMd5.equals(inputMd5)) {
            return ApiResponse.fail("密码错误");
        }

        log.info("登录成功: {}", phone);
        return ApiResponse.ok("登录成功", phone);
    }

    // ==================== 工具方法 ====================

    static boolean isValidPhone(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone).matches();
    }

    static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
