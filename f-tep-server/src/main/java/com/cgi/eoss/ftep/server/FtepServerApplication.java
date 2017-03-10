package com.cgi.eoss.ftep.server;

import com.cgi.eoss.ftep.api.ApiConfig;
import com.cgi.eoss.ftep.orchestrator.OrchestratorConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;

/**
 * <p>Application running the F-TEP orchestrator and associated "master" services.</p>
 */
@Import({
        ApiConfig.class,
        OrchestratorConfig.class
})
@SpringBootApplication(scanBasePackageClasses = FtepServerApplication.class)
@PropertySource(value = "file:/var/f-tep/etc/f-tep-server.properties", ignoreResourceNotFound = true)
@PropertySource(value = "file:${user.home}/.config/f-tep/f-tep-server.properties", ignoreResourceNotFound = true)
public class FtepServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FtepServerApplication.class, args);
    }

}
