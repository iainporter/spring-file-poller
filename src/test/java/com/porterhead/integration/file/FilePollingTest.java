package com.porterhead.integration.file;


import com.porterhead.integration.TestUtils;
import com.porterhead.integration.configuration.ApplicationConfiguration;
import org.junit.After;
import org.junit.Test;
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
        assertThatDirectoryHasFiles(inboundOutDirectory, 1);
        assertThatDirectoryHasFiles(inboundProcessedDirectory, 1);
    }

    @Test
    public void pollIgnoresInvalidFile() throws Exception {
        copy(TestUtils.locateClasspathResource(TestUtils.FILE_FIXTURE_PATH), new File(inboundReadDirectory, TestUtils.FILE_FIXTURE_NAME + ".tmp" ));
        Thread.sleep(2000); //enough time to ensure the file has been read and ignored before the test finishes
        assertThatDirectoryIsEmpty(inboundProcessedDirectory);
        assertThatDirectoryIsEmpty(inboundFailedDirectory);
        assertThatDirectoryIsEmpty(inboundOutDirectory);
        assertThatDirectoryHasFiles(inboundReadDirectory, 1);
    }

    @Test
    public void pollIgnoresFileAlreadySeen() throws Exception {
        final CountDownLatch stopLatch = new CountDownLatch(1);
        filePollingChannel.addInterceptor(new ChannelInterceptor() {
            @Override
            public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
                stopLatch.countDown();
            }
        });
        copy(TestUtils.locateClasspathResource(TestUtils.FILE_FIXTURE_PATH), new File(inboundReadDirectory, TestUtils.FILE_FIXTURE_NAME ));
        //wait for stopLatch before asserting
        assertThat(stopLatch.await(5, TimeUnit.SECONDS), is(true));
        assertThatDirectoryHasFiles(inboundProcessedDirectory, 1);
        assertThatDirectoryHasFiles(inboundOutDirectory, 1);
        assertThatDirectoryIsEmpty(inboundReadDirectory);
        assertThatDirectoryIsEmpty(inboundFailedDirectory);
        copy(TestUtils.locateClasspathResource(TestUtils.FILE_FIXTURE_PATH), new File(inboundReadDirectory, TestUtils.FILE_FIXTURE_NAME ));
        Thread.sleep(2000); //wait longer than the polling period
        assertThatDirectoryIsEmpty(inboundFailedDirectory);
        assertThatDirectoryHasFiles(inboundReadDirectory, 1);
        assertThatDirectoryHasFiles(inboundProcessedDirectory, 1);
        assertThatDirectoryHasFiles(inboundOutDirectory, 1);
    }

    @Test
    public void rollbackMovesFileToFailed() throws Exception {
        final CountDownLatch stopLatch = new CountDownLatch(1);
        filePollingChannel.addInterceptor(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                stopLatch.countDown();
                throw new RuntimeException("Forcing an Exception to trigger rollback");
            }
        });
        copy(TestUtils.locateClasspathResource(TestUtils.FILE_FIXTURE_PATH), new File(inboundReadDirectory, TestUtils.FILE_FIXTURE_NAME ));
        assertThat(stopLatch.await(5, TimeUnit.SECONDS), is(true));
        assertThatDirectoryIsEmpty(inboundReadDirectory);
        assertThatDirectoryIsEmpty(inboundProcessedDirectory);
        assertThatDirectoryIsEmpty(inboundOutDirectory);
        assertThatDirectoryHasFiles(inboundFailedDirectory, 1);
    }

}
