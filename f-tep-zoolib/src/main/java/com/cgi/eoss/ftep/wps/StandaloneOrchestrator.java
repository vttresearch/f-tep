package com.cgi.eoss.ftep.wps;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import io.grpc.BindableService;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

/**
 * <p>Standalone, in-process gRPC server implementation. May be used for isolation testing, or when a fully-distributed
 * environment is not necessary.</p>
 */
@Log4j2
public class StandaloneOrchestrator implements Closeable {
    private static final String DEFAULT_NAME = "f-tep-standalone-orchestrator";

    private static final Set<BindableService> SERVICES = Sets.newHashSet();

    @Getter
    private final Server server;
    @Getter
    private final ManagedChannelBuilder<?> channelBuilder;

    /**
     * <p>Instantiate a gRPC server with a globally static collection of services. The collection should be managed by
     * {@link #resetServices(Set)} before this constructor is called.</p>
     *
     * @param name The server name by which ChannelBuilders may connect.
     */
    public StandaloneOrchestrator(String name) throws IOException {
        this(name, SERVICES);
    }

    /**
     * <p>Instantiate a gRPC server with the given collection of services.</p>
     *
     * @param name The server name by which ChannelBuilders may connect.
     * @param services The services to be registered in the gRPC server.
     */
    public StandaloneOrchestrator(String name, Collection<BindableService> services) throws IOException {
        String realName = Strings.isNullOrEmpty(name) ? DEFAULT_NAME : name;

        LOG.info("Instantiating standalone gRPC server with name '{}'...", realName);
        InProcessServerBuilder inProcessServerBuilder = InProcessServerBuilder.forName(realName).directExecutor();
        services.stream().peek(svc -> LOG.info("Adding gRPC service {}", svc))
                .forEach(inProcessServerBuilder::addService);

        LOG.info("Starting standalone gRPC server!");
        this.server = inProcessServerBuilder.build().start();
        this.channelBuilder = InProcessChannelBuilder.forName(realName).directExecutor();
    }

    @PreDestroy
    @Override
    public void close() throws IOException {
        server.shutdownNow();
    }

    public static void resetServices(Set<BindableService> services) {
        SERVICES.clear();
        SERVICES.addAll(services);
    }

}
