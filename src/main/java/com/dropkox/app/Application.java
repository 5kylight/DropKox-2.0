package com.dropkox.app;

import com.dropkox.synchronizer.drive.GoogleDriveSynchronizer;
import com.dropkox.synchronizer.filesystem.FilesystemSynchronizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import javax.xml.bind.JAXBException;

@Service
@EnableAsync(proxyTargetClass = true)
@SpringBootApplication(scanBasePackages = { "com.dropkox" })
public class Application implements CommandLineRunner {

    private final GoogleDriveSynchronizer googleDriveSynchronizer;
    private final FilesystemSynchronizer filesystemSynchronizer;

    @Autowired
    public Application(GoogleDriveSynchronizer googleDriveSynchronizer, FilesystemSynchronizer filesystemSynchronizer) {
        this.googleDriveSynchronizer = googleDriveSynchronizer;
        this.filesystemSynchronizer = filesystemSynchronizer;
    }

    @Override
    public void run(String... args) throws Exception {
        filesystemSynchronizer.startListening();
        googleDriveSynchronizer.startListening();
    }
    public static void main(String[] args) throws JAXBException {
        new SpringApplicationBuilder(Application.class).web(false).run(args);

    }
}
