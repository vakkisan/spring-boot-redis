package com.example.springbootrediscache.service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Component;

@Component
public class RedisLockUtil {
    Logger logger = LoggerFactory.getLogger(this.getClass());
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    
    public boolean lock(String key, String value, long timeout, TimeUnit timeUnit) {
    // Lock, can also be used stringRedisTemplate.opsForValue().setIfAbsent(key, value, 15, TimeUnit.SECONDS);
    // Expiration.from(timeout, timeUnit) expiration time and unit
    // RedisStringCommands.SetOption.SET_IF_ABSENT, equivalent to NX, can only be used when the key does not exist
        Boolean lockStat = stringRedisTemplate.execute((RedisCallback<Boolean>) connection ->
                connection.set(key.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8),
                        Expiration.from(timeout, timeUnit), RedisStringCommands.SetOption.SET_IF_ABSENT));
        return lockStat != null && lockStat;
    }

    public boolean unlock(String key, String value) {
        try {
            // To release the lock, use Lua script to verify whether the key--value passed in is the same as that in Redis
            String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
            Boolean unLockStat = stringRedisTemplate.execute((RedisCallback<Boolean>) connection ->
                    connection.eval(script.getBytes(), ReturnType.BOOLEAN, 1,
                            key.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8)));
            return unLockStat == null || !unLockStat;
        } catch (Exception e) {
            logger.error("Unlock failed key = {}", key);
            return false;
        }
    }
}
