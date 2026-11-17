package bao.gabriel.cognia.rag.ingestion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateIngestionSettingRequest(
        @Min(1) @Max(16) Integer concurrency,
        @Min(10) @Max(100000) Integer queueLimit,
        @Min(0) @Max(20) Integer maxRetries,
        @Min(5) @Max(3600) Integer retryBackoffSeconds,
        @Min(4) @Max(64) Integer hnswM,
        @Min(40) @Max(1000) Integer hnswEfConstruction,
        @Min(10) @Max(1000) Integer hnswEfSearch,
        @Min(1) @Max(100) Integer healthFailureThreshold,
        @Min(1) Integer queueAlertDepth,
        @Min(1) @Max(100) Integer failureAlertPercent,
        Boolean alertEmailEnabled,
        @Size(max = 255) String alertRecipient,
        @Size(max = 64) String alertRoleCode
) {}
