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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import static javaslang.API.$;
import static javaslang.API.Case;
import static javaslang.API.Match;
import static javaslang.API.run;
import static javaslang.Predicates.is;

@Log
@Component
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
    @Async
    public void process(@NonNull final FileEvent fileEvent) {
        Match(fileEvent.getEventType()).of(
                Case(is(EventType.CREATE), o -> run(() -> fileCreated(fileEvent))),
                Case($(), o -> run(() -> {
                    throw new UnsupportedOperationException("Event type not supported yet!");
                }))
        );
    }

    private void fileCreated(FileEvent fileEvent) {
        log.info("Writing file: " + fileEvent.getKoxFile().getName());
        try {
            InputStream inputStream = fileEvent.getKoxFile().getSource().getInputStream(fileEvent.getKoxFile());
            if (inputStream != null)
                Files.copy(inputStream, Paths.get(rootFolder + "/" + fileEvent.getKoxFile().getPath()), StandardCopyOption.REPLACE_EXISTING);
            else
                log.warning("Input stream is nulll!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public InputStream getInputStream(@NonNull final KoxFile koxFile) {
        try {
            return new FileInputStream(koxFile.getId());
        } catch (FileNotFoundException e) {
            log.warning("File not found: " + koxFile.getId());
            return null;
        }
    }
}
