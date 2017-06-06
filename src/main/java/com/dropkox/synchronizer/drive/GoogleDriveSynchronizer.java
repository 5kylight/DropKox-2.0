package com.dropkox.synchronizer.drive;

import com.dropkox.model.EventType;
import com.dropkox.model.FileEvent;
import com.dropkox.model.FileType;
import com.dropkox.model.KoxFile;
import com.dropkox.synchronizer.ISynchronizer;
import com.dropkox.synchronizer.SynchronizationService;
import com.google.api.services.drive.model.Change;
import com.google.api.services.drive.model.File;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.log4j.Log4j;
import net.jodah.expiringmap.ExpiringMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.dropkox.model.FileType.DIR;
import static com.dropkox.model.FileType.REGULAR_FILE;
import static javaslang.API.$;
import static javaslang.API.Case;
import static javaslang.API.Match;
import static javaslang.API.run;
import static javaslang.Predicates.is;
import static javaslang.Predicates.isIn;

@Log4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@ToString(exclude = {"synchronizationService", "driveService"})
public class GoogleDriveSynchronizer implements ISynchronizer {

    @NonNull
    private SynchronizationService synchronizationService;
    @NonNull
    private GoogleDriveService driveService;

    private final Map<String, Object> recentUpdates = ExpiringMap.builder().expiration(5, TimeUnit.SECONDS).build();

    @Override
    public synchronized void process(@NonNull final FileEvent fileEvent) {
        recentUpdates.put(fileEvent.getKoxFile().getPath(), new Object());

        KoxFile koxFile = fileEvent.getKoxFile();
        if (!isFileNeededToUpdate(koxFile)) {
            log.warn("Ignoring change: " + fileEvent);
            return;
        }
        Match(fileEvent.getEventType()).of(
                Case($(isIn(EventType.CREATE, EventType.MODIFY)), o -> run(() -> fileModified(fileEvent.getKoxFile()))),
                Case(is(EventType.DELETE), o -> run(() -> fileDeleted(fileEvent.getKoxFile()))),
                Case($(), o -> run(() -> {
                    throw new UnsupportedOperationException("Event type not supported yet! " + fileEvent.getEventType());
                }))
        );
    }


    @Override
    public InputStream getInputStream(@NonNull final KoxFile koxFile) {
        return driveService.getInputStream(koxFile.getId());
    }

    @PostConstruct
    public void start() {
        synchronizationService.register(this);

    }

    @Async
    public void startListening() {
        List<Change> changes;
        while ((changes = driveService.getChanges()) != null) {
            for (Change change : changes) {
                processChange(change);
            }
            log.trace("PING");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.warn(e);
            }
        }
    }

    @Async
    private void processChange(Change change) {
        if (change.getFile() != null) {
            String filePath = getFilePath(change.getFile());
            if (recentUpdates.containsKey(filePath)) {
                log.debug("Skipping recent update");
                return;
            }
            log.info("Change found for file: " + change.getFile().getName());
            FileEvent fileEvent = FileEvent.builder()
                    .koxFile(KoxFile.builder()
                            .source(this)
                            .id(change.getFileId())
                            .fileType(resolveFileType(change.getFile().getMimeType()))
                            .modificationDate(new Date(change.getTime().getValue()))
                            .name(change.getFile().getName())
                            .path(filePath)
                            .build())
                    .eventType(resolveEventType(change))
                    .timestamp(change.getTime().getValue())
                    .build();

            synchronizationService.accept(fileEvent);
        } else log.warn(String.format("File with ID: %s do not exists!", change.getFileId()));
    }

    private boolean isFileNeededToUpdate(KoxFile koxFile) {
        if (koxFile.getFileType() == DIR)
            return true;

        String currentFileId = driveService.getId(koxFile.getName(), koxFile.getPath());
        if (currentFileId == null)
            return true;

        Instant currentFileModificationTime = driveService.getModificationDate(currentFileId);
        Instant changedFileModificationTime = koxFile.getModificationDate().toInstant();

        log.info("changedFileModificationTime " + changedFileModificationTime);
        log.info("currentFileModificationTime " + currentFileModificationTime);

        return currentFileModificationTime.plusSeconds(5).isBefore(changedFileModificationTime);
    }

    private void fileDeleted(KoxFile koxFile) {
        log.info("Removing file " + koxFile);
        String fileId = driveService.getId(koxFile.getName(), koxFile.getPath());
        if (fileId != null)
            driveService.delete(fileId);
        else
            log.warn("File do not exists " + koxFile);
    }

    private void fileModified(KoxFile koxFile) {
        if (koxFile.getFileType() == REGULAR_FILE) {
            sendRegularFile(koxFile);
        } else {
            sendDirectoryRecursive(koxFile);
        }
    }

    private void sendRegularFile(KoxFile koxFile) {
        log.info("Sending file: " + koxFile.getName());
        String actualId = driveService.getId(koxFile.getName(), koxFile.getPath());
        if (actualId != null) {
            log.debug("Updating file: " + koxFile.getName());
            driveService.delete(actualId);
        }

        InputStream inputStream = koxFile.getSource().getInputStream(koxFile);
        String filePath = koxFile.getPath();
        String[] filePathSplited = filePath.split("/");
        String parentId = "root";
        if (filePathSplited.length > 1) {
            String parentName = filePathSplited[filePathSplited.length - 2]; //
            String parentPath = filePath.substring(0, filePath.lastIndexOf('/'));
            String currentParentId = driveService.getId(parentName, parentPath);
            if (currentParentId == null) {
                parentId = createDirectoryRecursive(parentPath);
            } else {
                parentId = currentParentId;
            }
        }

        driveService.createFile(koxFile.getName(), parentId, inputStream);
    }

    private void sendDirectoryRecursive(KoxFile koxFile) {
        log.info("Sending directory: " + koxFile.getName());
        if (driveService.getId(koxFile.getName(), koxFile.getPath()) != null) {
            log.warn("Directory already exits");
            return;
        }

        createDirectoryRecursive(koxFile.getPath());
    }

    private String createDirectoryRecursive(String path) {
        List<String> fileNames = Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        String parentId = "root";
        StringBuilder currentPath = new StringBuilder(fileNames.get(0));
        for (String fileName : fileNames) {
            String driveId = driveService.getId(fileName, currentPath.toString());

            if (driveId == null) {
                parentId = driveService.createDirectory(parentId, fileName);
            } else {
                parentId = driveId;
            }
            currentPath.append("/").append(fileName);
        }
        return parentId;
    }
    private String getFilePath(File file) {
        return driveService.getFilePath(file.getId(), file.getName());
    }

    private EventType resolveEventType(Change change) {
        return driveService.isTrashed(change.getFileId()) ? EventType.DELETE : EventType.MODIFY;
    }

    private FileType resolveFileType(String mimeType) {
        return mimeType.equals("application/vnd.google-apps.folder") ? DIR : REGULAR_FILE;
    }

}
