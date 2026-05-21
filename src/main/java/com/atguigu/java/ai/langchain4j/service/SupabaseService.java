package com.atguigu.java.ai.langchain4j.service;

import com.atguigu.java.ai.langchain4j.model.StockAnalysisResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.*;

@Service
public class SupabaseService {

    private static final Logger log = LoggerFactory.getLogger(SupabaseService.class);

    @Autowired
    @Qualifier("supabaseWebClient")
    private WebClient supabaseWebClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== Stock Analysis ====================

    public JsonNode getAnalysis(String stockCode) {
        try {
            String response = supabaseWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/stock_analysis")
                            .queryParam("stock_code", "eq." + stockCode.toUpperCase())
                            .queryParam("select", "*")
                            .queryParam("limit", 1)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            if (response == null || response.equals("[]")) return null;
            JsonNode arr = objectMapper.readTree(response);
            return arr.size() > 0 ? arr.get(0) : null;
        } catch (Exception e) {
            log.error("查询分析记录失败: {}", e.getMessage());
            return null;
        }
    }

    public void saveAnalysis(String stockCode, StockAnalysisResponse analysis) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stock_code", stockCode);
        body.put("summary", analysis.getSummary());
        body.put("sentiment", analysis.getSentiment());
        body.put("risk_level", analysis.getRiskLevel());
        body.put("updated_at", Instant.now().toString());

        try {
            JsonNode existing = getAnalysis(stockCode);
            if (existing != null) {
                int id = existing.get("id").asInt();
                supabaseWebClient.patch()
                        .uri("/stock_analysis?id=eq." + id)
                        .header("Prefer", "return=minimal")
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
                log.info("Supabase 更新分析成功: {}", stockCode);
            } else {
                body.put("created_at", Instant.now().toString());
                supabaseWebClient.post()
                        .uri("/stock_analysis")
                        .header("Prefer", "return=minimal")
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
                log.info("Supabase 新增分析成功: {}", stockCode);
            }
        } catch (WebClientResponseException e) {
            log.error("Supabase 写入失败: HTTP {} - {}. 请检查: 1)表是否已创建 2)API Key是否正确",
                    e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Supabase 写入异常: {}", e.getMessage(), e);
        }
    }

    // ==================== User Auth ====================

    public JsonNode findUserByPhone(String phone) {
        try {
            String response = supabaseWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/users")
                            .queryParam("phone", "eq." + phone)
                            .queryParam("select", "*")
                            .queryParam("limit", 1)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            if (response == null || response.equals("[]")) return null;
            JsonNode arr = objectMapper.readTree(response);
            return arr.size() > 0 ? arr.get(0) : null;
        } catch (Exception e) {
            log.error("查询用户失败: {}", e.getMessage());
            return null;
        }
    }

    public boolean insertUser(String phone, String passwordMd5) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phone", phone);
        body.put("password_md5", passwordMd5);
        body.put("created_at", Instant.now().toString());

        try {
            supabaseWebClient.post()
                    .uri("/users")
                    .header("Prefer", "return=minimal")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("用户注册成功: {}", phone);
            return true;
        } catch (WebClientResponseException e) {
            log.error("注册用户失败: HTTP {} - {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("注册用户异常: {}", e.getMessage(), e);
            return false;
        }
    }

    // ==================== User Favorites ====================

    public List<String> getFavorites(String phone) {
        try {
            String response = supabaseWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/user_favorites")
                            .queryParam("phone", "eq." + phone)
                            .queryParam("select", "stock_code")
                            .queryParam("order", "created_at.desc")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            if (response == null || response.equals("[]")) return List.of();
            JsonNode arr = objectMapper.readTree(response);
            List<String> codes = new ArrayList<>();
            for (JsonNode node : arr) {
                codes.add(node.get("stock_code").asText());
            }
            return codes;
        } catch (Exception e) {
            log.error("查询收藏失败: {}", e.getMessage());
            return List.of();
        }
    }

    public boolean addFavorite(String phone, String stockCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phone", phone);
        body.put("stock_code", stockCode.toUpperCase());
        body.put("created_at", Instant.now().toString());

        try {
            supabaseWebClient.post()
                    .uri("/user_favorites")
                    .header("Prefer", "return=minimal")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("收藏成功: {} -> {}", phone, stockCode);
            return true;
        } catch (Exception e) {
            log.error("收藏失败: {}", e.getMessage());
            return false;
        }
    }

