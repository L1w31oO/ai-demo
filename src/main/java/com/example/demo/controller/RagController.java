package com.example.demo.controller;

import com.example.demo.ai.RagAssistantFactory;
import com.example.demo.service.ChatModelRegistry;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * RAG 知识库接口：文档入库 + 基于知识库的问答
 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final EmbeddingStoreIngestor ingestor;
    private final RagAssistantFactory assistantFactory;
    private final ChatModelRegistry modelRegistry;

    public RagController(EmbeddingStoreIngestor ingestor,
                         RagAssistantFactory assistantFactory,
                         ChatModelRegistry modelRegistry) {
        this.ingestor = ingestor;
        this.assistantFactory = assistantFactory;
        this.modelRegistry = modelRegistry;
    }

    // ---- 请求 / 响应 DTO ----

    public record IngestRequest(String text) {
    }

    public record RagChatRequest(String provider, String question) {
    }

    public record RagChatResponse(String provider, String question, String answer) {
    }

    /**
     * POST /api/rag/ingest — 将文本切分、向量化后写入知识库
     * 请求体：{"text": "任意长度的知识文本..."}
     */
    @PostMapping("/ingest")
    public Map<String, Object> ingest(@RequestBody IngestRequest request) {
        ingestor.ingest(Document.from(request.text()));
        return Map.of("status", "ingested", "chars", request.text().length());
    }

    /**
     * POST /api/rag/upload — 上传文件到知识库（Tika 解析，支持 txt/md/pdf/docx 等常见格式）
     * 用法：curl -F "file=@/path/to/file.pdf" http://localhost:8080/api/rag/upload
     */
    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) throws IOException {
        Document document = new ApacheTikaDocumentParser().parse(file.getInputStream());
        ingestor.ingest(document);
        return Map.of(
                "status", "ingested",
                "file", file.getOriginalFilename(),
                "chars", document.text().length());
    }

    /**
     * POST /api/rag/chat — 基于知识库的问答（自动检索相关片段注入上下文）
     * 请求体：{"provider": "ollama", "question": "问题"}
     */
    @PostMapping("/chat")
    public RagChatResponse chat(@RequestBody RagChatRequest request) {
        String provider = modelRegistry.resolve(request.provider());
        String answer = assistantFactory.get(provider).chat(request.question());
        return new RagChatResponse(provider, request.question(), answer);
    }

    // ---- 异常处理 ----

    /** provider 不可用等参数错误，返回 400 与可读信息 */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleIllegalArgument(IllegalArgumentException e) {
        return Map.of("error", e.getMessage());
    }
}
