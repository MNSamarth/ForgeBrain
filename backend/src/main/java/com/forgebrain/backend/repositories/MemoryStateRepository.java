package com.forgebrain.backend.repositories;

import com.forgebrain.backend.entities.MemoryStateEntity;
import java.util.Optional;

public interface MemoryStateRepository {

    Optional<MemoryStateEntity> findById(String documentId);

    MemoryStateEntity save(MemoryStateEntity entity);
}
