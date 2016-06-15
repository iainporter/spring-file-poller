package com.porterhead.integration.file;


import com.porterhead.configuration.ApplicationConfiguration;
import com.porterhead.integration.TestUtils;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.channel.MessageChannels;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.support.ChannelInterceptorAdapter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.FileCopyUtils;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = FilePollingTest.TestConfig.class)
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

    @After
    public void tearDown() throws Exception {
        TestUtils.deleteRecursive(inboundReadDirectory);
        TestUtils.deleteRecursive(inboundProcessedDirectory);
        TestUtils.deleteRecursive(inboundFailedDirectory);
    }

    @Autowired
    @Qualifier(ApplicationConfiguration.INBOUND_CHANNEL)
    public DirectChannel filePollingChannel;

    @Configuration
    @EnableAutoConfiguration
    @ComponentScan(basePackages = "com.porterhead.integration.file")
    public static class TestConfig {

        @Bean(name = ApplicationConfiguration.INBOUND_CHANNEL)
        public MessageChannel inboundFilePollingChannel() {
            return MessageChannels.direct().get();

        }

        @Bean
        public File inboundReadDirectory() throws IOException {
            return tempFolder.newFolder("in");
        }

        @Bean
        public File inboundProcessedDirectory() throws IOException {
            return tempFolder.newFolder("processed");
        }

        @Bean
        public File inboundFailedDirectory() throws IOException {
            return tempFolder.newFolder("failed");
        }

        @Bean
        public IntegrationFlow loggingFlow(@Qualifier(ApplicationConfiguration.INBOUND_CHANNEL) MessageChannel inChannel) {
            return IntegrationFlows.from(inChannel)
                    .handle(this.loggingHandler())
                    .get();
        }

        @Bean
        public MessageHandler loggingHandler() {
            LoggingHandler logger = new LoggingHandler("INFO");
            logger.setShouldLogFullMessage(true);
            return logger;
        }
    }

    @Test
    public void pollFindsValidFile() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        filePollingChannel.addInterceptor(new ChannelInterceptorAdapter() {
            @Override
            public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
                latch.countDown();
                super.postSend(message, channel, sent);
            }
        });
        FileCopyUtils.copy(TestUtils.locateClasspathResource(TestUtils.FILE_FIXTURE_PATH), new File(inboundReadDirectory, TestUtils.FILE_FIXTURE_NAME ));
        assertThat(latch.await(5, TimeUnit.SECONDS), is(true));
        TestUtils.assertThatDirectoryIsEmpty(inboundReadDirectory);
        TestUtils.assertThatDirectoryIsEmpty(inboundFailedDirectory);
        TestUtils.assertThatDirectoryHasFiles(inboundProcessedDirectory, 1);
    }

    @Test
    public void pollIgnoresInvalidFile() throws Exception {
        FileCopyUtils.copy(TestUtils.locateClasspathResource(TestUtils.FILE_FIXTURE_PATH), new File(inboundReadDirectory, TestUtils.FILE_FIXTURE_NAME + ".tmp" ));
        TestUtils.assertThatDirectoryIsEmpty(inboundProcessedDirectory);
        TestUtils.assertThatDirectoryIsEmpty(inboundFailedDirectory);
        TestUtils.assertThatDirectoryHasFiles(inboundReadDirectory, 1);
    }

    @Test
    public void pollIgnoresFileAlreadySeen() throws Exception {
        final CountDownLatch stopLatch = new CountDownLatch(1);
        filePollingChannel.addInterceptor(new ChannelInterceptorAdapter() {
            @Override
            public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
                stopLatch.countDown();
                super.postSend(message, channel, sent);
            }
        });
        FileCopyUtils.copy(TestUtils.locateClasspathResource(TestUtils.FILE_FIXTURE_PATH), new File(inboundReadDirectory, TestUtils.FILE_FIXTURE_NAME ));
        assertThat(stopLatch.await(5, TimeUnit.SECONDS), is(true));
        TestUtils.assertThatDirectoryIsEmpty(inboundReadDirectory);
        TestUtils.assertThatDirectoryIsEmpty(inboundFailedDirectory);
        TestUtils.assertThatDirectoryHasFiles(inboundProcessedDirectory, 1);
        //put file with same name in directory
        FileCopyUtils.copy(TestUtils.locateClasspathResource(TestUtils.FILE_FIXTURE_PATH), new File(inboundReadDirectory, TestUtils.FILE_FIXTURE_NAME ));
        TestUtils.assertThatDirectoryIsEmpty(inboundFailedDirectory);
        TestUtils.assertThatDirectoryHasFiles(inboundReadDirectory, 1);
        TestUtils.assertThatDirectoryHasFiles(inboundProcessedDirectory, 1);
    }

    @Test
    public void rollbackMovesFileToFailed() throws Exception {
        final CountDownLatch stopLatch = new CountDownLatch(1);
        filePollingChannel.addInterceptor(new ChannelInterceptorAdapter() {
            @Override
            public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
                stopLatch.countDown();
                throw new RuntimeException("Forcing an Exception to trigger rollback");
            }
        });
        FileCopyUtils.copy(TestUtils.locateClasspathResource(TestUtils.FILE_FIXTURE_PATH), new File(inboundReadDirectory, TestUtils.FILE_FIXTURE_NAME ));
        assertThat(stopLatch.await(5, TimeUnit.SECONDS), is(true));
        TestUtils.assertThatDirectoryIsEmpty(inboundReadDirectory);
        TestUtils.assertThatDirectoryIsEmpty(inboundProcessedDirectory);
        TestUtils.assertThatDirectoryHasFiles(inboundFailedDirectory, 1);
    }

}
