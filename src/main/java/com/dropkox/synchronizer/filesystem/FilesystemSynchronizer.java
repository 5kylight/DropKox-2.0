package com.dropkox.synchronizer.filesystem;

import com.dropkox.model.EventType;
import com.dropkox.model.FileEvent;
import com.dropkox.model.FileType;
import com.dropkox.model.KoxFile;
import com.dropkox.watcher.IFileSystemEventProcessor;
import com.dropkox.synchronizer.SynchronizationService;
import com.dropkox.synchronizer.ISynchronizer;
import com.dropkox.watcher.RecursiveWatcherService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
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
import java.util.Date;

import static javaslang.API.$;
import static javaslang.API.Case;
import static javaslang.API.Match;
import static javaslang.API.run;
import static javaslang.Predicates.is;

@Log
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@ToString(exclude = "synchronizationService")
public class FilesystemSynchronizer implements ISynchronizer, IFileSystemEventProcessor {

    @Value("${local.dir}")
    private File rootFolder;

    @NonNull
    private SynchronizationService synchronizationService;

    private RecursiveWatcherService recursiveWatcherService;

    @PostConstruct
    public void start() {
        recursiveWatcherService = new RecursiveWatcherService(this, rootFolder);
        synchronizationService.register(this);
    }


    @SneakyThrows(IOException.class)
    public void startListening() {
        recursiveWatcherService.init();
    }

    @Override
    public void processFilesystemEvent(@NonNull final Path path, @NonNull final EventType eventType, @NonNull final FileType fileType) {
        KoxFile koxFile = KoxFile.builder().fileType(fileType).path(path.toString()).id(path.toString()).name(path.toString()).source(this).modificationDate(new Date()).build();
        FileEvent fileEvent = FileEvent.builder()
                .eventType(eventType)
                .koxFile(koxFile)
                .timestamp(System.currentTimeMillis())
                .build();

        synchronizationService.accept(fileEvent);
    }

    @Override
    @Async
    public void process(@NonNull final FileEvent fileEvent) {
        Match(fileEvent.getEventType()).of(
                Case(is(EventType.MODIFY), o -> run(() -> fileCreated(fileEvent.getKoxFile()))),
                Case(is(EventType.DELETE), o -> run(() -> fileDeleted(fileEvent.getKoxFile()))),
                Case($(), o -> run(() -> {
                    throw new UnsupportedOperationException("Event type not supported yet! " + fileEvent.getEventType());
                }))
        );
    }


    @Override
    public InputStream getInputStream(@NonNull final KoxFile koxFile) {
        try {
            return new FileInputStream(rootFolder + "/" + koxFile.getPath());
        } catch (FileNotFoundException e) {
            log.warning("File not found: " + koxFile.getId());
            return null;
        }
    }

    private void fileCreated(KoxFile koxFile) {
        log.info("Writing file: " + koxFile.getName());
        try {
            InputStream inputStream = koxFile.getSource().getInputStream(koxFile);
            if (inputStream != null)
                Files.copy(inputStream, getAbsolutePath(koxFile), StandardCopyOption.REPLACE_EXISTING);
            else
                log.warning("Input stream is null!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SneakyThrows(IOException.class)
    private void fileDeleted(KoxFile koxFile) {
        Files.deleteIfExists(getAbsolutePath(koxFile));
    }


    private Path getAbsolutePath(KoxFile koxFile) {
        return Paths.get(rootFolder + "/" + koxFile.getPath());
    }


}
