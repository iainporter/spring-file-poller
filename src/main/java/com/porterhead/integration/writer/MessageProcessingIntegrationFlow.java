package com.porterhead.integration.writer;


import com.porterhead.integration.configuration.ApplicationConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.file.FileWritingMessageHandler;
import org.springframework.integration.handler.LoggingHandler;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
public class MessageProcessingIntegrationFlow {

    @Autowired
    public File inboundOutDirectory;

    @Bean
    public IntegrationFlow writeToFile(@Qualifier("fileWritingMessageHandler") MessageHandler fileWritingMessageHandler) {
        return IntegrationFlows.from(ApplicationConfiguration.INBOUND_CHANNEL)
                .transform(m -> new StringBuilder((String)m).reverse().toString())
                .handle(fileWritingMessageHandler)
                .handle(loggingHandler())
                .get();
    }


    @Bean (name = "fileWritingMessageHandler")
    public MessageHandler fileWritingMessageHandler() {
        FileWritingMessageHandler handler = new FileWritingMessageHandler(inboundOutDirectory);
        handler.setAutoCreateDirectory(true);
        return handler;
    }

    @Bean
    public MessageHandler loggingHandler() {
        LoggingHandler logger = new LoggingHandler("INFO");
        logger.setShouldLogFullMessage(true);
        return logger;
    }

}
