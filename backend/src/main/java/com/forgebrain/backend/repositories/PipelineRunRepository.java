package com.forgebrain.backend.repositories;

import com.forgebrain.backend.entities.PipelineRunEntity;
import java.util.Optional;

public interface PipelineRunRepository {

    Optional<PipelineRunEntity> findByTopicId(String topicId);

    PipelineRunEntity save(PipelineRunEntity entity);
}
