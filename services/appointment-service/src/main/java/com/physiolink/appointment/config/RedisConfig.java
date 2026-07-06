package com.physiolink.appointment.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
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
 * Redis cache configuration for appointment-service.
 *
 * <p>Uses {@link GenericJackson2JsonRedisSerializer} (JSON) instead of the default
 * JDK serializer so that cached objects ({@link com.physiolink.appointment.entity.Appointment}
 * and any future DTOs) do not need to implement {@link java.io.Serializable}, and so that
 * cached values are human-readable in Redis.</p>
 *
 * <p>Caches managed: {@code appointments}, {@code appointmentsByPatient},
 * {@code appointmentsByPhysio}.</p>
 */
@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * Jackson {@link ObjectMapper} configured for Redis value serialization:
     * <ul>
     *   <li>{@link JavaTimeModule} – handles {@code OffsetDateTime} fields in
     *       {@link com.physiolink.appointment.entity.Appointment}.</li>
     *   <li>Dates serialized as ISO-8601 strings (not timestamp arrays).</li>
     *   <li>Default typing enabled so Spring can reconstruct the concrete type
     *       on cache reads without explicit type hints.</li>
     * </ul>
     */
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

    /**
     * Default cache configuration:
     * <ul>
     *   <li>Keys – plain UTF-8 strings.</li>
     *   <li>Values – JSON via {@link GenericJackson2JsonRedisSerializer}.</li>
     *   <li>TTL – 30 minutes.</li>
     *   <li>Null values are not cached.</li>
     * </ul>
     */
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

    /**
     * Registers a {@link RedisCacheManager} using JSON serialization for all caches.
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                          RedisCacheConfiguration redisCacheConfiguration) {
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(redisCacheConfiguration)
                .build();
    }
}
