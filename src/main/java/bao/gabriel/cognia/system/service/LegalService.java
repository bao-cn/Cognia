package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.legal.ConsentStatusResponse;
import bao.gabriel.cognia.system.dto.legal.LegalDocumentResponse;
import bao.gabriel.cognia.system.dto.legal.SubmitConsentRequest;
import bao.gabriel.cognia.system.dto.legal.UpdateLegalDocumentRequest;

import java.util.List;

public interface LegalService {

    List<LegalDocumentResponse> listDocuments();

    ConsentStatusResponse getConsentStatus(Long userId);

    ConsentStatusResponse submitConsent(Long userId, SubmitConsentRequest request);

    LegalDocumentResponse publish(String type, UpdateLegalDocumentRequest request, Long operatorId);

    void recordConsentForNewUser(Long userId);
}
