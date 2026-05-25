package org.javaup.config;

import org.javaup.lease.RedisLeaseManager;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;

/**

 * @description: 自动配置类

 **/

public class ServiceLeaseAutoConfiguration {

    @Bean
    public RedisLeaseManager redisLeaseManager(RedissonClient redissonClient) {
        return new RedisLeaseManager(redissonClient);
    }
}
