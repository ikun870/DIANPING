package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.chat.memory.ChatMemoryProvider;
import com.hmdp.dto.SeckillVoucherDTO;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.service.ConsultantService;
import com.hmdp.utils.UserHolder;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 基于 OpenAiChatModel 的简单实现，避免使用 AiServices 动态代理。
 */
@Service
@Slf4j
public class ConsultantServiceImpl implements ConsultantService {

    private final OpenAiChatModel chatModel;
    private final OpenAiStreamingChatModel streamingChatModel; // 允许为 null
    private final String systemPrompt; // 从资源文件加载
    private final ChatMemoryProvider chatMemoryProvider; // 用户级提供者
    private final ContentRetriever contentRetriever; // RAG 内容检索

    private final VoucherMapper voucherMapper;
    private final SeckillVoucherMapper seckillVoucherMapper;
    private final ShopMapper shopMapper;



    @Value("${openai.rag.enabled:true}")
    private boolean ragEnabled; // 全局开关 (配置不存在时默认启用，可按需改 false)

    //这里可以不写注解，前提是openAiConfig 已经配置了 OpenAiChatModel 和 OpenAiStreamingChatModel,以及
    //ConsultantConfig 已经配置了 ChatMemoryProvider
    public ConsultantServiceImpl(OpenAiChatModel chatModel, OpenAiStreamingChatModel streamingChatModel, ChatMemoryProvider chatMemoryProvider, ContentRetriever contentRetriever,
                                 VoucherMapper voucherMapper, SeckillVoucherMapper seckillVoucherMapper, ShopMapper shopMapper) {
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
        this.chatMemoryProvider = chatMemoryProvider;
        this.contentRetriever = contentRetriever;
        this.systemPrompt = loadSystemPrompt();

        this.voucherMapper = voucherMapper;
        this.seckillVoucherMapper = seckillVoucherMapper;
        this.shopMapper = shopMapper;

        
    }

    private String loadSystemPrompt() {
        ClassPathResource resource = new ClassPathResource("system.txt");
        if (!resource.exists()) {
            return "你是一个中文助理。"; // fallback
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n")).trim();
        } catch (Exception e) {
            return "你是一个中文助理。"; // fallback
        }
    }

