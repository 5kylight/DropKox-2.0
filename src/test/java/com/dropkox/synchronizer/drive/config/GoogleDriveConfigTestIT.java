package com.dropkox.synchronizer.drive.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.List;

@SpringBootTest(classes = GoogleDriveConfig.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class GoogleDriveConfigTestIT {

    @Autowired
    private Drive driveService;

    @Test
    public void testGetAllNames() throws IOException, GeneralSecurityException {
        // Print the names and IDs for up to 10 files.
        FileList result = driveService.files().list()
                .setPageSize(10)
                .setFields("nextPageToken, files(id, name)")
                .execute();
        List<File> files = result.getFiles();
        if (files == null || files.size() == 0) {
            System.out.println("No files found.");
        } else {


            System.out.println("Files:");
            for (File file : files) {

                OutputStream out = new ByteArrayOutputStream();
                MediaHttpDownloader downloader =
                        new MediaHttpDownloader(GoogleNetHttpTransport.newTrustedTransport(), driveService.getRequestFactory().getInitializer());
                downloader.setDirectDownloadEnabled(true);
//                downloader.download(new GenericUrl(file.getLastModifyingUser()), out);
                System.out.printf("%s (%s)\n", file.getName(), file.getId());
                System.out.println("dupa: " + out);
            }
        }
    }


   }