package bao.gabriel.cognia.system.dto.legal;

import java.util.List;
import java.util.Map;

/**
 * @param consents map of document type (PRIVACY/TERMS) to the accepted version
 */
public record ConsentStatusResponse(
        List<LegalDocumentResponse> documents,
        Map<String, Integer> consents,
        boolean requiresConsent
) {
}
