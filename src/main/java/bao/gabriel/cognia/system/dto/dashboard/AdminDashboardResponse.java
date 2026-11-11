package bao.gabriel.cognia.system.dto.dashboard;

import java.util.List;

public record AdminDashboardResponse(
        Overview overview,
        Latency latency,
        List<TrendPoint> trends,
        List<FileTypeStat> fileTypes,
        List<ModelStat> models,
        List<TokenStat> tokensByUser,
        List<TokenStat> tokensByModel,
        Ops ops,
        List<TaskStat> tasks,
        List<ErrorCodeStat> errorCodes,
        String generatedAt
) {
    public record Overview(
            long totalUsers, long activeUsers, long bannedUsers, long inactiveUsers,
            long newUsersToday, long newUsersWeek, long newUsersMonth,
            long totalChannels, long publicChannels, long privateChannels,
            long totalFiles, long storageBytes,
            long conversations, double avgRounds,
            long qaRequests, long retrievalRequests, long llmCalls,
            long qaToday, long qaWeek, long qaMonth,
            double retrievalHitRate, double noRetrievalRate, double refusalRate,
            double sessionRetentionRate
    ) {}

    public record Latency(
            long avgRetrievalMs, long avgVectorMs, long avgRerankMs,
            long avgQaMs, long p50QaMs, long p90QaMs, long p99QaMs
    ) {}

    public record TrendPoint(String date, long qa, long retrieval, long llm) {}

    public record FileTypeStat(String type, long count, long bytes) {}

    public record ModelStat(String modelName, long calls, long inputTokens, long outputTokens,
                            long errors, long avgDurationMs) {}

    public record TokenStat(String name, long inputTokens, long outputTokens, long embeddingTokens) {}

    public record Ops(
            String apiStatus, String sseStatus, long sseOnline,
            String documentWorkerStatus, long documentQueue,
            String vectorWorkerStatus, long vectorQueue,
            String scheduledStatus, long scheduledEnabled, long scheduledFailed,
            long dbConnections, long vectorIndexBytes, long slowQueries,
            String redisMemory, double redisHitRate,
            long minioStorageBytes, long minioObjects, int lifecycleDays,
            boolean queueAvailable, long queueBacklog, double queueConsumeRate,
            long queueFailed, long queueRetry,
            List<String> warnings
    ) {}

    public record TaskStat(String code, String name, boolean enabled, String lastStatus,
                           String lastRunAt, String lastMessage) {}

    public record ErrorCodeStat(String errorType, long count) {}
}
