package com.example.hot6novelcraft.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.ReadFrom;
import io.lettuce.core.internal.HostAndPort;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.DnsResolvers;
import io.lettuce.core.resource.MappingSocketAddressResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.Set;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.sentinel.master}")
    private String masterName;

    @Value("${spring.data.redis.sentinel.nodes}")
    private Set<String> sentinelNodes;

    // 값 없으면 빈 문자열 주입
    @Value("${app.redis.nat-mapping-ip:}")
    private String natMappingIp;

    // Lettuce를 위한 '번역기' 생성 (Redisson의 NatMapper와 동일한 역할)
    @Bean(destroyMethod = "shutdown")
    public ClientResources clientResources() {

        // natMappingIp가 비어있으면 기본 설정 사용 (배포 환경)
        if (natMappingIp == null || natMappingIp.isBlank()) {
            return DefaultClientResources.create();
        }

        // 로컬 환경 - natMappingId (127.0.0.1)로 포워딩
        MappingSocketAddressResolver resolver = MappingSocketAddressResolver.create(
                DnsResolvers.JVM_DEFAULT,
                hostAndPort -> {
                    // 도커 내부 IP(172.x.x.x)를 무시하고 무조건 localhost로 포워딩
                    return HostAndPort.of("127.0.0.1", hostAndPort.getPort());
                }
        );

        return DefaultClientResources.builder()
                .socketAddressResolver(resolver)
                .build();
    }

    /**
     * Redis 다운 시 마스터-슬레이브 및 센티넬 발동
     * 읽기는 슬레이브에서 -> 마스터 부하 분산
     * 슬레이브 장애 시엔 마스터에서 읽음 -> 자동 fallback
     * Failover 새 마스터 선출 시 센티넬이 알려준 새 마스터로 자동 재연결
     * ClientResources clientResources 삭제하기
     **/
    @Bean
    public RedisConnectionFactory redisConnectionFactory(ClientResources clientResources) {
        // 127.0.0.1 하드코딩 제거 → 환경변수(SPRING_DATA_REDIS_SENTINEL_NODES)에서 읽음
        RedisSentinelConfiguration sentinelConfig = getSentinelConfig();

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .clientResources(clientResources) // Lettuce 설정에 번역기 장착!
                .readFrom(ReadFrom.REPLICA_PREFERRED)
                .build();

        // 팩토리 생성 후 초기화 (안전성 위해 추가)
        LettuceConnectionFactory factory = new LettuceConnectionFactory(sentinelConfig, clientConfig);
        factory.afterPropertiesSet();
        return factory;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = redisObjectMapper();

        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // key-value 관리 설정 1 : Redis 에서 받아올 때, 문자열 Json타입으로 파싱
        template.setKeySerializer(new StringRedisSerializer());
        //template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setValueSerializer(serializer);


        // key-value 관리 설정 2 : DTO 객체는 Hash Type 으로 받아올 수 있게 파싱
        template.setHashKeySerializer(new StringRedisSerializer());
        //template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

    private ObjectMapper redisObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }

    // 공통 sentinel configuration 생성 메서드
    private RedisSentinelConfiguration getSentinelConfig() {
        return new RedisSentinelConfiguration(masterName, sentinelNodes);
    }
}
