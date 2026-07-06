package com.physiolink.patient.config;

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
 * Redis cache configuration that uses JSON serialization instead of Java's default
 * JDK serialization. This allows caching of DTOs (records, etc.) without requiring
 * them to implement {@link java.io.Serializable}.
 *
 * <p>Fixes: {@code java.io.NotSerializableException: com.physiolink.patient.dto.UserResponse}
 * thrown when Spring's Redis cache attempted to serialize {@code UserResponse} and
 * {@code PatientProfileResponse} (both Java records) using JDK serialization.</p>
 */
@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * Builds a Jackson {@link ObjectMapper} suitable for Redis value serialization.
     * <ul>
     *   <li>Registers {@link JavaTimeModule} so {@code LocalDate} / {@code LocalDateTime}
     *       fields serialize correctly.</li>
     *   <li>Disables {@link SerializationFeature#WRITE_DATES_AS_TIMESTAMPS} so dates are
     *       written as ISO-8601 strings rather than numeric arrays.</li>
     *   <li>Enables default typing so that the concrete type is embedded in the JSON
     *       payload; this allows Spring to deserialize the cached value back to the
     *       correct class without needing explicit type hints at every call site.</li>
     * </ul>
     */
    private ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Embed type metadata so Spring can deserialize to the correct concrete type.
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }

    /**
     * Configures the default {@link RedisCacheConfiguration}:
     * <ul>
     *   <li>Keys are serialized as plain UTF-8 strings.</li>
     *   <li>Values are serialized as JSON via {@link GenericJackson2JsonRedisSerializer}.</li>
     *   <li>Default TTL is 30 minutes; individual caches can override this.</li>
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
     * Registers a {@link RedisCacheManager} that applies the JSON-based cache
     * configuration to all caches ({@code userProfiles}, {@code patientProfiles},
     * {@code patientsByPhysio}, {@code allPhysios}).
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                          RedisCacheConfiguration redisCacheConfiguration) {
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(redisCacheConfiguration)
                .build();
    }
}
