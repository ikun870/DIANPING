package com.hmdp.service;

import java.util.function.Consumer;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import reactor.core.publisher.Flux;

public interface ConsultantService {
    // 同步完整响应
    String chat(String message);

    // 基于 Reactor 的流式输出（默认未实现）
    Flux<String> chatStreamFlux(String message);


    String chatWithProtocol(String message);
}
