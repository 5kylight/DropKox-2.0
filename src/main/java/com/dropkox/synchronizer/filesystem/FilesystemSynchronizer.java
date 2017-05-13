package com.dropkox.synchronizer.filesystem;

import com.dropkox.model.EventType;
import com.dropkox.model.FileEvent;
import com.dropkox.model.FileType;
import com.dropkox.model.KoxFile;
import com.dropkox.synchronizer.SynchronizationService;
import com.dropkox.synchronizer.Synchronizer;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.nio.file.Path;

@Log
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@ToString(exclude = "synchronizationService")
public class FilesystemSynchronizer implements Synchronizer {

    @Value("${local.dir}")
    private File rootFolder;

    @NonNull
    private SynchronizationService synchronizationService;

    @PostConstruct
    public void start() {
        synchronizationService.register(this);
    }


    public void startListening() {

    }

    void processFilesystemEvent(Path path, EventType eventType, FileType fileType) {
        FileEvent fileEvent = FileEvent.builder()
                .eventType(eventType)
                .koxFile(KoxFile.builder().fileType(fileType).id(path.toString()).source(this).build())
                .timestamp(System.currentTimeMillis())
                .build();

        log.info("Received " + fileEvent);
        synchronizationService.accept(fileEvent);
    }

    @Override
    public void process(FileEvent fileEvent) {

    }
}
