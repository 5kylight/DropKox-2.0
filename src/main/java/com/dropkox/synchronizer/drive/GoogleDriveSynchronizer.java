package com.dropkox.synchronizer.drive;

import com.dropkox.model.FileEvent;
import com.dropkox.synchronizer.Synchronizer;
import com.google.api.services.drive.Drive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GoogleDriveSynchronizer implements Synchronizer {

    @Autowired
    private Drive driveService;


    @Override
    public void process(FileEvent fileEvent) {

    }
}
