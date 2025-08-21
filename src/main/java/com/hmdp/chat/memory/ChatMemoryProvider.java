package com.hmdp.chat.memory;

import dev.langchain4j.memory.ChatMemory;

/**
 * 用户级 ChatMemory 提供者接口。
 */
public interface ChatMemoryProvider {
    ChatMemory get(Long userId);
}
