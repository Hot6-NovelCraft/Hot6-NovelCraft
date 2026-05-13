package com.example.hot6novelcraft.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class RedissonConfig {

    /** "standalone" 이면 로컬 단독 Redis, 그 외(기본값)는 Sentinel 모드 */
    @Value("${redisson.mode:sentinel}")
    private String redissonMode;

    // standalone 용
    @Value("${spring.data.redis.host:localhost}")
    private String standaloneHost;

    @Value("${spring.data.redis.port:6379}")
    private int standalonePort;

    // sentinel 용
    @Value("${spring.data.redis.sentinel.master:mymaster}")
    private String masterName;

    @Value("${spring.data.redis.sentinel.nodes:}")
    private List<String> sentinelNodes;

    @Value("${app.redis.nat-mapping-ip:}")
    private String natMappingIp;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();

        if ("standalone".equalsIgnoreCase(redissonMode)) {
            // 로컬 개발 환경: 단독 Redis
            config.useSingleServer()
                  .setAddress("redis://" + standaloneHost + ":" + standalonePort)
                  .setConnectTimeout(1000)
                  .setRetryAttempts(3)
                  .setRetryInterval(1500);
        } else {
            // 배포 환경: Redis Sentinel
            String[] nodesArray = sentinelNodes.stream()
                    .filter(n -> n != null && !n.isBlank())
                    .map(node -> "redis://" + node.trim())
                    .toArray(String[]::new);

            org.redisson.config.SentinelServersConfig sentinelConfig = config.useSentinelServers()
                    .setMasterName(masterName)
                    .addSentinelAddress(nodesArray)
                    .setCheckSentinelsList(false)
                    .setReadMode(org.redisson.config.ReadMode.SLAVE)
                    .setConnectTimeout(1000)
                    .setRetryAttempts(3)
                    .setRetryInterval(1500);

            if (natMappingIp != null && !natMappingIp.isBlank()) {
                sentinelConfig.setNatMapper(uri -> new org.redisson.misc.RedisURI(
                        uri.getScheme() + "://" + natMappingIp + ":" + uri.getPort()
                ));
            }
        }

        return Redisson.create(config);
    }
}