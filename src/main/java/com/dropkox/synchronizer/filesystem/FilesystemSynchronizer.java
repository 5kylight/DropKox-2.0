package com.dropkox.synchronizer.filesystem;

import com.dropkox.model.EventType;
import com.dropkox.model.FileEvent;
import com.dropkox.model.FileType;
import com.dropkox.model.KoxFile;
import com.dropkox.model.file.File;
import com.dropkox.synchronizer.Synchronizer;
import com.dropkox.synchronizer.SynchronizerService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.nio.file.Path;
import java.nio.file.WatchEvent;

import static com.dropkox.model.FileType.DIR;
import static com.dropkox.model.FileType.REGULAR;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;

@Log
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
@ToString(exclude = "synchronizerService")
public class FilesystemSynchronizer implements Synchronizer {

    @NonNull
    private SynchronizerService synchronizerService;

    @PostConstruct
    public void start() {
        synchronizerService.register(this);
    }

    public void startListening() {

    }

    void processFilesystemEvent(Path path, WatchEvent.Kind<Path> pathKind) {

        FileEvent fileEvent = FileEvent.builder()
                .eventType(resolveEventType(pathKind))
                .koxFile(KoxFile.builder().fileType(resolveFileType(path)).id(path.toAbsolutePath().toString()).source(this).build())
                .timestamp(System.currentTimeMillis())
                .build();

        log.info("Received " + fileEvent);
    }

    private FileType resolveFileType(Path path) {
        return path.toFile().isDirectory() ? DIR : REGULAR;
    }

    private EventType resolveEventType(WatchEvent.Kind<Path> kind) {
        if (kind == ENTRY_CREATE)
            return EventType.CREATE;
        if (kind == ENTRY_DELETE)
            return EventType.DELETE;
        return EventType.MODIFY;
    }


    @Override
    public void process(File file) {

    }
}
