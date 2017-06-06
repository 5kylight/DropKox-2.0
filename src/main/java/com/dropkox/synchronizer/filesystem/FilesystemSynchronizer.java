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
import net.jodah.expiringmap.ExpiringMap;
import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.util.ConcurrentHashSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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
    private final Map<String, Object> recentUpdates = ExpiringMap.builder().expiration(5, TimeUnit.SECONDS).build();

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
    public void process(@NonNull final FileEvent fileEvent) {
        recentUpdates.put(fileEvent.getKoxFile().getPath(), new Object());

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

    @Override
    public InputStream getInputStream(@NonNull final KoxFile koxFile) {
        try {
            return new FileInputStream(rootFolder + "/" + koxFile.getPath());
        } catch (FileNotFoundException e) {
            log.warn("File not found: " + koxFile.getId());
            return null;
        }
    }

    @Override
    public void processFilesystemEvent(@NonNull final Path path, @NonNull final EventType eventType, @NonNull final FileType fileType) {
        if (recentUpdates.containsKey(path.toString())) {
            log.debug("Skipping recent update");
            return;
        }

        String name = getNameFromPath(path);
        Instant modificationDate;
        try {
            modificationDate = eventType == EventType.DELETE || fileType == FileType.DIR ? Instant.now() : Files.getLastModifiedTime(Paths.get(rootFolder + "/" + path)).toInstant();
        } catch (IOException e) {
            log.warn(e);
            modificationDate = Instant.MIN;
        }

        String filePath = path.toString().startsWith("/") ? path.toString().substring(1) : path.toString();

        KoxFile koxFile = KoxFile.builder()
                .fileType(fileType)
                .path(filePath)
                .id(filePath)
                .name(name)
                .source(this)
                .modificationDate(Date.from(modificationDate))
                .build();
        FileEvent fileEvent = FileEvent.builder()
                .eventType(eventType)
                .koxFile(koxFile)
                .timestamp(System.currentTimeMillis())
                .build();

        if (inProgressPaths.contains(getAbsolutePath(koxFile.getPath()))) {
            log.debug("File under processing");
            return;
        }

        synchronizationService.accept(fileEvent);

        if (fileType == FileType.DIR && eventType == EventType.CREATE)
            acceptSubdirectories(path);
    }

    private String getNameFromPath(@NonNull Path path) {
        return Arrays.stream(path.toString().split("/")).filter(p -> !p.isEmpty()).reduce((f, s) -> s).orElse(null);
    }

    private void acceptSubdirectories(Path path) {
        try {
            Path relativeStartPath = Paths.get(rootFolder + "/" + path);
            Files.walk(relativeStartPath).filter(s -> !s.equals(relativeStartPath)).map(filePath -> {
                String notAbsolutePath = filePath.toString().replace(rootFolder + "/", "");
                try {
                    return KoxFile.builder()
                            .fileType(filePath.toFile().isFile() ? FileType.REGULAR_FILE : FileType.DIR)
                            .path(notAbsolutePath)
                            .id(notAbsolutePath)
                            .name(getNameFromPath(filePath))
                            .source(this)
                            .modificationDate(new Date(Files.getLastModifiedTime(filePath).toMillis()))
                            .build();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }).map(koxFile -> FileEvent.builder()
                    .eventType(EventType.CREATE)
                    .koxFile(koxFile)
                    .timestamp(System.currentTimeMillis())
                    .build()
            ).forEach(synchronizationService::accept);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private boolean isFileNeededToUpdate(KoxFile koxFile) {
        if (koxFile.getFileType() == FileType.DIR)
            return true;

        Path absolutePath = getAbsolutePath(koxFile.getPath());

        if (Files.notExists(absolutePath))
            return true;

        Instant currentFileModificationTime;
        try {
            currentFileModificationTime = Files.getLastModifiedTime(absolutePath).toInstant();
        } catch (IOException e) {
            log.warn("Cannot take modification time", e);
            return true;
        }

        Instant changedFileModificationTime = koxFile.getModificationDate().toInstant();
        log.debug("changedFileModificationTime " + changedFileModificationTime);
        log.debug("currentFileModificationTime " + currentFileModificationTime);

        return currentFileModificationTime.plusSeconds(5).isBefore(changedFileModificationTime);
    }


    private void fileModified(KoxFile koxFile) {
        if (koxFile.getFileType() == FileType.REGULAR_FILE)
            saveRegularFile(koxFile);
        else
            saveDirectory(koxFile);
    }

    private void saveRegularFile(KoxFile koxFile) {
        log.info("Saving file: " + koxFile.getName());
        InputStream inputStream = koxFile.getSource().getInputStream(koxFile);

        if (inputStream != null) {
            Path absolutePath = getAbsolutePath(koxFile.getPath());
            inProgressPaths.add(absolutePath);
            try {
                Files.copy(inputStream, absolutePath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                log.warn(e);
            } finally {
                inProgressPaths.remove(absolutePath);
            }
        } else
            log.warn("Input stream is null!");
    }

    private void saveDirectory(KoxFile koxFile) {
        log.info("Saving directory: " + koxFile.getName());

        Path absolutePath = getAbsolutePath(koxFile.getPath());
        if (Files.exists(absolutePath)) {
            log.warn("Directory already exits " + koxFile);
            return;
        }

        inProgressPaths.add(absolutePath);
        try {
            Files.createDirectory(absolutePath);
        } catch (IOException e) {
            log.warn(e);
        } finally {
            inProgressPaths.remove(absolutePath);
        }
    }

    private void fileDeleted(KoxFile koxFile) {
        if (koxFile.getFileType() == FileType.REGULAR_FILE)
            deleteRegularFile(koxFile);
        else
            deleteDirectory(koxFile);
    }

    private void deleteRegularFile(KoxFile koxFile) {
        log.info("Removing file: " + koxFile);

        Path absolutePath = getAbsolutePath(koxFile.getPath());
        if (!absolutePath.startsWith(rootFolder.getAbsolutePath()))
            throw new RuntimeException("OMG!!!! Run away");

        inProgressPaths.add(absolutePath);
        boolean success = Optional.ofNullable(absolutePath.toFile()).map(File::delete).orElse(false);
        if (!success)
            log.warn("Couldn't delete file: " + koxFile);
        inProgressPaths.remove(absolutePath);
    }

    private void deleteDirectory(KoxFile koxFile) {
        log.info("Removing directory: " + koxFile);
        try {
            Path absolutePath = getAbsolutePath(koxFile.getPath());
            if (!absolutePath.startsWith(rootFolder.getAbsolutePath()))
                throw new RuntimeException("OMG!!!! Run away");
            inProgressPaths.add(absolutePath);
            FileUtils.deleteDirectory(absolutePath.toFile()); // VERY, VERY DANGEROUS!!!
            inProgressPaths.remove(absolutePath);
            recursiveWatcherService.directoryRemoved(absolutePath);
        } catch (IOException e) {
            log.warn(e);
        }
    }

    private Path getAbsolutePath(String path) {
        return Paths.get(rootFolder + "/" + path);
    }


}
