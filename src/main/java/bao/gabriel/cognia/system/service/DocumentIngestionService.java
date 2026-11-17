package bao.gabriel.cognia.system.service;

public interface DocumentIngestionService {
    void ingest(Long documentId);
    void parse(Long documentId);
    void embed(Long documentId);
}
