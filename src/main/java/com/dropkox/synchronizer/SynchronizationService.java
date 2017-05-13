package com.dropkox.synchronizer;

import com.dropkox.model.FileEvent;
import lombok.NonNull;
import lombok.extern.java.Log;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Log
@Service
public class SynchronizationService {

    private Set<Synchronizer> synchronizers = new HashSet<>();

    public void register(Synchronizer synchronizer) {
        this.synchronizers.add(synchronizer);
    }

    public void accept(@NonNull final FileEvent fileEvent) {
        log.info("Received: " + fileEvent);
        this.synchronizers.stream().filter(s -> s != fileEvent.getKoxFile().getSource()).forEach(p -> p.process(fileEvent));
    }


}
