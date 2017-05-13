package com.dropkox.synchronizer;

import com.dropkox.model.FileEvent;

public interface Synchronizer {
    void process(FileEvent fileEvent);
}
