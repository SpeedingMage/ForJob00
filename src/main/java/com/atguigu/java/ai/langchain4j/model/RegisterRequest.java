package com.atguigu.java.ai.langchain4j.model;

public class RegisterRequest {
    private String phone;
    private String password;
    private String code;

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
