package com.example.hot6novelcraft.common.config;

import com.example.hot6novelcraft.domain.coverai.dto.event.CoverGenerationEvent;
import com.example.hot6novelcraft.domain.notification.dto.event.NotificationEvent;
import com.example.hot6novelcraft.domain.reviewai.dto.event.AiReviewMessage;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.RoundRobinPartitioner;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${notification.kafka.topic}")
    private String notificationTopic;

    @Value("${cover.kafka.topic}")
    private String coverTopic;

    @Value("${ai-review.kafka.topic}")
    private String aiReviewTopic;

    @Bean
    public NewTopic notificationTopic() {
        return TopicBuilder.name(notificationTopic)
                .partitions(3)
                .replicas(3)
                .build();
    }

    @Bean
    public ProducerFactory<String, NotificationEvent> notificationProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, RoundRobinPartitioner.class.getName());
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, NotificationEvent> notificationKafkaTemplate() {
        return new KafkaTemplate<>(notificationProducerFactory());
    }

    @Bean
    public ConsumerFactory<String, NotificationEvent> notificationConsumerFactory() {
        JsonDeserializer<NotificationEvent> deserializer = new JsonDeserializer<>(NotificationEvent.class);
        deserializer.addTrustedPackages("com.example.hot6novelcraft");
        deserializer.setUseTypeHeaders(false);

        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NotificationEvent> notificationKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, NotificationEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(notificationConsumerFactory());
        factory.setConcurrency(3); // 파티션 수와 동일하게 설정 → 컨슈머 1개당 파티션 1개 담당
        return factory;
    }

    // Cover Producer
    @Bean
    public ProducerFactory<String, CoverGenerationEvent> coverProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, RoundRobinPartitioner.class.getName());
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, CoverGenerationEvent> coverKafkaTemplate() {
        return new KafkaTemplate<>(coverProducerFactory());
    }

    // Cover Consumer
    @Bean
    public ConsumerFactory<String, CoverGenerationEvent> coverConsumerFactory() {
        JsonDeserializer<CoverGenerationEvent> deserializer = new JsonDeserializer<>(CoverGenerationEvent.class);
        deserializer.addTrustedPackages("com.example.hot6novelcraft");
        deserializer.setUseTypeHeaders(false);
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "cover-generation-service");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CoverGenerationEvent> coverKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, CoverGenerationEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(coverConsumerFactory());
        factory.setConcurrency(3);
        return factory;
    }

    // Cover 토픽
    @Bean
    public NewTopic coverTopic() {
        return TopicBuilder.name(coverTopic)
                .partitions(3)
                .replicas(1) // 로컬 테스트용 1로 설정
                .build();
    }

    // AI Review Producer
    @Bean
    public ProducerFactory<String, AiReviewMessage> aiReviewProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, RoundRobinPartitioner.class.getName());
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, AiReviewMessage> aiReviewKafkaTemplate() {
        return new KafkaTemplate<>(aiReviewProducerFactory());
    }

    // AI Review Consumer
    @Bean
    public ConsumerFactory<String, AiReviewMessage> aiReviewConsumerFactory() {
        JsonDeserializer<AiReviewMessage> deserializer = new JsonDeserializer<>(AiReviewMessage.class);
        deserializer.addTrustedPackages("com.example.hot6novelcraft");
        deserializer.setUseTypeHeaders(false);

        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "ai-review-service");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AiReviewMessage> aiReviewKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, AiReviewMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(aiReviewConsumerFactory());
        factory.setConcurrency(3);
        return factory;
    }

    // AI Review 토픽
    @Bean
    public NewTopic aiReviewTopic() {
        return TopicBuilder.name(aiReviewTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