    public boolean removeFavorite(String phone, String stockCode) {
        try {
            supabaseWebClient.delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/user_favorites")
                            .queryParam("phone", "eq." + phone)
                            .queryParam("stock_code", "eq." + stockCode.toUpperCase())
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("取消收藏: {} -> {}", phone, stockCode);
            return true;
        } catch (Exception e) {
            log.error("取消收藏失败: {}", e.getMessage());
            return false;
        }
    }

    // ==================== Stock Intraday ====================

    public List<Map<String, Object>> getIntradayPoints(String stockCode, String tradeDate) {
        try {
            String response = supabaseWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/stock_intraday")
                            .queryParam("stock_code", "eq." + stockCode.toUpperCase())
                            .queryParam("trade_date", "eq." + tradeDate)
                            .queryParam("select", "time,price,volume")
                            .queryParam("order", "time.asc")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            if (response == null || response.equals("[]")) return List.of();
            JsonNode arr = objectMapper.readTree(response);
            List<Map<String, Object>> list = new ArrayList<>();
            for (JsonNode node : arr) {
                Map<String, Object> point = new LinkedHashMap<>();
                point.put("time", node.get("time").asText());
                point.put("price", node.get("price").asDouble());
                point.put("volume", node.get("volume").asLong());
                list.add(point);
            }
            return list;
        } catch (Exception e) {
            log.error("查询日内走势失败: {}", e.getMessage());
            return List.of();
        }
    }

    public void saveIntradayPoint(String stockCode, String tradeDate, String time, double price, long volume) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stock_code", stockCode.toUpperCase());
        body.put("trade_date", tradeDate);
        body.put("time", time);
        body.put("price", price);
        body.put("volume", volume);

        try {
            supabaseWebClient.post()
                    .uri("/stock_intraday")
                    .header("Prefer", "return=minimal")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            log.error("保存日内数据点失败: {}", e.getMessage());
        }
    }

    public void saveIntradayBatch(String stockCode, String tradeDate, List<Map<String, Object>> points) {
        for (Map<String, Object> point : points) {
            String time = (String) point.get("time");
            double price = ((Number) point.get("price")).doubleValue();
            long volume = ((Number) point.get("volume")).longValue();
            saveIntradayPoint(stockCode, tradeDate, time, price, volume);
        }
    }

    // ==================== Stock History ====================

    public JsonNode getStockHistory(String stockCode) {
        try {
            String response = supabaseWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/stock_history")
                            .queryParam("stock_code", "eq." + stockCode.toUpperCase())
                            .queryParam("select", "*")
                            .queryParam("limit", 1)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            if (response == null || response.equals("[]")) return null;
            JsonNode arr = objectMapper.readTree(response);
            return arr.size() > 0 ? arr.get(0) : null;
        } catch (Exception e) {
            log.error("查询股票历史失败: {}", e.getMessage());
            return null;
        }
    }

    public boolean saveStockHistory(String stockCode, String priceHistoryJson, String lastAnalysisJson) {
        // 先查是否存在，存在则更新，不存在则插入
        JsonNode existing = getStockHistory(stockCode);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stock_code", stockCode.toUpperCase());
        body.put("price_history", priceHistoryJson);
        body.put("last_analysis", lastAnalysisJson);
        body.put("updated_at", Instant.now().toString());

        try {
            if (existing != null) {
                int id = existing.get("id").asInt();
                supabaseWebClient.patch()
                        .uri("/stock_history?id=eq." + id)
                        .header("Prefer", "return=minimal")
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
            } else {
                body.put("created_at", Instant.now().toString());
                supabaseWebClient.post()
                        .uri("/stock_history")
                        .header("Prefer", "return=minimal")
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
            }
            log.info("股票历史保存成功: {}", stockCode);
            return true;
        } catch (Exception e) {
            log.error("股票历史保存失败: {}", e.getMessage());
            return false;
        }
    }

    public boolean updatePriceHistory(String stockCode, String priceHistoryJson) {
        JsonNode existing = getStockHistory(stockCode);
        if (existing == null) return false;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("price_history", priceHistoryJson);
        body.put("updated_at", Instant.now().toString());

        try {
            int id = existing.get("id").asInt();
            supabaseWebClient.patch()
                    .uri("/stock_history?id=eq." + id)
                    .header("Prefer", "return=minimal")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("价格历史更新成功: {}", stockCode);
            return true;
        } catch (Exception e) {
            log.error("价格历史更新失败: {}", e.getMessage());
            return false;
        }
    }
}
