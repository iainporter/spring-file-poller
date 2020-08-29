package com.porterhead.integration.file;


import com.porterhead.integration.TestUtils;
import com.porterhead.integration.configuration.ApplicationConfiguration;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.porterhead.integration.TestUtils.assertThatDirectoryHasFiles;
import static com.porterhead.integration.TestUtils.assertThatDirectoryIsEmpty;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.springframework.util.FileCopyUtils.copy;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class FilePollingTest  {

    @ClassRule
    public final static TemporaryFolder tempFolder = new TemporaryFolder();

    @Autowired
    @Qualifier("inboundReadDirectory")
    public File inboundReadDirectory;

    @Autowired
    @Qualifier("inboundProcessedDirectory")
    public File inboundProcessedDirectory;

    @Autowired
    @Qualifier("inboundFailedDirectory")
    public File inboundFailedDirectory;

    @Autowired
    @Qualifier("inboundOutDirectory")
    public File inboundOutDirectory;

    @After
    public void tearDown() throws Exception {
        TestUtils.deleteRecursive(inboundReadDirectory);
        TestUtils.deleteRecursive(inboundProcessedDirectory);
        TestUtils.deleteRecursive(inboundFailedDirectory);
        TestUtils.deleteRecursive(inboundOutDirectory);
    }

    @Autowired
    @Qualifier(ApplicationConfiguration.INBOUND_CHANNEL)
    public DirectChannel filePollingChannel;

    @Test
    public void pollFindsValidFile() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        filePollingChannel.addInterceptor(new ChannelInterceptor() {
            @Override
            public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
                latch.countDown();
            }
        });
        copy(TestUtils.locateClasspathResource(TestUtils.FILE_FIXTURE_PATH), new File(inboundReadDirectory, TestUtils.FILE_FIXTURE_NAME ));
        assertThat(latch.await(5, TimeUnit.SECONDS), is(true));
        assertThatDirectoryIsEmpty(inboundReadDirectory);
        assertThatDirectoryIsEmpty(inboundFailedDirectory);
        assertThatDirectoryHasFiles(inboundProcessedDirectory, 1);
    }

    @Test
    public void pollIgnoresInvalidFile() throws Exception {
        copy(TestUtils.locateClasspathResource(TestUtils.FILE_FIXTURE_PATH), new File(inboundReadDirectory, TestUtils.FILE_FIXTURE_NAME + ".tmp" ));
        assertThatDirectoryIsEmpty(inboundProcessedDirectory);
        assertThatDirectoryIsEmpty(inboundFailedDirectory);
        assertThatDirectoryHasFiles(inboundReadDirectory, 1);
    }

    @Test
    public void pollIgnoresFileAlreadySeen() throws Exception {
        copy(TestUtils.locateClasspathResource(TestUtils.FILE_FIXTURE_PATH), new File(inboundReadDirectory, TestUtils.FILE_FIXTURE_NAME ));
        assertThatDirectoryHasFiles(inboundProcessedDirectory, 1);
        assertThatDirectoryIsEmpty(inboundReadDirectory);
        assertThatDirectoryIsEmpty(inboundFailedDirectory);
        copy(TestUtils.locateClasspathResource(TestUtils.FILE_FIXTURE_PATH), new File(inboundReadDirectory, TestUtils.FILE_FIXTURE_NAME ));
        assertThatDirectoryIsEmpty(inboundFailedDirectory);
        assertThatDirectoryHasFiles(inboundReadDirectory, 1);
        assertThatDirectoryHasFiles(inboundProcessedDirectory, 1);
    }

    @Test
    public void rollbackMovesFileToFailed() throws Exception {
        final CountDownLatch stopLatch = new CountDownLatch(1);
        filePollingChannel.addInterceptor(new ChannelInterceptor() {
            @Override
            public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
                stopLatch.countDown();
                throw new RuntimeException("Forcing an Exception to trigger rollback");
            }
        });
        copy(TestUtils.locateClasspathResource(TestUtils.FILE_FIXTURE_PATH), new File(inboundReadDirectory, TestUtils.FILE_FIXTURE_NAME ));
        assertThat(stopLatch.await(5, TimeUnit.SECONDS), is(true));
        assertThatDirectoryIsEmpty(inboundReadDirectory);
        assertThatDirectoryIsEmpty(inboundProcessedDirectory);
        assertThatDirectoryHasFiles(inboundFailedDirectory, 1);
    }

}
