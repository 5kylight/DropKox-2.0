package com.dropkox.synchronizer.drive;

import com.dropkox.model.EventType;
import com.dropkox.model.FileEvent;
import com.dropkox.model.FileType;
import com.dropkox.model.KoxFile;
import com.dropkox.synchronizer.SynchronizationService;
import com.dropkox.synchronizer.Synchronizer;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.Change;
import com.google.api.services.drive.model.ChangeList;
import com.google.api.services.drive.model.File;
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
import java.util.Date;

import static com.dropkox.model.FileType.DIR;

@Log
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@ToString(exclude = {"synchronizationService", "driveService", "savedStartPageToken", "rootName"})
public class GoogleDriveSynchronizer implements Synchronizer {

    @NonNull
    private SynchronizationService synchronizationService;
    @NonNull
    private Drive driveService;

    private String savedStartPageToken;
    private String rootName;

    @Override
    public void process(@NonNull final FileEvent fileEvent) {

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
    @SneakyThrows
    public void start() {
        synchronizationService.register(this);
        StartPageToken response = driveService.changes()
                .getStartPageToken().execute();

        log.info("Start token: " + response.getStartPageToken());
        savedStartPageToken = response.getStartPageToken();
    }

    @SneakyThrows
    @Async
    public void startListening() {
        String pageToken = savedStartPageToken;
        rootName = driveService.files().get("root").setFields("name").execute().getName();
        while (pageToken != null) {

            ChangeList changes = driveService.changes().list(pageToken)
                    .execute();
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

    @SneakyThrows
    @Async
    private void processChange(Change change) {
        log.info("Change found for file: " + change.getFileId());
        String filePath = getFilePath(change.getFile());
        FileEvent fileEvent = FileEvent.builder()
                .koxFile(KoxFile.builder()
                        .source(this)
                        .id(change.getFileId())
                        .fileType(resolveFileType(change.getType()))
                        .modificationDate(new Date(change.getTime().getValue()))
                        .name(change.getFile().getName())
                        .path(filePath)
                        .build())
                .eventType(resolveEventType(change.getType()))
                .timestamp(change.getTime().getValue())
                .build();

        synchronizationService.accept(fileEvent);
    }

    @SneakyThrows
    private String getFilePath(File startFile) {
        StringBuilder stringBuilder = new StringBuilder(startFile.getName());

        File file = driveService.files().get(startFile.getId()).setFields("id, parents, name").execute();
        while (file.getParents() != null) {
            String parentId = file.getParents().get(0); // PoC
            file = driveService.files().get(parentId).setFields("id, parents, name").execute();
            if (!file.getName().equals(rootName))
                stringBuilder.insert(0, file.getName() + "/");
        }

        return stringBuilder.toString();
    }

    private EventType resolveEventType(String type) {
        return EventType.CREATE;
    }

    private FileType resolveFileType(String mimeType) {
        return DIR;
    }


}
