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
import lombok.extern.log4j.Log4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.util.ConcurrentHashSet;
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
import java.util.Arrays;
import java.util.Date;
import java.util.Set;

import static javaslang.API.$;
import static javaslang.API.Case;
import static javaslang.API.Match;
import static javaslang.API.run;
import static javaslang.Predicates.is;
import static javaslang.Predicates.isIn;

@Component
@Log4j
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@ToString(exclude = {"synchronizationService", "recursiveWatcherService"})
public class FilesystemSynchronizer implements ISynchronizer, IFileSystemEventProcessor {

    @Value("${local.dir}")
    private File rootFolder;

    private final Set<Path> inProgressPaths = new ConcurrentHashSet<>();

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


        String name = Arrays.stream(path.toString().split("/")).filter(p -> !p.isEmpty()).reduce((f, s) -> s).orElse(null);
        Instant modificationDate = null;
        try {
            modificationDate = eventType == EventType.DELETE || fileType == FileType.DIR ? Instant.now() : Files.getLastModifiedTime(Paths.get(rootFolder + "/" + path)).toInstant();
        } catch (IOException e) {
            e.printStackTrace();
        }
        KoxFile koxFile = KoxFile.builder()
                .fileType(fileType)
                .path(path.toString())
                .id(path.toString())
                .name(name)
                .source(this)
                .modificationDate(Date.from(modificationDate))
                .build();
        FileEvent fileEvent = FileEvent.builder()
                .eventType(eventType)
                .koxFile(koxFile)
                .timestamp(System.currentTimeMillis())
                .build();

        if (inProgressPaths.contains(getAbsolutePath(koxFile))) {
            log.debug("File under processing");
            return;
        }

        synchronizationService.accept(fileEvent);
    }

    @Override
    @Async
    public void process(@NonNull final FileEvent fileEvent) {
        KoxFile koxFile = fileEvent.getKoxFile();

        if (!isFileNeededToUpdate(koxFile)) {
            log.warn("Ignoring change: " + fileEvent);
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

        if (Files.notExists(absolutePath))
            return true;

        Instant changedFileModificationTime = koxFile.getModificationDate().toInstant();
        Instant currentFileModificationTime = null;
        try {
            currentFileModificationTime = Files.getLastModifiedTime(absolutePath).toInstant();
        } catch (IOException e) {
            e.printStackTrace();
        }
        log.debug("changedFileModificationTime " + changedFileModificationTime);
        log.debug("currentFileModificationTime " + currentFileModificationTime);

        return currentFileModificationTime.plusSeconds(5).isBefore(changedFileModificationTime);
    }


    @Override
    public InputStream getInputStream(@NonNull final KoxFile koxFile) {
        try {
            return new FileInputStream(rootFolder + "/" + koxFile.getPath());
        } catch (FileNotFoundException e) {
            log.warn("File not found: " + koxFile.getId());
            return null;
        }
    }

    @SneakyThrows(IOException.class)
    private void fileModified(KoxFile koxFile) {

        if (koxFile.getFileType() == FileType.REGULAR_FILE)
            saveRegularFile(koxFile);
        else
            saveDirectory(koxFile);
    }


    private void saveRegularFile(KoxFile koxFile) throws IOException {
        log.info("Saving file: " + koxFile.getName());
        InputStream inputStream = koxFile.getSource().getInputStream(koxFile);

        if (inputStream != null) {
            Path absolutePath = getAbsolutePath(koxFile);
            inProgressPaths.add(absolutePath);
            Files.copy(inputStream, absolutePath, StandardCopyOption.REPLACE_EXISTING);
            inProgressPaths.remove(absolutePath);
        } else
            log.warn("Input stream is null!");
    }

    @SneakyThrows(IOException.class)
    private void saveDirectory(KoxFile koxFile) {
        log.info("Saving directory: " + koxFile.getName());

        if (Files.exists(getAbsolutePath(koxFile))) {
            log.warn("Directory already exits " + koxFile);
            return;
        }

        Path absolutePath = getAbsolutePath(koxFile);
        inProgressPaths.add(absolutePath);
        Files.createDirectory(absolutePath);
        inProgressPaths.remove(absolutePath);

    }


    private void fileDeleted(KoxFile koxFile) {
        if (koxFile.getFileType() == FileType.REGULAR_FILE)
            deleteRegularFile(koxFile);
        else
            deleteDirectory(koxFile);

    }

    private void deleteRegularFile(KoxFile koxFile) {
        log.info("Removing file: " + koxFile);

        Path absolutePath = getAbsolutePath(koxFile);
        if (!absolutePath.startsWith(rootFolder.getAbsolutePath()))
            throw new RuntimeException("OMG!!!! Run away");

        inProgressPaths.add(absolutePath);
        absolutePath.toFile().delete(); // VERY, VERY DANGEROUS!!!
        inProgressPaths.remove(absolutePath);
    }

    private void deleteDirectory(KoxFile koxFile) {
        log.info("Removing directory: " + koxFile);
        try {

            Path absolutePath = getAbsolutePath(koxFile);
            if (!absolutePath.startsWith(rootFolder.getAbsolutePath()))
                throw new RuntimeException("OMG!!!! Run away");
            inProgressPaths.add(absolutePath);
            FileUtils.deleteDirectory(absolutePath.toFile()); // VERY, VERY DANGEROUS!!!
            inProgressPaths.remove(absolutePath);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private Path getAbsolutePath(KoxFile koxFile) {
        return Paths.get(rootFolder + "/" + koxFile.getPath());
    }


}
