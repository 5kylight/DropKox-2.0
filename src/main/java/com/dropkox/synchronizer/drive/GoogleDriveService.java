package com.dropkox.synchronizer.drive;


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
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Log
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class GoogleDriveService {
    @NonNull
    private Drive driveService;

    private String rootName;
    private String savedStartPageToken;

    @PostConstruct
    private void init() {
        try {
            rootName = driveService.files().get("root").setFields("name").execute().getName();
        } catch (IOException e) {
            log.warning(e.getMessage());
            rootName = "root";
        }
        try {
            StartPageToken response = driveService.changes()
                    .getStartPageToken().execute();
            log.info("Start token: " + response.getStartPageToken());
            savedStartPageToken = response.getStartPageToken();
        } catch (IOException e) {
            log.warning(e.getMessage());
        }

    }

    List<Change> getChanges() {
        String pageToken = savedStartPageToken;
        List<Change> resultChanges = new ArrayList<>();
        while (pageToken != null) {
            ChangeList changes;
            try {
                changes = driveService.changes().list(savedStartPageToken).execute();
            } catch (IOException e) {
                log.warning(e.getMessage());
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


    Instant getModificationDate(@NonNull final String fileId) {
        File currentFile;
        try {
            currentFile = driveService.files().get(fileId).setFields("modifiedTime").execute();
        } catch (IOException e) {
            log.warning(e.getMessage());
            return null;
        }
        return Instant.ofEpochMilli(currentFile.getModifiedTime().getValue());
    }


    @SneakyThrows(IOException.class)
    String getId(@NonNull final String name, @NonNull final String path) {
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
    String getFilePath(@NonNull final String startFileId, @NonNull final String fileName) {
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
    Boolean isTrashed(@NonNull final String fileId) {
        File file = driveService.files().get(fileId).setFields("trashed").execute();
        return file.getTrashed();
    }

    void delete(@NonNull final String fileId) {
        try {
            driveService.files().delete(fileId).execute();
        } catch (IOException e) {
            log.warning(e.getMessage());
        }
    }

    void create(@NonNull final String name, @NonNull final InputStream inputStream) {
        File fileMetadata = new File();
        fileMetadata.setName(name); // TODO: set parent directory
        InputStreamContent inputStreamContent = new InputStreamContent("text/plain", inputStream);

        try {
            driveService.files().create(fileMetadata, inputStreamContent)
                    .execute();
        } catch (IOException e) {
            log.warning(e.getMessage());
        }
    }

    String createDirectory(@NonNull final String parentId, @NonNull final String fileName) {
        File fileMetadata = new File();
        fileMetadata.setName(fileName);
        fileMetadata.setParents(Collections.singletonList(parentId));
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        try {
            return driveService.files().create(fileMetadata).setFields("id").execute().getId();
        } catch (IOException e) {
            log.warning(e.getMessage());
            return null;
        }
    }

    InputStream getInputStream(@NonNull final String fileId) {
        try {
            return driveService.files().get(fileId).executeMediaAsInputStream();
        } catch (IOException e) {
            log.warning(e.getMessage());
            return null;
        }
    }


}
