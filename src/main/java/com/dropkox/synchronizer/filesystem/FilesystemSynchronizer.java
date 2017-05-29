package com.dropkox.synchronizer.filesystem;

import com.dropkox.model.EventType;
import com.dropkox.model.FileEvent;
import com.dropkox.model.FileType;
import com.dropkox.model.KoxFile;
import com.dropkox.synchronizer.ISynchronizer;
import com.dropkox.synchronizer.SynchronizationService;
import com.dropkox.watcher.IFileSystemEventProcessor;
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
import java.time.Instant;
import java.util.Date;

import static javaslang.API.$;
import static javaslang.API.Case;
import static javaslang.API.Match;
import static javaslang.API.run;
import static javaslang.Predicates.is;
import static javaslang.Predicates.isIn;

@Log
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@ToString(exclude = {"synchronizationService", "recursiveWatcherService"})
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

        Instant modificationDate = null;
        try {
            modificationDate = eventType == EventType.DELETE ? Instant.now() : Files.getLastModifiedTime(Paths.get(rootFolder + "/" + path)).toInstant();
        } catch (IOException e) {
            e.printStackTrace();
        }
        KoxFile koxFile = KoxFile.builder()
                .fileType(fileType)
                .path(path.toString())
                .id(path.toString())
                .name(path.toString())
                .source(this)
                .modificationDate(Date.from(modificationDate))
                .build();
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
        KoxFile koxFile = fileEvent.getKoxFile();

        if (!isFileNeededToUpdate(koxFile)) {
            log.warning("Ignoring change: " + fileEvent);
            return;
        }

        Match(fileEvent.getEventType()).of(
                Case($(isIn(EventType.CREATE, EventType.MODIFY)), o -> run(() -> fileModified(koxFile))),
                Case(is(EventType.DELETE), o -> run(() -> fileDeleted(koxFile))),
                Case($(), o -> run(() -> {
                    throw new UnsupportedOperationException("Event type not supported yet! " + fileEvent.getEventType());
                }))
        );
    }

    private boolean isFileNeededToUpdate(KoxFile koxFile) {
        Path absolutePath = getAbsolutePath(koxFile);

        if (!Files.exists(absolutePath) )
            return true;

        Instant changedFileModificationTime =  koxFile.getModificationDate().toInstant();
        Instant currentFileModificationTime = null;
        try {
             currentFileModificationTime = Files.getLastModifiedTime(absolutePath).toInstant();
        } catch (IOException e) {
            e.printStackTrace();
        }
        log.info("changedFileModificationTime " + changedFileModificationTime);
        log.info("currentFileModificationTime " + currentFileModificationTime);

        return currentFileModificationTime.plusSeconds(5).isBefore(changedFileModificationTime);
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

    private void fileModified(KoxFile koxFile) {
        log.info("Saving file: " + koxFile.getName());
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

    private void fileDeleted(KoxFile koxFile) {
        log.info("Removing file: " + koxFile);
        try {
            Files.deleteIfExists(getAbsolutePath(koxFile));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private Path getAbsolutePath(KoxFile koxFile) {
        return Paths.get(rootFolder + "/" + koxFile.getPath());
    }


}
