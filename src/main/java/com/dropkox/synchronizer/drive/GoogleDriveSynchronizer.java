package com.dropkox.synchronizer.drive;

import com.dropkox.model.EventType;
import com.dropkox.model.FileEvent;
import com.dropkox.model.FileType;
import com.dropkox.model.KoxFile;
import com.dropkox.synchronizer.ISynchronizer;
import com.dropkox.synchronizer.SynchronizationService;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.Change;
import com.google.api.services.drive.model.ChangeList;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.StartPageToken;
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
import java.util.Collections;
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
@ToString(exclude = {"synchronizationService", "driveService", "savedStartPageToken", "rootName"})
public class GoogleDriveSynchronizer implements ISynchronizer {

    @NonNull
    private SynchronizationService synchronizationService;
    @NonNull
    private Drive driveService;

    private String savedStartPageToken;
    private String rootName;

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

    @SneakyThrows(IOException.class)
    private boolean isFileNeededToUpdate(KoxFile koxFile) {
        String currentFileId = getDriveId(koxFile.getName(), koxFile.getPath());
        if (currentFileId == null)
            return true;

        File currentFile = driveService.files().get(currentFileId).setFields("modifiedTime").execute();

        Instant changedFileModificationTime = koxFile.getModificationDate().toInstant();
        Instant currentFileModificationTime = Instant.ofEpochMilli(currentFile.getModifiedTime().getValue());

        log.info("changedFileModificationTime " + changedFileModificationTime);
        log.info("currentFileModificationTime " + currentFileModificationTime);

        return currentFileModificationTime.plusSeconds(5).isBefore(changedFileModificationTime);
    }

    @SneakyThrows(IOException.class)
    private void fileDeleted(KoxFile koxFile) {
        log.info("Removing file " + koxFile);
        String fileId = getDriveId(koxFile.getName(), koxFile.getPath());
        if (fileId != null)
            driveService.files().delete(fileId).execute();
        else
            log.warning("File do not exists " + koxFile);
    }


    @SneakyThrows(IOException.class)
    private String getDriveId(String name, String path) {
        FileList result = driveService.files().list()
                .setQ(String.format("trashed = false and name='%s'", name))
                .execute();

        return result.getFiles().stream()
                .map(File::getId)
                .filter(id -> getFilePath(id, name).equals(path))
                .findAny()
                .orElse(null);
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
        File fileMetadata = new File();
        fileMetadata.setName(koxFile.getName()); // TODO: set parent directory
        InputStream inputStream = koxFile.getSource().getInputStream(koxFile);
        InputStreamContent inputStreamContent = new InputStreamContent("text/plain", inputStream);
        driveService.files().create(fileMetadata, inputStreamContent)
                .execute();
    }

    private void sendDirectoryRecursive(KoxFile koxFile) throws IOException {
        log.info("Sending directory: " + koxFile.getName());
        if (getDriveId(koxFile.getName(), koxFile.getPath()) != null) {
            log.warning("Directory already exits");
            return;
        }

        List<String> fileNames = Arrays.stream(koxFile.getPath().split("/")).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        String parentId = "root";
        StringBuilder currentPath = new StringBuilder(fileNames.get(0));
        for (String fileName : fileNames) {
            String driveId = getDriveId(fileName, currentPath.toString());

            if (driveId == null) {
                File fileMetadata = new File();
                fileMetadata.setName(fileName);
                fileMetadata.setParents(Collections.singletonList(parentId));
                fileMetadata.setMimeType("application/vnd.google-apps.folder");
                File createdFile = driveService.files().create(fileMetadata).setFields("id").execute();
                parentId = createdFile.getId();
            } else {
                parentId = driveId;
            }


            currentPath.append("/").append(fileName);
        }
    }


    @Override
    public InputStream getInputStream(@NonNull final KoxFile koxFile) {
        try {
            return driveService.files().get(koxFile.getId()).executeMediaAsInputStream();
        } catch (IOException e) {
            log.warning(e.getMessage());
            return null;
        }
    }

    @PostConstruct
    @SneakyThrows(IOException.class)
    public void start() {
        synchronizationService.register(this);
        StartPageToken response = driveService.changes()
                .getStartPageToken().execute();

        log.info("Start token: " + response.getStartPageToken());
        savedStartPageToken = response.getStartPageToken();
    }

    @SneakyThrows(value = {IOException.class, InterruptedException.class})
    @Async
    public void startListening() {
        String pageToken = savedStartPageToken;
        rootName = driveService.files().get("root").setFields("name").execute().getName();
        while (pageToken != null) {

            ChangeList changes = driveService.changes().list(pageToken).execute();
            for (Change change : changes.getChanges()) {
                processChange(change);
            }
            if (changes.getNewStartPageToken() != null) {
                savedStartPageToken = changes.getNewStartPageToken();
            }
            pageToken = changes.getNextPageToken();
        }

        Thread.sleep(1000);
        startListening();
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
        return getFilePath(file.getId(), file.getName());
    }

    @SneakyThrows(IOException.class)
    private String getFilePath(String startFileId, String fileName) {
        StringBuilder stringBuilder = new StringBuilder(fileName);

        File file = driveService.files().get(startFileId).setFields("id, parents, name").execute();
        while (file.getParents() != null) {
            String parentId = file.getParents().get(0); // PoC
            file = driveService.files().get(parentId).setFields("id, parents, name").execute();
            if (!file.getName().equals(rootName))
                stringBuilder.insert(0, file.getName() + "/");
        }

        return stringBuilder.toString();
    }

    @SneakyThrows(IOException.class)
    private EventType resolveEventType(Change change) {
        File file = driveService.files().get(change.getFileId()).setFields("trashed").execute();
        return file.getTrashed() ? EventType.DELETE : EventType.MODIFY;
    }

    private FileType resolveFileType(String mimeType) {
        return mimeType.equals("application/vnd.google-apps.folder") ? DIR : REGULAR_FILE;
    }


}
