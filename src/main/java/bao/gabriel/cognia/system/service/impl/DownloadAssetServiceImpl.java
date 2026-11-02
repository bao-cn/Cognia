package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.downloads.DownloadAssetResponse;
import bao.gabriel.cognia.system.dto.downloads.UpdateDownloadAssetRequest;
import bao.gabriel.cognia.system.entity.DownloadAsset;
import bao.gabriel.cognia.system.mapper.DownloadAssetMapper;
import bao.gabriel.cognia.system.service.DownloadAssetService;
import bao.gabriel.cognia.system.service.SiteSettingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadAssetServiceImpl implements DownloadAssetService {

    private final DownloadAssetMapper downloadAssetMapper;
    private final SiteSettingService settingService;

    @Override
    public List<DownloadAssetResponse> listPublic() {
        return downloadAssetMapper.selectList(baseQuery().eq(DownloadAsset::getEnabled, true))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<DownloadAssetResponse> listAdmin() {
        return downloadAssetMapper.selectList(baseQuery())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public DownloadAssetResponse update(String code, UpdateDownloadAssetRequest request, Long operatorId) {
        DownloadAsset asset = downloadAssetMapper.selectOne(baseQuery().eq(DownloadAsset::getCode, normalizeCode(code)));
        if (asset == null) {
            throw BusinessException.notFound("下载项不存在");
        }
        String url = StringUtils.hasText(request.downloadUrl()) ? request.downloadUrl().trim() : null;
        if (url != null && !url.startsWith("https://") && !url.startsWith("http://")) {
            throw BusinessException.badRequest("下载链接必须是 http:// 或 https:// 地址");
        }
        String sha256 = StringUtils.hasText(request.sha256())
                ? request.sha256().trim().toLowerCase(Locale.ROOT) : null;
        if (sha256 != null && !sha256.matches("^[a-f0-9]{64}$")) {
            throw BusinessException.badRequest("SHA256 必须是 64 位十六进制字符串");
        }
        if (request.fileSize() != null && request.fileSize() < 0) {
            throw BusinessException.badRequest("文件大小不能为负数");
        }

        asset.setName(request.name().trim());
        asset.setVersion(request.version().trim());
        asset.setDownloadUrl(url);
        asset.setSha256(sha256);
        asset.setFileSize(request.fileSize());
        asset.setEnabled(request.enabled() == null || request.enabled());
        asset.setUpdatedBy(operatorId);
        asset.setUpdatedAt(LocalDateTime.now());
        downloadAssetMapper.updateById(asset);
        log.info("Download asset {} updated by user {}", asset.getCode(), operatorId);
        return toResponse(asset);
    }

    private LambdaQueryWrapper<DownloadAsset> baseQuery() {
        return new LambdaQueryWrapper<DownloadAsset>()
                .orderByAsc(DownloadAsset::getSort)
                .orderByAsc(DownloadAsset::getId);
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw BusinessException.badRequest("下载项编码不能为空");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private DownloadAssetResponse toResponse(DownloadAsset asset) {
        return new DownloadAssetResponse(
                asset.getCode(),
                asset.getName(),
                asset.getPlatform(),
                asset.getArch(),
                asset.getPackageType(),
                asset.getVersion(),
                asset.getDownloadUrl(),
                asset.getSha256(),
                asset.getFileSize(),
                Boolean.TRUE.equals(asset.getEnabled()),
                settingService.formatDateTime(asset.getUpdatedAt())
        );
    }
}
