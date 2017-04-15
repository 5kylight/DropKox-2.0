package com.dropkox.synchronizer.drive;

import com.google.api.services.drive.Drive;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class GoogleDriveSynchronizer {

    @Autowired
    private Drive driveService;


}
