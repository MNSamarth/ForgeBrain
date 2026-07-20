package com.forgebrain.backend.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forgebrain.backend.config.JobStorageConfig;
import com.forgebrain.backend.exceptions.ConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * File-based {@link ReelJobRepository}: one {@code <jobId>.json} file per job under {@link
 * JobStorageConfig#jobsDirectory()}, overwritten on every {@link #update}. The simplest real
 * durable storage that fits this repo today — matches the exact convention already established
 * by {@link com.forgebrain.backend.pipeline.ReportWriterImpl} and {@link
 * com.forgebrain.backend.pipeline.ReelExportReportWriterImpl} (one JSON file per record via the
 * shared snake_case {@code ObjectMapper}) rather than introducing a database dependency this
 * project doesn't otherwise have.
 */
@Component
public class LocalFileReelJobRepository implements ReelJobRepository {

    private final ObjectMapper objectMapper;
    private final File jobsDirectory;

    public LocalFileReelJobRepository(ObjectMapper objectMapper, JobStorageConfig jobStorageConfig) {
        this.objectMapper = objectMapper;
        this.jobsDirectory = new File(jobStorageConfig.jobsDirectory());
    }

    @Override
    public ReelJob create(ReelJob job) {
        return persist(job);
    }

    @Override
    public ReelJob update(ReelJob job) {
        return persist(job);
    }

    @Override
    public Optional<ReelJob> findById(String jobId) {
        File file = jobFile(jobId);
        if (!file.isFile()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(file, ReelJob.class));
        } catch (IOException e) {
            throw new ConfigurationException("Failed to read job record from " + file.getAbsolutePath(), e);
        }
    }

    @Override
    public List<ReelJob> findAll() {
        File[] files = jobsDirectory.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) {
            return List.of();
        }
        List<ReelJob> jobs = new ArrayList<>();
        for (File file : files) {
            try {
                jobs.add(objectMapper.readValue(file, ReelJob.class));
            } catch (IOException e) {
                throw new ConfigurationException("Failed to read job record from " + file.getAbsolutePath(), e);
            }
        }
        return List.copyOf(jobs);
    }

    private ReelJob persist(ReelJob job) {
        if (!jobsDirectory.exists() && !jobsDirectory.mkdirs()) {
            throw new ConfigurationException("Could not create jobs directory at " + jobsDirectory.getAbsolutePath());
        }
        File file = jobFile(job.jobId());
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, job);
        } catch (IOException e) {
            throw new ConfigurationException("Failed to write job record to " + file.getAbsolutePath(), e);
        }
        return job;
    }

    private File jobFile(String jobId) {
        return new File(jobsDirectory, jobId + ".json");
    }
}
