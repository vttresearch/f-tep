package com.cgi.eoss.ftep.server;

import com.cgi.eoss.ftep.catalogue.geoserver.GeoserverService;
import com.cgi.eoss.ftep.catalogue.resto.RestoService;
import com.cgi.eoss.ftep.io.ServiceInputOutputManager;
import com.cgi.eoss.ftep.io.download.DownloaderFacade;
import com.cgi.eoss.ftep.model.FtepService;
import com.cgi.eoss.ftep.model.FtepServiceDescriptor;
import com.cgi.eoss.ftep.model.User;
import com.cgi.eoss.ftep.orchestrator.service.FtepJobLauncher;
import com.cgi.eoss.ftep.orchestrator.service.WorkerFactory;
import com.cgi.eoss.ftep.persistence.service.ServiceDataService;
import com.cgi.eoss.ftep.persistence.service.UserDataService;
import com.cgi.eoss.ftep.rpc.FtepJobLauncherGrpc;
import com.cgi.eoss.ftep.rpc.FtepJobResponse;
import com.cgi.eoss.ftep.rpc.FtepServiceParams;
import com.cgi.eoss.ftep.rpc.GrpcUtil;
import com.cgi.eoss.ftep.rpc.Job;
import com.cgi.eoss.ftep.rpc.JobParam;
import com.cgi.eoss.ftep.rpc.worker.FtepWorkerGrpc;
import com.cgi.eoss.ftep.worker.FtepWorkerApplication;
import com.cgi.eoss.ftep.worker.WorkerConfig;
import com.cgi.eoss.ftep.worker.worker.FtepWorker;
import com.cgi.eoss.ftep.worker.worker.FtepWorkerNodeManager;
import com.cgi.eoss.ftep.worker.worker.JobEnvironmentService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.io.MoreFiles;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.inprocess.InProcessServerBuilder;
import org.hamcrest.CustomTypeSafeMatcher;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {
        FtepServerApplicationIT.FtepServerApplicationTestConfig.class
}, properties = {
        "logging.level.com.cgi.eoss.ftep=debug"
})
@Import({
        FtepWorkerApplication.class,
        WorkerConfig.class
})
@TestPropertySource
public class FtepServerApplicationIT {
    private static final String PROCESSOR_NAME = "service1";
    private static final String GUI_APPLICATION_NAME = "service2";
    private static final User TESTUSER = new User("testuser");

    @Autowired
    private InProcessServerBuilder serverBuilder;

    @Autowired
    private ManagedChannelBuilder channelBuilder;

    @Autowired
    private FtepJobLauncher ftepJobLauncher;

    @Autowired
    private UserDataService userDataService;

    @Autowired
    private ServiceDataService serviceDataService;

    @Autowired
    private FtepWorkerNodeManager nodeManager;

    @MockBean
    private WorkerFactory workerFactory;

    @MockBean
    private ServiceInputOutputManager ioManager;

    @MockBean // Keep this to avoid instantiating a real Bean which needs a data dir
    private DownloaderFacade downloaderFacade;

    @MockBean // Keep this to avoid instantiating a real Bean
    private GeoserverService geoserverService;

    @MockBean
    private RestoService restoService;

    @Autowired
    private Path workspace;

    @Autowired
    private Path outputProductBasedir;

    private Server server;

    private UUID ftepJobId;

    @BeforeClass
    public static void precondition() {
        // Shortcut if docker socket is not accessible to the current user
        assumeTrue("Unable to write to Docker socket; disabling docker tests", Files.isWritable(Paths.get("/var/run/docker.sock")));
    }

