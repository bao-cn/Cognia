package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.downloads.DownloadAssetResponse;
import bao.gabriel.cognia.system.dto.downloads.UpdateDownloadAssetRequest;

import java.util.List;

public interface DownloadAssetService {

    List<DownloadAssetResponse> listPublic();

    List<DownloadAssetResponse> listAdmin();

    DownloadAssetResponse update(String code, UpdateDownloadAssetRequest request, Long operatorId);
}
