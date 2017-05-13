package com.dropkox.synchronizer;

import com.dropkox.model.file.File;
import lombok.NonNull;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class SynchronizerService {

    private Set<Synchronizer> synchronizers = new HashSet<>();

    public void register(Synchronizer synchronizer) {
        this.synchronizers.add(synchronizer);
    }

    public void accept(@NonNull final File file, @NonNull final Synchronizer synchronizer){
        this.synchronizers.stream().filter(s-> s != synchronizer).forEach(p -> p.process(file));
    }






}
