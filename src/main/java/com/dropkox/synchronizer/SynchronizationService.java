package com.dropkox.synchronizer;

import com.dropkox.model.FileEvent;
import lombok.NonNull;
import lombok.extern.log4j.Log4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Log4j
@Service
public class SynchronizationService {

    private Set<ISynchronizer> synchronizers = new HashSet<>();

    public void register(@NonNull final ISynchronizer synchronizer) {
        log.debug("Registering " + synchronizer);
        this.synchronizers.add(synchronizer);
    }

    @Async
    public void accept(@NonNull final FileEvent fileEvent) {
        log.info("Received: " + fileEvent);
        this.synchronizers.stream().filter(s -> s != fileEvent.getKoxFile().getSource()).forEach(p -> p.process(fileEvent));
    }


}
