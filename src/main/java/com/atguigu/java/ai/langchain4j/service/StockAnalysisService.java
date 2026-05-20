package com.atguigu.java.ai.langchain4j.service;

import com.atguigu.java.ai.langchain4j.model.StockAnalysisResponse;
import com.atguigu.java.ai.langchain4j.model.StockDetailResponse;
import com.atguigu.java.ai.langchain4j.model.StockQuote;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StockAnalysisService {

    @Autowired
    private ChatLanguageModel chatLanguageModel;

    @Autowired
    private SupabaseService supabaseService;

    @Autowired
    private MockStockApiService mockStockApiService;

    @Value("classpath:stock-analysis-prompt-template.txt")
    private Resource promptTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Random random = new Random();

    // 内存中存储实时行情数据: stockCode -> [{time, price, volume}, ...]
    private final Map<String, List<Map<String, Object>>> realtimeCache = new ConcurrentHashMap<>();
    // 每个股票的基础实时价格缓存
    private final Map<String, Double> realtimeBasePrice = new ConcurrentHashMap<>();

    // ==================== AI 分析 ====================

    public StockAnalysisResponse analyzeStock(String stockCode) {
        StockQuote quote = fetchStockQuote(stockCode);

        String quoteContext = buildQuoteContext(quote);
        String systemPrompt = loadPromptTemplate();
        String prompt = systemPrompt + "\n\n请分析以下股票行情数据：\n\n" + quoteContext;

        String llmOutput = chatLanguageModel.chat(prompt);
        StockAnalysisResponse response = parseJsonResponse(llmOutput);

        supabaseService.saveAnalysis(stockCode, response);

        List<Map<String, Object>> history = generateMockPriceHistory(stockCode);
        String historyJson = toJson(history);
        String analysisJson = toJson(response);
        supabaseService.saveStockHistory(stockCode, historyJson, analysisJson);

        // 初始化实时行情基准价
        realtimeCache.remove(stockCode);
        realtimeBasePrice.put(stockCode.toUpperCase(), quote.getPrice().doubleValue());

        return response;
    }

    // ==================== 股票详情 ====================

    public StockDetailResponse getStockDetail(String stockCode) {
        StockDetailResponse detail = new StockDetailResponse();
        detail.setStockCode(stockCode.toUpperCase());

        JsonNode historyNode = supabaseService.getStockHistory(stockCode);
        if (historyNode != null) {
            try {
                // 解析 last_analysis（可能是 JSON 字符串或对象）
                JsonNode analysisNode = historyNode.get("last_analysis");
                if (analysisNode != null && !analysisNode.isNull()) {
                    StockAnalysisResponse analysis;
                    if (analysisNode.isTextual()) {
                        analysis = objectMapper.readValue(analysisNode.asText(), StockAnalysisResponse.class);
                    } else {
                        analysis = objectMapper.treeToValue(analysisNode, StockAnalysisResponse.class);
                    }
                    detail.setAnalysis(analysis);
                }

                // 解析 price_history（存储为 JSON 字符串）
                JsonNode priceNode = historyNode.get("price_history");
                if (priceNode != null && !priceNode.isNull()) {
                    String priceText = priceNode.isTextual() ? priceNode.asText() : priceNode.toString();
                    List<Map<String, Object>> history = objectMapper.readValue(
                            priceText, new TypeReference<List<Map<String, Object>>>() {});
                    detail.setPriceHistory(history);
                }
            } catch (Exception e) {
                detail.setPriceHistory(generateMockPriceHistory(stockCode));
            }
        } else {
            detail.setPriceHistory(generateMockPriceHistory(stockCode));
        }

        return detail;
    }

    // ==================== 实时行情 ====================

    /**
     * 获取/生成实时行情数据。每次调用追加一个数据点（FIFO 队列），5 秒调用一次。
     */
    public List<Map<String, Object>> getRealtimeData(String stockCode) {
        String code = stockCode.toUpperCase();
        List<Map<String, Object>> list = realtimeCache.computeIfAbsent(code, k -> new ArrayList<>());

        // 基准价
        double base = realtimeBasePrice.computeIfAbsent(code,
                k -> MockStockApiService.generateBasePrice(code));

        synchronized (list) {
            double lastPrice;
            if (list.isEmpty()) {
                lastPrice = base;
            } else {
                Object closeObj = list.get(list.size() - 1).get("price");
                lastPrice = ((Number) closeObj).doubleValue();
            }

            // 随机波动 ±0.5%
            double change = lastPrice * (random.nextDouble() * 0.01 - 0.005);
            double newPrice = BigDecimal.valueOf(lastPrice + change).setScale(2, RoundingMode.HALF_UP).doubleValue();
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", time);
            point.put("price", newPrice);
            point.put("volume", 100_000L + random.nextLong(2_000_000L));
            list.add(point);

            // FIFO：最多保留 120 个点（10 分钟）
            while (list.size() > 120) {
                list.remove(0);
            }
        }

        return new ArrayList<>(list); // 返回副本
    }

    // ==================== Mock 10 日历史 ====================

    private List<Map<String, Object>> generateMockPriceHistory(String stockCode) {
        double basePrice = MockStockApiService.generateBasePrice(stockCode);
        List<Map<String, Object>> list = new ArrayList<>();
        LocalDate today = LocalDate.now();

        double price = basePrice * (0.85 + random.nextDouble() * 0.3);
        for (int i = 9; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            double change = price * (random.nextDouble() * 0.06 - 0.03);
            double open = BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP).doubleValue();
            double close = BigDecimal.valueOf(price + change).setScale(2, RoundingMode.HALF_UP).doubleValue();
            double high = BigDecimal.valueOf(Math.max(open, close) * (1 + random.nextDouble() * 0.02)).setScale(2, RoundingMode.HALF_UP).doubleValue();
            double low = BigDecimal.valueOf(Math.min(open, close) * (1 - random.nextDouble() * 0.02)).setScale(2, RoundingMode.HALF_UP).doubleValue();
            long volume = 5_000_000L + random.nextLong(45_000_000L);

            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", date.format(DateTimeFormatter.ISO_LOCAL_DATE));
            day.put("open", open);
            day.put("high", high);
            day.put("low", low);
            day.put("close", close);
            day.put("volume", volume);
            list.add(day);

            price = close;
        }
        return list;
    }

    // ==================== helpers ====================

    private StockQuote fetchStockQuote(String stockCode) {
        return mockStockApiService.fetchQuote(stockCode);
    }

    private String buildQuoteContext(StockQuote quote) {
        return String.format(
                "股票代码: %s\n最新价: %.2f\n涨跌额: %+.2f\n涨跌幅: %s\n成交量: %d\n最高价: %.2f\n最低价: %.2f\n开盘价: %.2f\n昨收价: %.2f",
                quote.getSymbol(), quote.getPrice(), quote.getChange(), quote.getChangePercent(),
                quote.getVolume(), quote.getHigh(), quote.getLow(), quote.getOpen(), quote.getPreviousClose());
    }

    private String loadPromptTemplate() {
        try {
            String template = new String(promptTemplate.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            return template.replace("{{current_date}}", today);
        } catch (IOException e) {
            throw new RuntimeException("无法加载 prompt 模板文件", e);
        }
    }

    StockAnalysisResponse parseJsonResponse(String llmOutput) {
        String json = llmOutput.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```(?:json)?\\s*\\n?", "");
            json = json.replaceFirst("\\n?```\\s*$", "");
        }
        try {
            StockAnalysisResponse response = objectMapper.readValue(json, StockAnalysisResponse.class);
            String sentiment = response.getSentiment();
            if (sentiment != null) {
                String lower = sentiment.trim().toLowerCase();
                if (lower.contains("bullish")) response.setSentiment("Bullish");
                else if (lower.contains("bearish")) response.setSentiment("Bearish");
                else response.setSentiment("Neutral");
            }
            return response;
        } catch (Exception e) {
            throw new RuntimeException("LLM 返回的 JSON 解析失败。原始输出: " + llmOutput, e);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
