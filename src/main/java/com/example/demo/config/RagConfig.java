package com.example.demo.config;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15.BgeSmallZhV15EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * RAG 配置：进程内中文嵌入模型（BGE-small-zh）+ 向量存储（InMemory / Milvus 可切换）
 */
@Configuration
public class RagConfig {

    /**
     * 嵌入模型：BGE-small-zh v1.5（进程内 ONNX，无需外部服务，512 维，中文优化）
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return new BgeSmallZhV15EmbeddingModel();
    }

    /** 向量存储：默认内存实现，rag.store-type=milvus 时切换到 Milvus */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(
            @Value("${rag.store-type:in-memory}") String storeType,
            @Value("${milvus.host:localhost}") String milvusHost,
            @Value("${milvus.port:19530}") int milvusPort,
            @Value("${milvus.collection-name:demo_rag}") String collectionName,
            @Value("${milvus.dimension:768}") int dimension) {

        if ("milvus".equalsIgnoreCase(storeType)) {
            return MilvusEmbeddingStore.builder()
                    .host(milvusHost)
                    .port(milvusPort)
                    .collectionName(collectionName)
                    .dimension(dimension)
                    .build();
        }
        return new InMemoryEmbeddingStore<>();
    }

    /** 内容检索器：RAG 问答时从向量库召回最相关的片段 */
    @Bean
    public ContentRetriever contentRetriever(EmbeddingStore<TextSegment> embeddingStore,
                                             EmbeddingModel embeddingModel) {
        // 每次召回 4 条，相似度得分低于 0.75 的直接过滤
        return new EmbeddingStoreContentRetriever(embeddingStore, embeddingModel, 4, 0.75);
    }

    /** 文档摄取器：切分 → 向量化 → 入库 */
    @Bean
    public EmbeddingStoreIngestor embeddingStoreIngestor(EmbeddingStore<TextSegment> embeddingStore,
                                                         EmbeddingModel embeddingModel,
                                                         DocumentSplitter documentSplitter) {
        return EmbeddingStoreIngestor.builder()
                .documentSplitter(documentSplitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
    }

    /** 文档切分器：每块 300 token、相邻块重叠 50 token，保持上下文连续 */
    @Bean
    public DocumentSplitter documentSplitter() {
        return DocumentSplitters.recursive(300, 50, new BgeTokenCountEstimator());
    }

    /**
     * Token 计数器：复用 BGE 模型 jar 内置的 tokenizer.json，
     * 保证切分时的 token 统计与实际向量化输入口径一致
     */
    static final class BgeTokenCountEstimator implements TokenCountEstimator {

        private final HuggingFaceTokenizer tokenizer;

        BgeTokenCountEstimator() {
            try (InputStream is = getClass().getResourceAsStream("/bge-small-zh-v1.5-tokenizer.json")) {
                this.tokenizer = HuggingFaceTokenizer.newInstance(is, Map.of());
            } catch (IOException e) {
                throw new IllegalStateException("加载 BGE tokenizer 失败", e);
            }
        }

        @Override
        public int estimateTokenCountInText(String text) {
            return tokenizer.encode(text).getIds().length;
        }

        @Override
        public int estimateTokenCountInMessage(ChatMessage message) {
            if (message instanceof UserMessage um) {
                return estimateTokenCountInText(um.singleText());
            }
            if (message instanceof SystemMessage sm) {
                return estimateTokenCountInText(sm.text());
            }
            if (message instanceof AiMessage am) {
                return estimateTokenCountInText(am.text());
            }
            return estimateTokenCountInText(String.valueOf(message));
        }

        @Override
        public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
            int total = 0;
            for (ChatMessage message : messages) {
                total += estimateTokenCountInMessage(message);
            }
            return total;
        }
    }
}
