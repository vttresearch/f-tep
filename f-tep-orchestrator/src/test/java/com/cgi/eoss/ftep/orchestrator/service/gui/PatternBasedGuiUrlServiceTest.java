package com.cgi.eoss.ftep.orchestrator.service.gui;

import com.cgi.eoss.ftep.orchestrator.OrchestratorConfig;
import com.cgi.eoss.ftep.orchestrator.OrchestratorTestConfig;
import com.cgi.eoss.ftep.orchestrator.service.WorkerFactory;
import com.cgi.eoss.ftep.rpc.Job;
import com.cgi.eoss.ftep.rpc.worker.Binding;
import com.cgi.eoss.ftep.rpc.worker.FtepWorkerGrpc;
import com.cgi.eoss.ftep.rpc.worker.PortBinding;
import com.cgi.eoss.ftep.rpc.worker.PortBindings;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {OrchestratorConfig.class, OrchestratorTestConfig.class})
@TestPropertySource("classpath:test-orchestrator.properties")
@Transactional
public class PatternBasedGuiUrlServiceTest {

    @Autowired
    private GuiUrlService guiUrlService;

    @Autowired
    private InProcessServerBuilder serverBuilder;

    @Autowired
    private ManagedChannelBuilder channelBuilder;

    @MockBean
    private WorkerFactory workerFactory;

    private FtepWorkerGrpc.FtepWorkerBlockingStub worker;

    private Server server;

    @Before
    public void setUp() throws IOException {
        serverBuilder.addService(new WorkerStub());
        server = serverBuilder.build().start();

        worker = FtepWorkerGrpc.newBlockingStub(channelBuilder.build());

        when(workerFactory.getWorkerById("LOCAL")).thenReturn(worker);
    }

    @After
    public void tearDown() {
        server.shutdownNow();
    }

    @Test
    public void getGuiUrl() throws Exception {
        String guiUrl = guiUrlService.buildGuiUrl("LOCAL", Job.getDefaultInstance(), "8080/tcp");
        assertThat(guiUrl, is("/gui/:12345/"));
    }

    private class WorkerStub extends FtepWorkerGrpc.FtepWorkerImplBase {
        @Override
        public void getPortBindings(Job request, StreamObserver<PortBindings> responseObserver) {
            PortBinding portBinding = PortBinding.newBuilder()
                    .setPortDef("8080/tcp")
                    .setBinding(Binding.newBuilder().setPort(12345).build())
                    .build();

            responseObserver.onNext(PortBindings.newBuilder().addBindings(portBinding).build());
            responseObserver.onCompleted();
        }
    }

}