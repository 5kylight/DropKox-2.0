package com.dropkox.synchronizer.filesystem;

import com.dropkox.synchronizer.SynchronizerService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@SpringBootTest(classes = {FilesystemSynchronizer.class, RecursiveWatcherService.class, SynchronizerService.class})
@RunWith(SpringJUnit4ClassRunner.class)
public class FilesystemSynchronizerServiceTest {


    @Autowired
    private FilesystemSynchronizer filesystemSynchronizer;


    @Test
    public void test() throws InterruptedException {
        while (true) {
            filesystemSynchronizer.startListening();
            Thread.sleep(1000);
        }
    }
}