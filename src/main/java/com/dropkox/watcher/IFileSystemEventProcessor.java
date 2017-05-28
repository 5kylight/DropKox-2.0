package com.dropkox.watcher;

import com.dropkox.model.EventType;
import com.dropkox.model.FileType;
import lombok.NonNull;

import java.nio.file.Path;

public interface IFileSystemEventProcessor {
    void processFilesystemEvent(@NonNull Path path, @NonNull EventType eventType, @NonNull FileType fileType);
}
