package com.cgi.eoss.ftep.wps;

import com.cgi.eoss.ftep.catalogue.CatalogueService;
import com.cgi.eoss.ftep.costing.CostingService;
import com.cgi.eoss.ftep.model.FtepService;
import com.cgi.eoss.ftep.model.FtepServiceDescriptor;
import com.cgi.eoss.ftep.model.Job;
import com.cgi.eoss.ftep.model.JobConfig;
import com.cgi.eoss.ftep.model.User;
import com.cgi.eoss.ftep.model.Wallet;
import com.cgi.eoss.ftep.orchestrator.service.FtepGuiServiceManager;
import com.cgi.eoss.ftep.orchestrator.service.FtepServiceLauncher;
import com.cgi.eoss.ftep.orchestrator.service.WorkerEnvironment;
import com.cgi.eoss.ftep.orchestrator.service.WorkerFactory;
import com.cgi.eoss.ftep.persistence.service.JobDataService;
import com.cgi.eoss.ftep.rpc.worker.FtepWorkerGrpc;
import com.cgi.eoss.ftep.worker.docker.DockerClientFactory;
import com.cgi.eoss.ftep.worker.io.ServiceInputOutputManager;
import com.cgi.eoss.ftep.worker.worker.FtepWorker;
import com.cgi.eoss.ftep.worker.worker.JobEnvironmentService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.internal.ServerImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import shadow.dockerjava.com.github.dockerjava.api.DockerClient;
import shadow.dockerjava.com.github.dockerjava.core.DefaultDockerClientConfig;
import shadow.dockerjava.com.github.dockerjava.core.DockerClientBuilder;
import shadow.dockerjava.com.github.dockerjava.core.DockerClientConfig;
import shadow.dockerjava.com.github.dockerjava.core.RemoteApiVersion;
import shadow.dockerjava.com.github.dockerjava.core.command.PullImageResultCallback;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * <p>Integration test for launching WPS services.</p>
 * <p><strong>This uses a real Docker engine to build and run a container!</strong></p>
 */
public class FtepServicesClientIT {
    private static final String RPC_SERVER_NAME = FtepServicesClientIT.class.getName();
    private static final String TEST_CONTAINER_IMAGE = "hello-world:latest";

    @Mock
    private JobDataService jobDataService;

    @Mock
    private CatalogueService catalogueService;

    @Mock
    private CostingService costingService;

    private FileSystem fs;

    private FtepServicesClient ftepServicesClient;

    private ServerImpl server;

    @BeforeClass
    public static void precondition() {
        // Shortcut if docker socket is not accessible to the current user
        assumeTrue("Unable to write to Docker socket; disabling docker tests", Files.isWritable(Paths.get("/var/run/docker.sock")));
        // TODO Pass in a DOCKER_HOST env var to allow remote docker engine use
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        this.fs = Jimfs.newFileSystem(Configuration.unix());
        Files.createDirectories(fs.getPath("/tmp/ftep_data"));

        JobEnvironmentService jobEnvironmentService = spy(new JobEnvironmentService(fs.getPath("/tmp/ftep_data")));
        ServiceInputOutputManager ioManager = mock(ServiceInputOutputManager.class);

        DockerClientConfig dockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withApiVersion(RemoteApiVersion.VERSION_1_19)
                .withDockerHost("unix:///var/run/docker.sock")
                .build();
        DockerClient dockerClient = DockerClientBuilder.getInstance(dockerClientConfig).build();
        DockerClientFactory dockerClientFactory = new DockerClientFactory(dockerClient);

        InProcessServerBuilder inProcessServerBuilder = InProcessServerBuilder.forName(RPC_SERVER_NAME).directExecutor();
        InProcessChannelBuilder channelBuilder = InProcessChannelBuilder.forName(RPC_SERVER_NAME).directExecutor();

        WorkerFactory workerFactory = mock(WorkerFactory.class);

        FtepServiceLauncher ftepServiceLauncher = new FtepServiceLauncher(workerFactory, jobDataService, new FtepGuiServiceManager(), catalogueService, costingService);
        FtepWorker ftepWorker = new FtepWorker(dockerClientFactory, jobEnvironmentService, ioManager);

        when(workerFactory.getWorker(WorkerEnvironment.LOCAL)).thenReturn(FtepWorkerGrpc.newBlockingStub(channelBuilder.build()));

        inProcessServerBuilder.addService(ftepServiceLauncher);
        inProcessServerBuilder.addService(ftepWorker);

        server = inProcessServerBuilder.build().start();

        ftepServicesClient = new FtepServicesClient(channelBuilder);

        // Ensure the test image is available before testing
        dockerClient.pullImageCmd(TEST_CONTAINER_IMAGE).exec(new PullImageResultCallback()).awaitSuccess();
    }

    @After
    public void tearDown() {
        server.shutdownNow();
    }

    @Test
    public void launchProcessor() throws Exception {
        FtepService service = mock(FtepService.class);
        FtepServiceDescriptor serviceDescriptor = mock(FtepServiceDescriptor.class);
        User user = mock(User.class);
        Wallet wallet = mock(Wallet.class);
        when(user.getWallet()).thenReturn(wallet);
        when(wallet.getBalance()).thenReturn(100);

        when(service.getDockerTag()).thenReturn(TEST_CONTAINER_IMAGE);
        when(service.getServiceDescriptor()).thenReturn(serviceDescriptor);
        when(jobDataService.buildNew(any(), any(), any(), any())).thenAnswer(invocation -> {
            JobConfig config = new JobConfig(user, service);
            config.setInputs(invocation.getArgument(3));
            return new Job(config, invocation.getArgument(0), user);
        });

        String jobId = UUID.randomUUID().toString();
        String userId = "userId";
        String serviceId = "serviceId";
        Multimap<String, String> inputs = ImmutableMultimap.<String, String>builder()
                .put("inputKey1", "inputVal1")
                .putAll("inputKey2", ImmutableList.of("inputVal2-1", "inputVal2-2"))
                .build();

        when(costingService.estimateJobCost(any())).thenReturn(20);

        Multimap<String, String> outputs = ftepServicesClient.launchService(userId, serviceId, jobId, inputs);
        assertThat(outputs, is(notNullValue()));

        List<String> jobConfigLines = Files.readAllLines(fs.getPath("/tmp/ftep_data/Job_" + jobId + "/FTEP-WPS-INPUT.properties"));
        assertThat(jobConfigLines, is(ImmutableList.of(
                "inputKey1=\"inputVal1\"",
                "inputKey2=\"inputVal2-1,inputVal2-2\""
        )));

        verify(costingService).chargeForJob(eq(wallet), any());
    }

}
