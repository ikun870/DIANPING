package com.hmdp.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import com.hmdp.chat.memory.ChatMemoryProvider;

import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.ClassPathDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;


@Configuration
public class ConsultantConfig {

    private static final Logger log = LoggerFactory.getLogger(ConsultantConfig.class);

    @Autowired
    private ChatMemoryStore redisChatMemoryStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private RedisEmbeddingStore redisEmbeddingStore;

    @Value("${openai.rag.enabled:true}")
    private boolean ragEnabled;

    @Bean
    public ChatMemoryProvider chatMemoryProvider(){
        ChatMemoryProvider provider = new ChatMemoryProvider() {
            @Override
            public ChatMemory get(Long userId) {
                // 是的，这里的 id 是 ChatMemory 的唯一标识（memoryId），通常一个用户对应一个 id。
                // 这里通过 "u-" + userId 保证每个用户的 ChatMemory 唯一。
                return MessageWindowChatMemory.builder()
                        .id("u-" + userId)
                        .maxMessages(20)
                        .chatMemoryStore(redisChatMemoryStore)
                        .build();
            }
  
        };
        return provider;
    }
    /**
     * 执行一次就好了，@Bean可以注解掉，节省资源
     * 创建一个 EmbeddingStore 实例，使用 InMemoryEmbeddingStore。
     * 这是一个存储向量的内存数据库，适用于小规模数据。
     */
    //@Bean
    public EmbeddingStore<TextSegment> store() {
        // 这里可以配置你的 EmbeddingStore 实现
        //加载文档
        List<Document> contents = ClassPathDocumentLoader.loadDocuments("rag_content");
        //内存版本的向量数据库
        //InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();

        //使用Redis版本的向量数据库
        //构建文档分割器对象
        DocumentSplitter splitter = DocumentSplitters.recursive(500, 100);
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingStore(redisEmbeddingStore)
                .embeddingModel(embeddingModel)
                .documentSplitter(splitter)
                .build();
        if (!ragEnabled) {
            log.info("RAG 已关闭 (openai.rag.enabled=false)，跳过文档向量化");
        } else {
            try {
                ingestor.ingest(contents);
                log.info("RAG 文档向量化完成, 文档数: {}", contents.size());
            } catch (Exception e) {
                // 捕获模型不可用/无权限等问题，避免整个应用启动失败
                log.warn("RAG 文档向量化失败，将跳过检索功能: {}", e.getMessage());
            }
        }
        return redisEmbeddingStore; // 即使失败也返回空的 store，检索时只会得到空结果
    }

    /*
     * 创建一个内容检索器,创建时需要注入EmbeddingStore
     */
    @Bean
    public ContentRetriever contentRetriever() {
        // 创建一个内容检索器
    //public ContentRetriever contentRetriever(EmbeddingStore<TextSegment> embeddingStore) {

        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(redisEmbeddingStore)
                .minScore(0.5)
                .maxResults(3)
                .embeddingModel(embeddingModel)
                .build();
    }
    

}
