package com.forgebrain.backend.repositories;

import com.forgebrain.backend.entities.TopicEntity;
import java.util.List;
import java.util.Optional;

public interface TopicRepository {

    Optional<TopicEntity> findByTopicId(String topicId);

    List<TopicEntity> findAll();

    TopicEntity save(TopicEntity entity);
}