    /* */
    @Override
    public String chat(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        // 未登录直接拒绝
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return "请先登录后再使用智能顾问功能";
        }
        try {
            ChatMemory chatMemory = currentUserMemory();
            chatMemory.add(UserMessage.from(message));
            // 2. 构造带 systemPrompt 的上下文
            List<ChatMessage> messages = ragEnabled ? buildMessagesWithRag(message) : buildMessagesPlain();
            ChatResponse response = chatModel.chat(messages);
            if (response == null || response.aiMessage() == null || response.aiMessage().text() == null) {
                return "";
            }
            chatMemory.add(response.aiMessage()); // 3. 记录 AI 回复
            return response.aiMessage().text();
        } catch (Exception e) {
            return "调用大模型失败: " + e.getMessage();
        }
    }

    @Override
    public Flux<String> chatStreamFlux(String message) {
        if (message == null || message.isBlank()) {
            return Flux.empty();
        }
        // 未登录拒绝
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Flux.error(new RuntimeException("未登录，无法使用智能顾问"));
        }
        
        ChatMemory chatMemory = currentUserMemory();
        chatMemory.add(UserMessage.from(message));
            // 2. 构造完整上下文
        List<ChatMessage> messages = ragEnabled ? buildMessagesWithRag(message) : buildMessagesPlain();

        if (streamingChatModel == null) {
            // 直接返回模型提供的字符串；不要二次编码（否则会把 UTF-16 -> 默认编码 -> 重新按 UTF-8 解读导致乱码）
            try {
                ChatResponse response = chatModel.chat(messages);
                String text = response != null && response.aiMessage() != null ? response.aiMessage().text() : "";
                if (response != null && response.aiMessage() != null) {
                    chatMemory.add(response.aiMessage());
                }
                return Flux.just(text);
            } catch (Exception e) {
                return Flux.error(new RuntimeException("调用大模型失败: " + e.getMessage()));
            }
        }
        return Flux.create(sink -> {
            StringBuilder sb = new StringBuilder();
            streamingChatModel.chat(messages, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    // 直接输出库已经构造好的 Java String；避免二次编码导致乱码（平台默认编码 -> UTF-8 误解码）
                    sb.append(partialResponse);
                    sink.next(partialResponse);
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    // 记录 AI 完整回复
                    if (completeResponse != null && completeResponse.aiMessage() != null) {
                        chatMemory.add(completeResponse.aiMessage());
                    } else if (sb.length() > 0) {
                        chatMemory.add(AiMessage.from(sb.toString()));
                    }
                    sink.complete();
                }

                @Override
                public void onError(Throwable error) {
                    sink.error(error);
                }
            });
        });
    }

    private List<ChatMessage> buildMessagesWithRag(String userQuery) {
        ChatMemory chatMemory = currentUserMemory();
        List<ChatMessage> history = chatMemory.messages();
        List<ChatMessage> list = new ArrayList<>();
        list.add(SystemMessage.from(systemPrompt));
        list.addAll(history);
        if (contentRetriever != null && userQuery != null && !userQuery.isBlank()) {
            try {
                List<Content> contents = contentRetriever.retrieve(Query.from(userQuery));
                if (contents != null && !contents.isEmpty()) {
                    String refs = contents.stream()
                            .map(c -> {
                                try {
                                    if (c instanceof TextSegment ts) return ts.text();
                                } catch (Exception ignored) {}
                                return c.toString();
                            })
                            .filter(t -> t != null && !t.isBlank())
                            .map(t -> t.length() > 800 ? t.substring(0, 800) + "..." : t)
                            .limit(3)
                            .reduce((a,b) -> a + "\n---\n" + b)
                            .orElse("");
                    if (!refs.isBlank()) {
                        list.add(SystemMessage.from("以下是检索到的参考资料：\n" + refs + "\n请优先基于这些资料回答，若资料不足请说明不足之处。"));
                    }
                }
            } catch (Exception ignored) {}
        }
        return list;
    }

    private ChatMemory currentUserMemory() {
        UserDTO user = UserHolder.getUser();
        if (user == null) throw new IllegalStateException("未登录");
        return chatMemoryProvider.get(user.getId());
    }

    // 原始（无 RAG）构造
    private List<ChatMessage> buildMessagesPlain(){
        ChatMemory chatMemory = currentUserMemory();
        List<ChatMessage> history = chatMemory.messages();
        List<ChatMessage> list = new ArrayList<>();
        list.add(SystemMessage.from(systemPrompt));
        list.addAll(history);
        return list;
    }

    

        // * 完整的功能是：用户向agent查询当前时间段到未来1天时间内存在秒杀优惠卷活动的商店，agent返回商店信息。
    @Tool(
            name = "chatWithTool",
            value  = {"用户向小助手t查询当前时间段到未来1天时间内存在的所有秒杀优惠卷活动信息。",
            "若用户提供了商店名称，则只返回该商店的所有秒杀活动信息。",
            "若用户没有提供商店名称，参数shop传null，返回所有商店的秒杀活动信息。",}
    )
    private Flux<String> queryForSeckill(@P("商店名称，可以为空") String shopName) {
        UserDTO user = UserHolder.getUser();
        if (user == null) return Flux.error(new RuntimeException("未登录，无法查询秒杀活动"));
        String filter = (shopName == null || shopName.isBlank()) ? null : shopName.trim();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime end = now.plusDays(1);
        // 1. 查询时间窗口内的秒杀券（库存>0, 时间交集）
        List<SeckillVoucher> seckillList = seckillVoucherMapper.selectList(new LambdaQueryWrapper<SeckillVoucher>()
                .gt(SeckillVoucher::getEndTime, now)
                .lt(SeckillVoucher::getBeginTime, end)
                .gt(SeckillVoucher::getStock, 0));
        if (seckillList.isEmpty()) {
            return Flux.just(filter == null ? "未来24小时内没有正在进行或即将开始的秒杀活动。" : "未来24小时内店铺【" + filter + "】没有正在进行或即将开始的秒杀活动。");
        }
        // 2. 关联 voucher
        // tb_seckill_voucher 的字段：voucherId、stock、beginTime、endTime
        // seckill_voucher存了两次，在voucher表中也存了，并且有shopid
        Set<Long> voucherIds = seckillList.stream().map(SeckillVoucher::getVoucherId).collect(Collectors.toSet());
        if (voucherIds.isEmpty()) {
            return Flux.just("暂无数据");
        }
        List<Voucher> vouchers = voucherMapper.selectBatchIds(voucherIds);
        Map<Long, Voucher> voucherMap = vouchers.stream().collect(Collectors.toMap(Voucher::getId, v -> v));
        // 3. 店铺过滤
        Set<Long> shopIdFilter = null;
        if (filter != null) {
            List<Shop> matchedShops = shopMapper.selectList(new LambdaQueryWrapper<Shop>().like(Shop::getName, filter));
            shopIdFilter = matchedShops.stream().map(Shop::getId).collect(Collectors.toSet());
            if (shopIdFilter.isEmpty()) {
                return Flux.just("未来24小时内店铺【" + filter + "】没有正在进行或即将开始的秒杀活动。");
            }
        }
        // 4. 批量获取涉及的店铺
        Set<Long> shopIdsAll = vouchers.stream().map(Voucher::getShopId).collect(Collectors.toSet());
        List<Shop> shops = shopMapper.selectBatchIds(shopIdsAll);
        Map<Long, Shop> shopMap = shops.stream().collect(Collectors.toMap(Shop::getId, s -> s));
        // 5. 组装 DTO
        List<SeckillVoucherDTO> dtoList = new ArrayList<>();
        for (SeckillVoucher sv : seckillList) {
            Voucher v = voucherMap.get(sv.getVoucherId());
            if (v == null) continue;
            if (shopIdFilter != null && !shopIdFilter.contains(v.getShopId())) continue;
            Shop shop = shopMap.get(v.getShopId());
            if (shop == null) continue;
            SeckillVoucherDTO dto = new SeckillVoucherDTO();
            dto.setVoucherId(v.getId());
            dto.setShopId(v.getShopId());
            dto.setShopName(shop.getName());
            dto.setTitle(v.getTitle());
            dto.setStock(sv.getStock());
            dto.setBeginTime(sv.getBeginTime());
            dto.setEndTime(sv.getEndTime());
            dtoList.add(dto);
        }
        if (dtoList.isEmpty()) {
            return Flux.just(filter == null ? "未来24小时内没有正在进行或即将开始的秒杀活动。" : "未来24小时内店铺【" + filter + "】没有正在进行或即将开始的秒杀活动。");
        }
        dtoList.sort(Comparator.comparing(SeckillVoucherDTO::getBeginTime));
        List<String> lines = new ArrayList<>();
        lines.add("查询时间窗口：" + now + " ~ " + end + (filter != null ? "，店铺包含：" + filter : ""));
        for (SeckillVoucherDTO dto : dtoList) {
            String status = dto.getBeginTime().isAfter(now) ? "即将开始" : (dto.getEndTime().isBefore(now) ? "已结束" : "进行中");
            lines.add(String.format("[%s] 店铺:%s | 活动:%s | 时间:%s ~ %s | 库存:%d", status, dto.getShopName(), dto.getTitle(), dto.getBeginTime(), dto.getEndTime(), dto.getStock()));
        }
        return Flux.fromIterable(lines);
    }

    /**
     * 简单协议版本：
     * 第一次让模型判断是否需要查询，若返回 JSON {"action":"seckill_query","shop":"xx"} 则执行查询并二次让模型生成最终回答。
     */
    public String chatWithProtocol(String userInput) {
        if (userInput == null || userInput.isBlank()) return "";
        UserDTO user = UserHolder.getUser();
        if (user == null) return "请先登录后再使用智能顾问功能";
        // 1. 分类探测：使用“干净”上下文，避免历史中出现的秒杀关键词放大触发概率
        List<ChatMessage> classify = new ArrayList<>();
        classify.add(SystemMessage.from("你是一个严格的分类器，只回答是否需要执行‘未来24小时内秒杀优惠券活动查询’。\n" +
            "判断标准：用户是否明确想要知道现在或未来24小时(或今天/今晚/明天)有哪些‘秒杀’/‘秒杀券’/‘秒杀优惠券’/‘抢购’/‘限时优惠券’/‘限时秒杀’。\n" +
            "如果需要查询：输出严格 JSON：{\"action\":\"seckill_query\",\"shop\":\"(店铺名或空)\"}\n" +
            "店铺名规则：若用户明确提到某个店铺名称(模糊也可)则填该名称原文；否则填空字符串。\n" +
            "如果不需要查询：只输出 NO_ACTION。\n" +
            "不要输出除以上两种格式外的任何字符。不要解释。\n" +
            "示例1 用户: 今天有哪些秒杀券? 输出: {\"action\":\"seckill_query\",\"shop\":\"\"}\n" +
            "示例2 用户: 帮我查下海底捞明天有没有秒杀活动 输出: {\"action\":\"seckill_query\",\"shop\":\"海底捞\"}\n" +
            "示例3 用户: 你好 输出: NO_ACTION\n" +
            "示例4 用户: 给我解释一下Java内存模型 输出: NO_ACTION"));
        classify.add(UserMessage.from(userInput));
        ChatResponse probe = chatModel.chat(classify);
        String probeText = probe.aiMessage() != null ? probe.aiMessage().text() : "";
        QueryAction qa = parseAction(probeText);
        if (qa == null) {
            // 未触发查询 => 普通聊天
            return chat(userInput);
        }
        // 二次关键词校验（保险）：避免模型误判
        if (!userInput.matches(".*(秒杀|秒杀券|抢购|限时|优惠券).*")) {
            return chat(userInput);
        }
        // 工具查询路径
        ChatMemory memory = currentUserMemory();
        // 现在再把用户输入写入 memory
        memory.add(UserMessage.from(userInput));
        List<String> resultLines = queryForSeckill(qa.shop).collectList().block();
        String resultText = (resultLines == null || resultLines.isEmpty()) ? "无相关秒杀活动。" : String.join("\n", resultLines);
        List<ChatMessage> follow = (ragEnabled ? buildMessagesWithRag(userInput) : buildMessagesPlain());
        follow.add(UserMessage.from(userInput));
        follow.add(SystemMessage.from("秒杀活动查询结果如下：\n" + resultText));
        follow.add(UserMessage.from("请基于上述查询结果，简洁回答用户原始问题。"));
        ChatResponse finalResp = chatModel.chat(follow);
        if (finalResp.aiMessage() != null) memory.add(finalResp.aiMessage());
        return finalResp.aiMessage() != null ? finalResp.aiMessage().text() : "";
    }

    private static class QueryAction { String shop; }

    private QueryAction parseAction(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        if ("NO_ACTION".equalsIgnoreCase(trimmed)) return null; // 明确不查询
        // 必须是一个以 { 开头的简短 JSON
        if (!trimmed.startsWith("{")) return null;
        // 限制长度，防止模型输出其它解释被错误截取
        if (trimmed.length() > 200) return null;
        try {
            ObjectMapper om = new ObjectMapper();
            JsonNode node = om.readTree(trimmed);
            // 只允许 action / shop 两个字段（容错：额外字段则拒绝）
            Iterator<String> it = node.fieldNames();
            int count = 0;
            while (it.hasNext()) {
                count++; String fn = it.next();
                if (!"action".equals(fn) && !"shop".equals(fn)) return null;
                if (count > 2) return null; // 不超过两个字段
            }
            String action = node.path("action").asText(null);
            if (!"seckill_query".equalsIgnoreCase(action)) return null;
            QueryAction qa = new QueryAction();
            String shop = node.has("shop") && !node.get("shop").isNull() ? node.get("shop").asText() : null;
            qa.shop = (shop == null || shop.isBlank()) ? null : shop.trim();
            return qa;
        } catch (Exception e) {
            return null;
        }
    }
}
