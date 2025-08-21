package com.hmdp.chat.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import com.hmdp.utils.RedisConstants;

import java.time.Duration;
import java.util.List;

/**
 * 基于 Redis List 的 ChatMemoryStore 实现，只持久化 User 与 AI 消息。
 * key: chat:mem:{memoryId}
 */
@Component
public class RedisChatMemoryStore implements ChatMemoryStore {

    @Autowired
    private StringRedisTemplate redis;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
                
        String k = RedisConstants.CHAT_MEMORY_KEY + memoryId;
        // 获取消息列表
        String json = redis.opsForValue().get(k);;
        List<ChatMessage> result = ChatMessageDeserializer.messagesFromJson(json);
        return result;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String k = RedisConstants.CHAT_MEMORY_KEY + memoryId;
       
        if(messages != null){
            //直接使用chatMessageSerializer 序列化
            String s = ChatMessageSerializer.messagesToJson(messages);
            redis.opsForValue().set(k, s);
            
        }
        // 设置过期时间
        redis.expire(k, Duration.ofSeconds(RedisConstants.CHAT_MEMORY_TTL));
        
    }

    @Override
    public void deleteMessages(Object memoryId) {
        redis.delete(RedisConstants.CHAT_MEMORY_KEY + memoryId);
    }
}
