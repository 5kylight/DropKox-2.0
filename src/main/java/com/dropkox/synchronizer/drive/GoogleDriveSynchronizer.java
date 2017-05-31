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
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static com.dropkox.model.FileType.DIR;
import static com.dropkox.model.FileType.REGULAR_FILE;
import static javaslang.API.$;
import static javaslang.API.Case;
import static javaslang.API.Match;
import static javaslang.API.run;
import static javaslang.Predicates.is;
import static javaslang.Predicates.isIn;

@Log
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@ToString(exclude = {"synchronizationService", "driveService"})
public class GoogleDriveSynchronizer implements ISynchronizer {

    @NonNull
    private SynchronizationService synchronizationService;
    @NonNull
    private GoogleDriveService driveService;


    @Async
    @Override
    public void process(@NonNull final FileEvent fileEvent) {
        KoxFile koxFile = fileEvent.getKoxFile();
        if (!isFileNeededToUpdate(koxFile)) {
            log.warning("Ignoring change: " + fileEvent);
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

    private boolean isFileNeededToUpdate(KoxFile koxFile) {
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
            log.warning("File do not exists " + koxFile);
    }


    @SneakyThrows(IOException.class)
    private void fileModified(KoxFile koxFile) {
        if (koxFile.getFileType() == REGULAR_FILE) {
            sendRegularFile(koxFile);
        } else {
            sendDirectoryRecursive(koxFile);
        }
    }


    private void sendRegularFile(KoxFile koxFile) throws IOException {
        log.info("Sending file: " + koxFile.getName());
        InputStream inputStream = koxFile.getSource().getInputStream(koxFile);
        driveService.create(koxFile.getName(), inputStream);
    }

    private void sendDirectoryRecursive(KoxFile koxFile) throws IOException {
        log.info("Sending directory: " + koxFile.getName());
        if (driveService.getId(koxFile.getName(), koxFile.getPath()) != null) {
            log.warning("Directory already exits");
            return;
        }

        List<String> fileNames = Arrays.stream(koxFile.getPath().split("/")).filter(s -> !s.isEmpty()).collect(Collectors.toList());
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
    }


    @Override
    public InputStream getInputStream(@NonNull final KoxFile koxFile) {
        return driveService.getInputStream(koxFile.getId());
    }

    @PostConstruct
    public void start() {
        synchronizationService.register(this);

    }

    @SneakyThrows(value = InterruptedException.class)
    @Async
    public void startListening() {
        List<Change> changes;
        while ((changes = driveService.getChanges()) != null) {
            for (Change change : changes) {
                processChange(change);
            }
            log.warning("PING");
            Thread.sleep(1000);
        }
    }

    @Async
    private void processChange(Change change) {
        log.info("Change found for file: " + change.getFileId());
        if (change.getFile() != null) {
            String filePath = getFilePath(change.getFile());
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
        } else log.warning(String.format("File with ID: %s do not exists!", change.getFileId()));
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
