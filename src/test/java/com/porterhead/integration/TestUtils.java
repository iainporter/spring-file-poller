package com.porterhead.integration;


import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class TestUtils {

    public final static String FILE_FIXTURE_PATH = "/data/foo.txt";

    public final static String FILE_FIXTURE_NAME = "foo.txt";

    public static File locateClasspathResource(String resourceName) {
        try {
            return new ClassPathResource(resourceName).getFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void assertThatDirectoryHasFiles(File directory, int expectedFileCount) throws Exception {
        int count = 0;
        while (directory.listFiles().length < expectedFileCount && count++ < 10) {
            Thread.sleep(500);
        }
        assertThat(directory.listFiles().length, is(expectedFileCount));
    }

    public static void assertThatDirectoryIsEmpty(File directory) throws Exception {
        int count = 0;
        while (directory.listFiles().length > 0 && count++ < 10) {
            Thread.sleep(500);
        }
        assertThat(directory.listFiles().length, is(0));
    }

    public static boolean deleteRecursive(File path) throws FileNotFoundException{
        if (!path.exists()) throw new FileNotFoundException(path.getAbsolutePath());
        boolean ret = true;
        if (path.isDirectory()){
            for (File f : path.listFiles()){
                ret = ret && deleteRecursive(f);
            }
        }
        return ret && path.delete();
    }
}
