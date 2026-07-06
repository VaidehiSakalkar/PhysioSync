package com.physiolink.patient.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis cache configuration using JSON serialization and a resilient error handler.
 *
 * <p>Fixes: {@code java.io.NotSerializableException} when caching DTOs (records) that
 * don't implement {@link java.io.Serializable}.</p>
 *
 * <p>The {@link ResilientCacheErrorHandler} swallows deserialization errors on stale
 * cache entries, evicts the corrupt key, and falls back to the real data source — so
 * the app never crashes due to incompatible Redis data formats.</p>
 */
@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    private ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }

    @Bean
    public RedisCacheConfiguration redisCacheConfiguration() {
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper());

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues();
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                          RedisCacheConfiguration redisCacheConfiguration) {
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(redisCacheConfiguration)
                .build();
    }

    /**
     * Returns a named (non-anonymous) error handler so Spring's CGLIB proxy can always
     * locate the class by name. Anonymous classes ($1, $2 …) cause
     * {@code ClassNotFoundException} when the proxy tries to load them at runtime.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new ResilientCacheErrorHandler();
    }

    /**
     * Cache error handler that silently evicts a corrupt/stale entry on a GET failure
     * and lets the method re-execute against the real data source.
     */
    static class ResilientCacheErrorHandler implements CacheErrorHandler {

        private static final Logger log = LoggerFactory.getLogger(ResilientCacheErrorHandler.class);

        @Override
        public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
            log.warn("Cache GET error on cache='{}' key='{}' — evicting and falling back to source: {}",
                    cache.getName(), key, ex.getMessage());
            try {
                cache.evict(key);
            } catch (RuntimeException evictEx) {
                log.warn("Failed to evict corrupt cache entry key='{}': {}", key, evictEx.getMessage());
            }
        }

        @Override
        public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
            log.warn("Cache PUT error on cache='{}' key='{}': {}", cache.getName(), key, ex.getMessage());
        }

        @Override
        public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
            log.warn("Cache EVICT error on cache='{}' key='{}': {}", cache.getName(), key, ex.getMessage());
        }

        @Override
        public void handleCacheClearError(RuntimeException ex, Cache cache) {
            log.warn("Cache CLEAR error on cache='{}': {}", cache.getName(), ex.getMessage());
        }
    }
}
