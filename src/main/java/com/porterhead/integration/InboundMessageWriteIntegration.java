package com.porterhead.integration;


import com.porterhead.configuration.ApplicationConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.file.FileWritingMessageHandler;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.messaging.MessageHandler;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class InboundMessageWriteIntegration {

    @Autowired
    public File inboundOutDirectory;

    @Autowired
    @Qualifier("fileWritingMessageHandler")
    public MessageHandler fileWritingMessageHandler;

    @Bean
    public IntegrationFlow writeToFile() {
        return IntegrationFlows.from(ApplicationConfiguration.INBOUND_CHANNEL)
                .handle(fileWritingMessageHandler)
                .handle(loggingHandler())
                .get();
    }

    @Bean
    public MessageHandler loggingHandler() {
        LoggingHandler logger = new LoggingHandler("INFO");
        logger.setShouldLogFullMessage(true);
        return logger;
    }


    @Bean (name = "fileWritingMessageHandler")
    MessageHandler fileWritingMessageHandler() {
        FileWritingMessageHandler handler = new FileWritingMessageHandler(inboundOutDirectory);
        handler.setAutoCreateDirectory(true);
        return handler;
    }
}
