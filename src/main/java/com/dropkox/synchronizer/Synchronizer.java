package com.dropkox.synchronizer;

import com.dropkox.model.file.File;

public interface Synchronizer {
    void process(File file);
}
