package com.dropkox.synchronizer.drive;

import com.dropkox.synchronizer.SynchronizationService;
import com.dropkox.synchronizer.drive.config.GoogleDriveConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@SpringBootTest(classes = {GoogleDriveSynchronizer.class, GoogleDriveConfig.class, SynchronizationService.class})
@RunWith(SpringJUnit4ClassRunner.class)
public class GoogleDriveSynchronizerTestIT {

    @Autowired
    private GoogleDriveSynchronizer googleDriveSynchronizer;

    @Test
    public void testStartListeningForChanges() throws Exception {
        googleDriveSynchronizer.process(null);

        while (true) {
            // TODO Update
            // googleDriveSynchronizer.startListening();
            Thread.sleep(1000);
        }
    }
}