    @Configuration
    @Import({
            RpcTestConfig.class,
            FtepServerApplication.class
    })
    public static class FtepServerApplicationTestConfig {
        @Bean
        public Path workspace() throws IOException {
            Path workspace = Files.createTempDirectory(Paths.get("target"), FtepServerApplicationIT.class.getSimpleName());
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    MoreFiles.deleteRecursively(workspace);
                } catch (IOException ignored) {
                }
            }));
            return workspace;
        }

        @Bean
        public Path outputProductBasedir(Path workspace) throws IOException {
            return Files.createDirectory(workspace.resolve("outputProducts"));
        }

        @Bean
        public Path referenceDataBasedir(Path workspace) throws IOException {
            return Files.createDirectory(workspace.resolve("refData"));
        }

        @Bean
        public Path cacheRoot(Path workspace) throws IOException {
            return Files.createDirectory(workspace.resolve("cache"));
        }

        @Bean
        public Path jobEnvironmentRoot(Path workspace) throws IOException {
            return Files.createDirectory(workspace.resolve("jobs"));
        }
    }

    @Before
    public void setUp() throws Exception {
        ftepJobId = UUID.randomUUID();

        when(ioManager.getServiceContext(PROCESSOR_NAME)).thenReturn(Paths.get("src/test/resources/" + PROCESSOR_NAME).toAbsolutePath());
        when(ioManager.getServiceContext(GUI_APPLICATION_NAME)).thenReturn(Paths.get("src/test/resources/" + GUI_APPLICATION_NAME).toAbsolutePath());

        serverBuilder.addService(ftepJobLauncher);
        serverBuilder.addService(new FtepWorker(nodeManager, new JobEnvironmentService(workspace), ioManager, 1));
        server = serverBuilder.build().start();

        FtepWorkerGrpc.FtepWorkerBlockingStub workerStub = FtepWorkerGrpc.newBlockingStub(channelBuilder.build());
        when(workerFactory.getWorker(any())).thenReturn(workerStub);
        when(workerFactory.getWorkerById(any())).thenReturn(workerStub);

        User owner = userDataService.save(TESTUSER);

        FtepService testProcessor = new FtepService(PROCESSOR_NAME, owner, "test/" + PROCESSOR_NAME);
        FtepServiceDescriptor testProcessorDescriptor = new FtepServiceDescriptor();
        testProcessorDescriptor.setDataOutputs(ImmutableList.of(FtepServiceDescriptor.Parameter.builder().id("output").minOccurs(1).maxOccurs(1).build()));
        testProcessor.setServiceDescriptor(testProcessorDescriptor);

        FtepService testApplication = new FtepService(GUI_APPLICATION_NAME, owner, "test/" + GUI_APPLICATION_NAME);
        testApplication.setType(FtepService.Type.APPLICATION);
        FtepServiceDescriptor testApplicationDescriptor = new FtepServiceDescriptor();
        testApplicationDescriptor.setDataOutputs(ImmutableList.of(FtepServiceDescriptor.Parameter.builder().id("output").minOccurs(1).maxOccurs(7).build()));
        testApplication.setServiceDescriptor(testApplicationDescriptor);

        serviceDataService.save(ImmutableList.of(testProcessor, testApplication));

        when(restoService.ingestOutputProduct(any())).thenReturn(UUID.randomUUID());
    }

    @After
    public void tearDown() {
        server.shutdownNow();
    }

    @Test
    public void testProcessor() {
        FtepJobLauncherGrpc.FtepJobLauncherBlockingStub jobLauncher = FtepJobLauncherGrpc.newBlockingStub(channelBuilder.build());
        Iterator<FtepJobResponse> jobResponseIterator = jobLauncher.launchService(FtepServiceParams.newBuilder()
                .setServiceId(PROCESSOR_NAME)
                .setUserId(TESTUSER.getName())
                .setJobId(String.valueOf(ftepJobId))
                .addAllInputs(GrpcUtil.mapToParams(
                        ImmutableMultimap.<String, String>builder()
                                .put("inputKey1", "inputVal1")
                                .putAll("inputKey2", ImmutableList.of("inputVal2-1", "inputVal2-2"))
                                .build()
                ))
                .build());

        Job job = jobResponseIterator.next().getJob();
        assertThat(job, is(notNullValue()));
        FtepJobResponse.JobOutputs jobOutputs = jobResponseIterator.next().getJobOutputs();
        assertThat(jobOutputs, is(notNullValue()));
        assertThat(jobOutputs.getOutputsCount(), is(1));
        JobParam output = jobOutputs.getOutputs(0);
        Path relativeOutputPath = Paths.get(output.getParamValue(0).replace("ftep://outputProduct/", ""));
        assertThat(Files.exists(outputProductBasedir.resolve(relativeOutputPath)), is(true));
        assertThat(relativeOutputPath.getFileName().toString(), is("output_file_1"));
    }

    @Test
    public void testGuiApplication() {
        FtepJobLauncherGrpc.FtepJobLauncherBlockingStub jobLauncher = FtepJobLauncherGrpc.newBlockingStub(channelBuilder.build());
        Iterator<FtepJobResponse> jobResponseIterator = jobLauncher.launchService(FtepServiceParams.newBuilder()
                .setServiceId(GUI_APPLICATION_NAME)
                .setUserId(TESTUSER.getName())
                .setJobId(String.valueOf(ftepJobId))
                .addAllInputs(GrpcUtil.mapToParams(
                        ImmutableMultimap.<String, String>builder()
                                .put("inputKey1", "inputVal1")
                                .putAll("inputKey2", ImmutableList.of("inputVal2-1", "inputVal2-2"))
                                .build()
                ))
                .build());

        Job job = jobResponseIterator.next().getJob();
        assertThat(job, is(notNullValue()));
        FtepJobResponse.JobOutputs jobOutputs = jobResponseIterator.next().getJobOutputs();
        assertThat(jobOutputs, is(notNullValue()));
        assertThat(jobOutputs.getOutputsCount(), is(2));

        assertThat(jobOutputs.getOutputsList(), Matchers.containsInAnyOrder(
                new JobOutputParamMatcher("Output Param: output_file_1", "output_file_1"),
                new JobOutputParamMatcher("Output Param: A_plot.zip", "A_plot.zip")));
    }

    private final class JobOutputParamMatcher extends CustomTypeSafeMatcher<JobParam> {
        private final String expectedFileName;

        JobOutputParamMatcher(String description, String expectedFileName) {
            super(description);
            this.expectedFileName = expectedFileName;
        }

        @Override
        protected boolean matchesSafely(JobParam output) {
            Path relativeOutputPath = Paths.get(output.getParamValue(0).replace("ftep://outputProduct/", ""));
            return Files.exists(outputProductBasedir.resolve(relativeOutputPath))
                    && relativeOutputPath.getFileName().toString().equals(expectedFileName);
        }
    }

}