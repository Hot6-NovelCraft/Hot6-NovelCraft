package com.example.hot6novelcraft.domain.aichat.loader;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
@DependsOn("vectorStore") // VectorStore 빈이 먼저 생성된 후 실행 보장
public class KnowledgeBaseLoader {

    private final VectorStore vectorStore;
    private final ResourceLoader resourceLoader;

    private static final String FAQ_SOURCE_TYPE = "faq";

    private static final List<String> FAQ_FILES = Arrays.asList(
            "classpath:ai/knowledge/faq-payment.md",
            "classpath:ai/knowledge/faq-subscription.md",
            "classpath:ai/knowledge/faq-points.md",
            "classpath:ai/knowledge/faq-mentoring.md",
            "classpath:ai/knowledge/faq-general.md"
    );

    /**
     * 앱 시작 시 FAQ 문서를 VectorStore에 적재한다.
     * PgVectorStore는 영속 저장이므로 데이터가 이미 존재하면 재적재를 건너뛴다.
     */
    @PostConstruct
    public void load() {
        // source_type == 'faq' 메타데이터로 정확히 필터링 → 유사도 검색 오탐 방지
        List<Document> existing = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("FAQ")
                        .topK(1)
                        .filterExpression("source_type == '" + FAQ_SOURCE_TYPE + "'")
                        .build()
        );
        if (!existing.isEmpty()) {
            log.info("FAQ 문서가 이미 VectorStore에 존재합니다. 적재를 건너뜁니다.");
            return;
        }

        log.info("FAQ 문서 VectorStore 적재 시작...");

        List<Document> allDocuments = FAQ_FILES.stream()
                .flatMap(this::readDocument)
                .toList();

        List<Document> chunks = new TokenTextSplitter().apply(allDocuments);

        vectorStore.add(chunks);

        log.info("FAQ 문서 적재 완료: {}개 파일 → {}개 청크", FAQ_FILES.size(), chunks.size());
    }

    private Stream<Document> readDocument(String path) {
        try {
            Resource resource = resourceLoader.getResource(path);
            if (!resource.exists()) {
                log.warn("FAQ 파일을 찾을 수 없습니다: {}", path);
                return Stream.empty();
            }
            // 각 청크에 source_type 메타데이터 태깅 → 이후 필터 검색에 사용
            return new TextReader(resource).get().stream()
                    .peek(doc -> doc.getMetadata().put("source_type", FAQ_SOURCE_TYPE));
        } catch (Exception e) {
            log.error("FAQ 파일 읽기 실패: {} - {}", path, e.getMessage());
            return Stream.empty();
        }
    }
}
