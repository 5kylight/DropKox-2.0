package com.dropkox.categorizer.filemanager.strategies;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@RequiredArgsConstructor
public class FileTransferringHandler {

    private static final int MAX_ALLOWED_DUPLIACATION_COUNT = 1000;

    @NonNull
    private File inputFolder;
    @NonNull
    private File outputFolder;

    /**
     * Moves file from inputFolder to outputFolder/subFolderName.
     * In case the file already exists, the postfix is added, e.g. car.jpg is changed to car-1.jpg.
     *
     * @param fileName name of the file to be moved
     * @param subFolderName name of folder in outputFolder, into which the file from first param will be moved.
     */
    public void moveFile(String fileName, String subFolderName) {
        moveFile(fileName, subFolderName, 0);
    }

    private void moveFile(String fileName, String subFolderName, int attemptNumber) {
        Path inputFilePath = Paths.get(pathOf(inputFolder, fileName));
        try {
            File outputCategoryFolder = getOrCreateSubfolder(outputFolder, subFolderName);
            Path outputFilePath = Paths.get(pathOf(outputCategoryFolder, getNumberedFileName(fileName, attemptNumber)));
            Files.move(inputFilePath, outputFilePath);
        } catch (FileAlreadyExistsException e) {
            if (attemptNumber < MAX_ALLOWED_DUPLIACATION_COUNT) {
                moveFile(fileName, subFolderName, attemptNumber + 1);
            } else {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getNumberedFileName(String fileName, int attemptNumber) {
        String[] nameElements = fileName.split("\\.");
        if (nameElements.length < 2) {
            throw new IllegalArgumentException("Filename must have an extension.");
        } else {
            nameElements[nameElements.length - 2] = nameElements[nameElements.length - 2] + "-" + attemptNumber;
            return String.join(".", nameElements);
        }
    }

    private File getOrCreateSubfolder(File baseFolder, String subfolderName) {
        String outputFolderPath = pathOf(baseFolder, subfolderName);
        File subfolder = new File(outputFolderPath);
        if (!subfolder.exists()) {
            subfolder.mkdirs();
        }
        return subfolder;
    }


    private String pathOf(File baseFolder, String... following) {
        StringBuilder sb = new StringBuilder(baseFolder.getAbsolutePath().concat(File.separator));
        Stream.of(following).forEach(s -> sb.append(File.separator).append(s));
        return sb.toString();
    }
}
