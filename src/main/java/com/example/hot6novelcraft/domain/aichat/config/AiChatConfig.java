package com.example.hot6novelcraft.domain.aichat.config;

import com.example.hot6novelcraft.domain.aichat.memory.RedisChatMemoryRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class AiChatConfig {

    // ─── PGVector 전용 PostgreSQL 접속 정보 ───────────────────────────────────
    // spring.datasource(MySQL, JPA용)와 완전히 분리된 별도 datasource
    @Value("${pgvector.datasource.url}")
    private String pgVectorUrl;

    @Value("${pgvector.datasource.username}")
    private String pgVectorUsername;

    @Value("${pgvector.datasource.password}")
    private String pgVectorPassword;

    /**
     * Redis 기반 대화 메모리 빈 (4단계: InMemory → Redis 교체)
     *
     * - 2단계의 InMemoryChatMemoryRepository는 서버 재시작 시 모든 대화 소멸
     * - RedisChatMemoryRepository를 주입하면 재시작 후에도 대화 히스토리 유지
     * - 다중 서버 인스턴스 환경에서도 동일한 사용자 세션을 공유
     * - TTL(7일)로 오래된 대화 자동 삭제 → 비용 관리
     */
    @Bean
    public ChatMemory chatMemory(RedisChatMemoryRepository redisChatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(redisChatMemoryRepository) // Redis 기반으로 교체
                .maxMessages(10) // 최근 10턴만 LLM에 전달 → 토큰 비용 고정
                .build();
    }

    /**
     * PGVector 기반 벡터 스토어 빈 (4단계: SimpleVectorStore → PgVectorStore 교체)
     *
     * - 3단계의 SimpleVectorStore는 인메모리이므로 재시작 시 FAQ 데이터 소멸 → KnowledgeBaseLoader가 매번 재적재
     * - PgVectorStore는 PostgreSQL에 영속 저장 → 재시작 후에도 FAQ 벡터 유지 (KnowledgeBaseLoader는 최초 1회만 적재)
     * - HNSW 인덱스: 대규모 벡터 유사도 검색에 최적화된 그래프 기반 인덱스
     * - COSINE_DISTANCE: 텍스트 임베딩 유사도 측정에 가장 적합한 거리 함수
     * - dimensions(1536): OpenAI text-embedding-ada-002 출력 차원 수
     * - initializeSchema(true): 앱 시작 시 vector_store 테이블 + pgvector 익스텐션 자동 생성
     *
     * [사전 요구사항]
     * PostgreSQL에서 pgvector 익스텐션 설치 필요:
     *   CREATE EXTENSION IF NOT EXISTS vector;
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        // MySQL JPA와 분리된 PGVector 전용 datasource
        DataSource pgDataSource = DataSourceBuilder.create()
                .url(pgVectorUrl)
                .username(pgVectorUsername)
                .password(pgVectorPassword)
                .driverClassName("org.postgresql.Driver")
                .build();

        JdbcTemplate pgJdbcTemplate = new JdbcTemplate(pgDataSource);

        return PgVectorStore.builder(pgJdbcTemplate, embeddingModel)
                .dimensions(1536)                                               // OpenAI ada-002 차원
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)    // 텍스트 유사도에 최적
                .indexType(PgVectorStore.PgIndexType.HNSW)                     // 빠른 근사 최근접 검색
                .initializeSchema(true)                                         // 테이블/인덱스 자동 생성
                .build();
    }

    @Bean
    public ChatClient customerServiceChatClient(ChatModel chatModel, ChatMemory chatMemory, VectorStore vectorStore) {
        return ChatClient.builder(chatModel)
                .defaultOptions(OpenAiChatOptions.builder()
                        .model("gpt-4.1-nano")
                        .build())
                .defaultAdvisors(
                        new QuestionAnswerAdvisor(vectorStore), // RAG: 유사 FAQ 검색 → 컨텍스트 주입
                        new SimpleLoggerAdvisor()               // 디버깅용 로그
                )
                .defaultSystem("""
                        너는 NovelCraft 고객센터 AI다.
                        다음 규칙을 반드시 지켜라:
                        1. 반드시 제공된 문서 기반으로만 답변한다.
                        2. 문서에 없으면 "담당자에게 문의해주세요"라고 말한다.
                        3. 답변은 친절하고 짧게 (3~5줄)
                        4. 불필요한 설명 금지
                        5. 한국어로만 답변
                        """)
                .build();
    }
}
