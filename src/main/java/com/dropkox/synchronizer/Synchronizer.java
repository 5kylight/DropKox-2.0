package com.dropkox.synchronizer;

import com.dropkox.model.FileEvent;
import com.dropkox.model.KoxFile;

import java.io.InputStream;

public interface Synchronizer {
    void process(FileEvent fileEvent);
    InputStream getInputStream(KoxFile koxFile);
}
