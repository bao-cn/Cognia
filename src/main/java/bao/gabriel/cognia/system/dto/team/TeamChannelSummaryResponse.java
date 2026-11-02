package bao.gabriel.cognia.system.dto.team;

/**
 * Placeholder shape for the public channel list. Channel persistence and
 * document ingestion are intentionally outside the first team release.
 */
public record TeamChannelSummaryResponse(
        String id,
        String name,
        String description,
        long documentCount
) {
}
