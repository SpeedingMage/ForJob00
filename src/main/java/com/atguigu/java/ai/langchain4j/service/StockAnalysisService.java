package com.atguigu.java.ai.langchain4j.service;

import com.atguigu.java.ai.langchain4j.model.RealTimeResponse;
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

    @Autowired
    private TradingHoursService tradingHoursService;

    @Value("classpath:stock-analysis-prompt-template.txt")
    private Resource promptTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Random random = new Random();

    // 内存中存储实时行情数据: stockCode -> [{time, price, volume}, ...]
    private final Map<String, List<Map<String, Object>>> realtimeCache = new ConcurrentHashMap<>();
    // 每个股票的基础实时价格缓存
    private final Map<String, Double> realtimeBasePrice = new ConcurrentHashMap<>();
    // 记录已做过收盘总结的 stockCode+date，防止重复
    private final Set<String> summarizedDates = ConcurrentHashMap.newKeySet();

    // ==================== AI 分析 ====================

    public StockAnalysisResponse analyzeStock(String stockCode) {
        StockQuote quote = fetchStockQuote(stockCode);

        String quoteContext = buildQuoteContext(quote);
        String systemPrompt = loadPromptTemplate();
        String prompt = systemPrompt + "\n\n请分析以下股票行情数据：\n\n" + quoteContext;

        String llmOutput = chatLanguageModel.chat(prompt);
        logLLMOutput(stockCode, llmOutput);
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

    public RealTimeResponse getRealtimeData(String stockCode) {
        TradingHoursService.MarketStatus status = tradingHoursService.getMarketStatus();
        String code = stockCode.toUpperCase();
        String today = TradingHoursService.todayInChina();

        if (status == TradingHoursService.MarketStatus.WEEKEND
                || status == TradingHoursService.MarketStatus.PRE_MARKET) {
            return new RealTimeResponse(status.name(), List.of());
        }

        List<Map<String, Object>> list = realtimeCache.computeIfAbsent(code, k -> new ArrayList<>());
        double base = realtimeBasePrice.computeIfAbsent(code,
                k -> MockStockApiService.generateBasePrice(code));

        if (status == TradingHoursService.MarketStatus.OPEN) {
            synchronized (list) {
                if (list.isEmpty()) {
                    List<Map<String, Object>> dbPoints = supabaseService.getIntradayPoints(code, today);
                    if (!dbPoints.isEmpty()) {
                        list.addAll(dbPoints);
                    } else {
                        LocalTime sessionStart = tradingHoursService.getSessionStart();
                        List<Map<String, Object>> backfill = generateBackfillPoints(code, base, sessionStart);
                        if (!backfill.isEmpty()) {
                            list.addAll(backfill);
                            supabaseService.saveIntradayBatch(code, today, backfill);
                        }
                    }
                }

                double lastPrice = list.isEmpty() ? base
                        : ((Number) list.get(list.size() - 1).get("price")).doubleValue();
                double change = lastPrice * (random.nextDouble() * 0.01 - 0.005);
                double newPrice = BigDecimal.valueOf(lastPrice + change).setScale(2, RoundingMode.HALF_UP).doubleValue();
                long newVolume = 100_000L + random.nextLong(2_000_000L);
                String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

                Map<String, Object> point = new LinkedHashMap<>();
                point.put("time", time);
                point.put("price", newPrice);
                point.put("volume", newVolume);
                list.add(point);

                supabaseService.saveIntradayPoint(code, today, time, newPrice, newVolume);

                // FIFO：保留全天约 4 小时的 5 秒间隔数据
                while (list.size() > 3000) {
                    list.remove(0);
                }
            }
            return new RealTimeResponse(status.name(), new ArrayList<>(list));
        }

        // LUNCH_BREAK 或 AFTER_MARKET：不追加新点，但从DB加载已有数据
        if (status == TradingHoursService.MarketStatus.LUNCH_BREAK
                || status == TradingHoursService.MarketStatus.AFTER_MARKET) {
            synchronized (list) {
                if (list.isEmpty()) {
                    List<Map<String, Object>> dbPoints = supabaseService.getIntradayPoints(code, today);
                    if (!dbPoints.isEmpty()) {
                        list.addAll(dbPoints);
                    }
                }
            }
        }

        if (status == TradingHoursService.MarketStatus.AFTER_MARKET && !list.isEmpty()) {
            summarizeDayIfNeeded(code, today, list);
        }

        return new RealTimeResponse(status.name(), new ArrayList<>(list));
    }

    private List<Map<String, Object>> generateBackfillPoints(String stockCode, double basePrice, LocalTime sessionStart) {
        List<Map<String, Object>> points = new ArrayList<>();
        LocalTime now = LocalTime.now();
        if (!now.isAfter(sessionStart)) return points;

        double price = basePrice * (0.97 + random.nextDouble() * 0.06);
        LocalTime cursor = sessionStart;
        while (cursor.isBefore(now)) {
            double change = price * (random.nextDouble() * 0.002 - 0.001);
            price = BigDecimal.valueOf(price + change).setScale(2, RoundingMode.HALF_UP).doubleValue();
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", cursor.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            point.put("price", price);
            point.put("volume", 100_000L + random.nextLong(2_000_000L));
            points.add(point);
            cursor = cursor.plusSeconds(5);
        }
        return points;
    }

    private void summarizeDayIfNeeded(String stockCode, String tradeDate,
                                      List<Map<String, Object>> intradayData) {
        String key = stockCode + "_" + tradeDate;
        if (!summarizedDates.add(key)) return;

        if (intradayData.isEmpty()) return;

        double open = ((Number) intradayData.get(0).get("price")).doubleValue();
        double close = ((Number) intradayData.get(intradayData.size() - 1).get("price")).doubleValue();
        double high = open;
        double low = open;
        long totalVolume = 0;
        for (Map<String, Object> p : intradayData) {
            double px = ((Number) p.get("price")).doubleValue();
            if (px > high) high = px;
            if (px < low) low = px;
            totalVolume += ((Number) p.get("volume")).longValue();
        }
        high = BigDecimal.valueOf(high).setScale(2, RoundingMode.HALF_UP).doubleValue();
        low = BigDecimal.valueOf(low).setScale(2, RoundingMode.HALF_UP).doubleValue();
        open = BigDecimal.valueOf(open).setScale(2, RoundingMode.HALF_UP).doubleValue();
        close = BigDecimal.valueOf(close).setScale(2, RoundingMode.HALF_UP).doubleValue();

        JsonNode historyNode = supabaseService.getStockHistory(stockCode);
        List<Map<String, Object>> history;
        try {
            if (historyNode != null && historyNode.has("price_history")) {
                JsonNode priceNode = historyNode.get("price_history");
                String priceText = priceNode.isTextual() ? priceNode.asText() : priceNode.toString();
                history = objectMapper.readValue(priceText,
                        new TypeReference<List<Map<String, Object>>>() {});
            } else {
                history = new ArrayList<>();
            }
        } catch (Exception e) {
            history = new ArrayList<>();
        }

        Map<String, Object> day = new LinkedHashMap<>();
        day.put("date", tradeDate);
        day.put("open", open);
        day.put("high", high);
        day.put("low", low);
        day.put("close", close);
        day.put("volume", totalVolume);
        history.add(day);

        while (history.size() > 10) {
            history.remove(0);
        }

        supabaseService.updatePriceHistory(stockCode, toJson(history));
    }

    // ==================== Mock 10 日历史 ====================

    private List<Map<String, Object>> generateMockPriceHistory(String stockCode) {
        double basePrice = MockStockApiService.generateBasePrice(stockCode);
        List<Map<String, Object>> list = new ArrayList<>();
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));

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
            String today = TradingHoursService.todayInChina();
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

    private void logLLMOutput(String stockCode, String llmOutput) {
        try {
            String dir = System.getProperty("user.dir");
            java.nio.file.Path filePath = java.nio.file.Path.of(dir, "src/main/resources/LLMReturn.txt");
            java.nio.file.Files.createDirectories(filePath.getParent());
            String log = "\n===== " + java.time.LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    + " | " + stockCode.toUpperCase() + " =====\n"
                    + llmOutput + "\n";
            java.nio.file.Files.writeString(filePath, log,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            // 静默失败，不影响 AI 分析主流程
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
