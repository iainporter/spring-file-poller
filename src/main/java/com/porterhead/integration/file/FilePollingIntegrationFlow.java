package com.porterhead.integration.file;

import com.porterhead.integration.configuration.ApplicationConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.core.Pollers;
import org.springframework.integration.dsl.support.Transformers;
import org.springframework.integration.file.FileReadingMessageSource;
import org.springframework.integration.file.filters.AcceptOnceFileListFilter;
import org.springframework.integration.file.filters.CompositeFileListFilter;
import org.springframework.integration.file.filters.RegexPatternFileListFilter;
import org.springframework.integration.transaction.DefaultTransactionSynchronizationFactory;
import org.springframework.integration.transaction.ExpressionEvaluatingTransactionSynchronizationProcessor;
import org.springframework.integration.transaction.PseudoTransactionManager;
import org.springframework.integration.transaction.TransactionSynchronizationFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.File;
import java.util.Arrays;

/**
 *  Inbound File Adapter looks for files that match the given regular expression
 *  Any files that have already been processed with the same name within the same
 *  JVM session will be ignored
 *  The poller is transactional and will move the file to a processed directory on successful
 *  downstream processing. If there is an exception in processing the file will be moved to a
 *  failed directory
 *
 *  @author Iain Porter
 */
@Configuration
class FilePollingIntegrationFlow {

    @Autowired
    public File inboundReadDirectory;

    @Autowired
    private ApplicationContext applicationContext;

    @Bean
    public IntegrationFlow inboundFileIntegration(@Value("${inbound.file.poller.fixed.delay}") long period,
                                                  @Value("${inbound.file.poller.max.messages.per.poll}") int maxMessagesPerPoll,
                                                  TaskExecutor taskExecutor,
                                                  MessageSource<File> fileReadingMessageSource) {
        return IntegrationFlows.from(fileReadingMessageSource,
                c -> c.poller(Pollers.fixedDelay(period)
                        .taskExecutor(taskExecutor)
                        .maxMessagesPerPoll(maxMessagesPerPoll)
                        .transactionSynchronizationFactory(transactionSynchronizationFactory())
                        .transactional(transactionManager())))
                .transform(Transformers.fileToString())
                .channel(ApplicationConfiguration.INBOUND_CHANNEL)
                .get();
    }

    @Bean
    TaskExecutor taskExecutor(@Value("${inbound.file.poller.thread.pool.size}") int poolSize) {
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(poolSize);
        return taskExecutor;
    }

    @Bean
    PseudoTransactionManager transactionManager() {
        return new PseudoTransactionManager();
    }

    @Bean
    TransactionSynchronizationFactory transactionSynchronizationFactory() {
        ExpressionParser parser = new SpelExpressionParser();
        ExpressionEvaluatingTransactionSynchronizationProcessor syncProcessor =
                new ExpressionEvaluatingTransactionSynchronizationProcessor();
        syncProcessor.setBeanFactory(applicationContext.getAutowireCapableBeanFactory());
        syncProcessor.setAfterCommitExpression(parser.parseExpression("payload.renameTo(new java.io.File(@inboundProcessedDirectory.path " +
                " + T(java.io.File).separator + payload.name))"));
        syncProcessor.setAfterRollbackExpression(parser.parseExpression("payload.renameTo(new java.io.File(@inboundFailedDirectory.path " +
                " + T(java.io.File).separator + payload.name))"));
        return new DefaultTransactionSynchronizationFactory(syncProcessor);
    }

    @Bean
    public FileReadingMessageSource fileReadingMessageSource(@Value("${inbound.filename.regex}") String regex) {
        FileReadingMessageSource source = new FileReadingMessageSource();
        source.setDirectory(this.inboundReadDirectory);
        source.setAutoCreateDirectory(true);
        CompositeFileListFilter<File> filter = new CompositeFileListFilter<>(
                Arrays.asList(new AcceptOnceFileListFilter<File>(),
                        new RegexPatternFileListFilter(regex))
        );
        source.setFilter(filter);
        return source;
    }


}