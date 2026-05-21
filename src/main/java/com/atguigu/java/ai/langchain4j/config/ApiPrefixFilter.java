package com.atguigu.java.ai.langchain4j.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ApiPrefixFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String uri = req.getRequestURI();
        if (uri.startsWith("/api/")) {
            req = new HttpServletRequestWrapper(req) {
                @Override
                public String getRequestURI() {
                    return uri.substring(4);
                }

                @Override
                public String getServletPath() {
                    return uri.substring(4);
                }
            };
        }
        chain.doFilter(req, response);
    }
}
