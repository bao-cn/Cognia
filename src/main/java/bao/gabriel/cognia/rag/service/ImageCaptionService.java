package bao.gabriel.cognia.rag.service;

import bao.gabriel.cognia.system.entity.KbDocument;

public interface ImageCaptionService {
    /** Returns null when image captioning is disabled; throws on configured-but-failed calls. */
    String captionIfConfigured(KbDocument document, byte[] bytes);
}
