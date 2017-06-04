package com.dropkox.categorizer.filemanager;

import com.dropkox.categorizer.filemanager.strategies.FileTransferringHandler;
import com.dropkox.categorizer.service.CategoryRetriever;
import com.dropkox.categorizer.service.ImageCategorizationService;
import com.dropkox.categorizer.suppliers.UrlType;
import com.dropkox.core.exceptions.NoLabelsAssignedException;
import com.dropkox.model.EventType;
import com.dropkox.model.FileType;
import com.dropkox.model.ImageLabel;
import com.dropkox.watcher.IFileSystemEventProcessor;
import com.dropkox.watcher.RecursiveWatcherService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@ToString(exclude = "categorizationFileManagingService")
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class CategorizationFileManagingService implements IFileSystemEventProcessor {

    private static String DEFAULT_CATEGORY_NAME = "other";
    private static List<String> existingCategories = new LinkedList<>();

    private FileTransferringHandler fileTransferringHandler;

    private File inputFolder;
    private File outputFolder;

    @Autowired
    private CategorizationFolderProperties categorizationFolderProperties;

    @NonNull
    private ImageCategorizationService imageCategorizationService;

    private RecursiveWatcherService recursiveWatcherService;
    private CategoryRetriever categoryRetriever;

    @PostConstruct
    public void start() {
        this.inputFolder = new File(categorizationFolderProperties.getInputFolder());
        this.outputFolder = new File(categorizationFolderProperties.getOutputFolder());

        if (!isFolderCorrect(inputFolder)) {
            inputFolder.mkdirs();
        }
        readExistingCategories();
        recursiveWatcherService = new RecursiveWatcherService(this, inputFolder);
        categoryRetriever = new CategoryRetriever();

        try {
            recursiveWatcherService.init();
        } catch (IOException e) {
            e.printStackTrace();
        }

        fileTransferringHandler = new FileTransferringHandler(inputFolder, outputFolder);
    }

    private void readExistingCategories() {
        if (isFolderCorrect(outputFolder)) {
            List<String> existingCategories = getNearestSubfolderNames(outputFolder).stream().map(String::toLowerCase).collect(Collectors.toList());
            CategorizationFileManagingService.existingCategories.addAll(existingCategories);
        }
    }

    private List<? extends String> getNearestSubfolderNames(File outputFolder) {
        return Stream.of(outputFolder.listFiles())
                    .filter(File::isDirectory)
                    .map(File::getName)
                    .collect(Collectors.toList());
    }

    private boolean isFolderCorrect(File file) {
        return  file != null && file.exists() && file.isDirectory();
    }

    @Override
    public void processFilesystemEvent(@NonNull final Path path, @NonNull final EventType eventType, @NonNull final FileType fileType) {
        if (EventType.CREATE.equals(eventType) && !FileType.DIR.equals(fileType)) {

            String fileName = path.getFileName().toString();
            try {
                String imgPath = inputFolder.getAbsolutePath().concat(File.separator).concat(fileName);
                List<ImageLabel> labels = imageCategorizationService.getLabelsForImage(imgPath, UrlType.LOCAL);

                String outputFolderName = categoryRetriever.getCategoryName(existingCategories, labels);
                fileTransferringHandler.moveFile(fileName, outputFolderName);

            } catch (NoLabelsAssignedException e) {
                fileTransferringHandler.moveFile(fileName, DEFAULT_CATEGORY_NAME);
            }
        }
    }
}
