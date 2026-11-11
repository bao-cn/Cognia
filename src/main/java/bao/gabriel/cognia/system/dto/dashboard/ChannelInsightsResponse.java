package bao.gabriel.cognia.system.dto.dashboard;

import java.util.List;

public record ChannelInsightsResponse(
        ChannelSummary channel,
        long documentCount, long storageBytes,
        long subscribers, long qaRequests, long retrievalRequests,
        long inputTokens, long outputTokens, double retrievalHitRate, double refusalRate,
        long avgLatencyMs, long p90LatencyMs,
        List<FileTypeStat> fileTypes,
        List<TrendPoint> trends,
        List<TopDocument> topDocuments,
        String generatedAt
) {
    public record ChannelSummary(Long id, String name, String slug, boolean isPublic, long members) {}
    public record FileTypeStat(String type, long count, long bytes) {}
    public record TrendPoint(String date, long qa, long retrieval, long llm) {}
    public record TopDocument(Long id, String name, String type, long size, String status, String createTime) {}
}
