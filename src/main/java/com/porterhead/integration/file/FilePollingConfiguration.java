package com.porterhead.integration.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * Set up Directories
 *
 * @author Iain Porter
 */
@Configuration
public class FilePollingConfiguration {

    @Bean(name="inboundReadDirectory")
    public File inboundReadDirectory(@Value("${inbound.read.path}") String path) {
        return makeDirectory(path);
    }

    @Bean(name="inboundProcessedDirectory")
    public File inboundProcessedDirectory(@Value("${inbound.processed.path}") String path) {
        return makeDirectory(path);
    }

    @Bean(name="inboundFailedDirectory")
    public File inboundFailedDirectory(@Value("${inbound.failed.path}") String path) {
        return makeDirectory(path);
    }

    @Bean(name="inboundOutDirectory")
    public File inboundOutDirectory(@Value("${inbound.out.path}") String path) {
        return makeDirectory(path);
    }

    private File makeDirectory(String path) {
        File file = new File(path);
        file.mkdirs();
        return file;
    }

}
