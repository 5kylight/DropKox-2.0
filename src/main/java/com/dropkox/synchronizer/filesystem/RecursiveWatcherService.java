package com.dropkox.synchronizer.filesystem;


import com.sun.nio.file.SensitivityWatchEventModifier;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static java.nio.file.StandardWatchEventKinds.*;

/* Source http://fabriziofortino.github.io/articles/recursive-watchservice-java8/ */

@Log
@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class RecursiveWatcherService {

    @NonNull
    private FilesystemSynchronizer filesystemSynchronizer;

    private WatchService watcher;
    private ExecutorService executor;
    private File rootFolder = new File("/home/tom/test/local");

    @PostConstruct
    public void init() throws IOException {
        watcher = FileSystems.getDefault().newWatchService();
        executor = Executors.newSingleThreadExecutor();
        startRecursiveWatcher();
    }

    @PreDestroy
    @SneakyThrows
    public void cleanup() {
        watcher.close();
        executor.shutdown();
    }

    @SuppressWarnings("unchecked")
    private void startRecursiveWatcher() throws IOException {
        final Map<WatchKey, Path> keys = new HashMap<>();

        Consumer<Path> register = p -> {
            if (!p.toFile().exists() || !p.toFile().isDirectory()) {
                throw new RuntimeException("folder " + p + " does not exist or is not a directory");
            }
            try {
                Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        log.info("Registering " + dir + " in watcher service");
                        WatchKey watchKey = dir.register(watcher, new WatchEvent.Kind[]{ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY}, SensitivityWatchEventModifier.HIGH);
                        keys.put(watchKey, dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }

        };

        register.accept(rootFolder.toPath());

        executor.submit(() -> {
            while (true) {
                final WatchKey key;
                try {
                    key = watcher.take(); // wait for a key to be available
                } catch (InterruptedException ex) {
                    return;
                }

                final Path dir = keys.get(key);
                if (dir == null) {
                    log.warning("WatchKey " + key + " not recognized!");
                    continue;
                }
                key.pollEvents().stream()
                        .filter(e -> (e.kind() != OVERFLOW))
                        .map(watchEvent -> (WatchEvent<Path>)watchEvent)
                        .forEach(watchEvent -> {
                            final Path absPath = dir.resolve(watchEvent.context());
                            filesystemSynchronizer.processFilesystemEvent(absPath, watchEvent.kind());
                            if (absPath.toFile().isDirectory()) {
                                register.accept(absPath);
                            }
                        });
                boolean valid = key.reset(); // IMPORTANT: The key must be reset after processed
                if (!valid) {
                    break;
                }
            }
        });
    }
}