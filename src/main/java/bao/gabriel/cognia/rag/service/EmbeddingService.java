package bao.gabriel.cognia.rag.service;

import java.util.List;

public interface EmbeddingService {

    EmbeddedText embed(String text);

    List<EmbeddedText> embedAll(List<String> texts);

    int dimension();

    record EmbeddedText(float[] vector, String model, int dimension) {
    }
}
