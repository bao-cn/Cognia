package bao.gabriel.cognia.system.dto.rag;

public record EmbeddingDimensionCheckResponse(
        int currentDimension,
        Integer modelDimension,
        boolean compatible,
        String message
) {
}
