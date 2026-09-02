package com.planmate.realtime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class RealtimeTaskSchedulerConfig {

    @Bean(name = "taskScheduler")
    public TaskScheduler applicationTaskScheduler() {
        return scheduler("planmate-scheduled-", 2);
    }

    @Bean(name = "realtimeTaskScheduler")
    public TaskScheduler realtimeTaskScheduler() {
        return scheduler("planmate-realtime-", 2);
    }

    private TaskScheduler scheduler(String threadNamePrefix, int poolSize) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix(threadNamePrefix);
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.initialize();
        return scheduler;
    }
}
