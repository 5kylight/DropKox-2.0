package com.dropkox.synchronizer.drive;


import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.GenericJson;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveRequest;
import com.google.api.services.drive.model.Change;
import com.google.api.services.drive.model.ChangeList;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.StartPageToken;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Log4j
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class GoogleDriveService {
    private static final String USER_RATE_LIMIT_EXCEEDED = "User Rate Limit Exceeded";
    private static final int FORBIDDEN_CODE = 403;

    @NonNull
    private Drive driveService;

    private String rootName;
    private String savedStartPageToken;

    @PostConstruct
    private void init() {
        try {
            rootName = ((File) executeRequest(driveService.files().get("root").setFields("name"))).getName();
        } catch (IOException e) {
            log.warn(e.getMessage());
            rootName = "root";
        }
        try {
            StartPageToken response = (StartPageToken) executeRequest(driveService.changes()
                    .getStartPageToken());
            log.debug("Start token: " + response.getStartPageToken());
            savedStartPageToken = response.getStartPageToken();
        } catch (IOException e) {
            log.warn(e.getMessage());
        }

    }

    List<Change> getChanges() {
        String pageToken = savedStartPageToken;
        List<Change> resultChanges = new ArrayList<>();
        while (pageToken != null) {
            ChangeList changes;
            try {
                changes = (ChangeList) executeRequest(driveService.changes().list(savedStartPageToken));
            } catch (IOException e) {
                log.warn(e.getMessage());
                return null;
            }

            resultChanges.addAll(changes.getChanges());
            if (changes.getNewStartPageToken() != null) {
                savedStartPageToken = changes.getNewStartPageToken();
            }
            pageToken = changes.getNextPageToken();
        }

        return resultChanges;
    }


    synchronized Instant getModificationDate(@NonNull final String fileId) {
        File currentFile;
        try {
            currentFile = (File) executeRequest(driveService.files().get(fileId).setFields("modifiedTime"));
        } catch (IOException e) {
            log.warn(e.getMessage());
            return null;
        }
        return Instant.ofEpochMilli(currentFile.getModifiedTime().getValue());
    }


    @SneakyThrows(IOException.class)
    synchronized String getId(@NonNull final String name, @NonNull final String path) {
        FileList result = (FileList) executeRequest(driveService.files().list()
                .setQ(String.format("trashed = false and name='%s'", name)));

        return result.getFiles().stream()
                .map(File::getId)
                .filter(id -> getFilePath(id, name).equals(path))
                .findAny()
                .orElse(null);
    }


    @SneakyThrows(IOException.class)
    synchronized String getFilePath(@NonNull final String startFileId, @NonNull final String fileName) {
        StringBuilder stringBuilder = new StringBuilder(fileName);

        File file = (File) executeRequest(driveService.files().get(startFileId).setFields("id, parents, name"));
        while (file.getParents() != null) {
            String parentId = file.getParents().get(0); // PoC
            file = (File) executeRequest(driveService.files().get(parentId).setFields("id, parents, name"));
            if (!file.getName().equals(rootName))
                stringBuilder.insert(0, file.getName() + "/");
        }

        return stringBuilder.toString();
    }

    @SneakyThrows(IOException.class)
    synchronized Boolean isTrashed(@NonNull final String fileId) {
        File file = (File) executeRequest(driveService.files().get(fileId).setFields("trashed"));
        return file.getTrashed();
    }

    synchronized  void delete(@NonNull final String fileId) {
        try {
            executeRequest(driveService.files().delete(fileId));
        } catch (IOException e) {
            log.warn(e.getMessage());
        }
    }

    synchronized void createFile(@NonNull final String name, @NonNull final String parentId, @NonNull final InputStream inputStream) {
        File fileMetadata = new File();
        fileMetadata.setParents(Collections.singletonList(parentId));
        fileMetadata.setName(name);
        // TODO: resolve MimeType
        InputStreamContent inputStreamContent = new InputStreamContent("text/plain", inputStream);

        try {
            executeRequest(driveService.files().create(fileMetadata, inputStreamContent));
        } catch (IOException e) {
            log.warn(e);
        }
    }

    synchronized String createDirectory(@NonNull final String parentId, @NonNull final String fileName) {
        File fileMetadata = new File();
        fileMetadata.setName(fileName);
        fileMetadata.setParents(Collections.singletonList(parentId));
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        try {
            return ((File) executeRequest(driveService.files().create(fileMetadata).setFields("id"))).getId();
        } catch (IOException e) {
            log.warn(e.getMessage());
            return null;
        }
    }

    InputStream getInputStream(@NonNull final String fileId) {
        try {
            return driveService.files().get(fileId).executeMediaAsInputStream();
        } catch (IOException e) {
            log.warn(e.getMessage());
            return null;
        }
    }


    private GenericJson executeRequest(DriveRequest driveRequest) throws IOException {
        return executeRequestExpBackoff(driveRequest, 0);
    }

    private GenericJson executeRequestExpBackoff(DriveRequest driveRequest, double delayS) throws IOException {
        try {
            return (GenericJson) driveRequest.execute();
        } catch (GoogleJsonResponseException e) {
            if (e.getDetails().getMessage().equals(USER_RATE_LIMIT_EXCEEDED) && e.getDetails().getCode() == FORBIDDEN_CODE) {
                try {
                    Thread.sleep((long) (1000 * delayS));
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
                return executeRequestExpBackoff(driveRequest, Math.pow(2, delayS + 1));
            }
            throw e;
        }
    }

}
