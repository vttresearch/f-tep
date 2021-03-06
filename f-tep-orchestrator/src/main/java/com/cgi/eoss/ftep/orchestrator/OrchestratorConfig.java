package com.cgi.eoss.ftep.orchestrator;

import com.cgi.eoss.ftep.catalogue.CatalogueConfig;
import com.cgi.eoss.ftep.costing.CostingConfig;
import com.cgi.eoss.ftep.orchestrator.service.WorkerFactory;
import com.cgi.eoss.ftep.persistence.PersistenceConfig;
import com.cgi.eoss.ftep.persistence.service.WorkerLocatorExpressionDataService;
import com.cgi.eoss.ftep.queues.QueuesConfig;
import com.cgi.eoss.ftep.rpc.InProcessRpcConfig;
import com.cgi.eoss.ftep.search.SearchConfig;
import com.cgi.eoss.ftep.security.SecurityConfig;
import com.google.common.util.concurrent.Striped;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.locks.Lock;

/**
 * <p>Spring configuration for the F-TEP Orchestrator component.</p>
 * <p>Manages access to distributed workers and provides RPC services.</p>
 */
@Configuration
@Import({
        PropertyPlaceholderAutoConfiguration.class,

        CatalogueConfig.class,
        CostingConfig.class,
        InProcessRpcConfig.class,
        PersistenceConfig.class,
        QueuesConfig.class,
        SearchConfig.class,
        SecurityConfig.class
})
@EnableEurekaClient
@ComponentScan(basePackageClasses = OrchestratorConfig.class)
@EnableScheduling
@EnableAsync
public class OrchestratorConfig {

    // Executor for handling asynchronously calls to FtepOrchestratorService.processContainerExit()
    @Bean(name = "exitExecutor")
    public TaskExecutor taskExecutor() {
	ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
	executor.setCorePoolSize(4);
	return executor;
    }

    @Bean
    public ExpressionParser workerLocatorExpressionParser() {
        return new SpelExpressionParser();
    }

    @Bean
    public WorkerFactory workerFactory(DiscoveryClient discoveryClient,
                                       @Value("${ftep.orchestrator.worker.eurekaServiceId:f-tep worker}") String workerServiceId,
                                       ExpressionParser workerLocatorExpressionParser,
                                       WorkerLocatorExpressionDataService workerLocatorExpressionDataService,
                                       @Value("${ftep.orchestrator.worker.defaultWorkerExpression:\"LOCAL\"}") String defaultWorkerExpression) {
        return new WorkerFactory(discoveryClient, workerServiceId, workerLocatorExpressionParser, workerLocatorExpressionDataService, defaultWorkerExpression);
    }

    @Bean
    public Striped<Lock> imageBuildUpdateLocks() {
        return Striped.lazyWeakLock(1);
    }

    @Bean
    public Striped<Lock> jobUpdateLocks() {
        return Striped.lazyWeakLock(1);
    }

}
