package com.aimv.application.chain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 链路执行器。生产环境使用后台线程池异步推进链路，HTTP 请求立即返回可轮询的 chain run；
 * 测试环境通过 aimv.chain-run.sync=true 切换为同步执行，保持请求-响应内完成整条链路。
 */
@Configuration
public class ChainRunExecutorConfiguration {

    @Bean(name = "chainRunTaskExecutor")
    public TaskExecutor chainRunTaskExecutor(@Value("${aimv.chain-run.sync:false}") boolean sync) {
        if (sync) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("chain-run-");
        executor.initialize();
        return executor;
    }
}
