package bao.gabriel.cognia.system.service.impl;

import bao.gabriel.cognia.common.exception.BusinessException;
import bao.gabriel.cognia.system.dto.downloads.UpdateDownloadAssetRequest;
import bao.gabriel.cognia.system.entity.DownloadAsset;
import bao.gabriel.cognia.system.mapper.DownloadAssetMapper;
import bao.gabriel.cognia.system.service.SiteSettingService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DownloadAssetServiceImplTest {

    private final DownloadAssetMapper mapper = mock(DownloadAssetMapper.class);
    private final SiteSettingService settingService = mock(SiteSettingService.class);
    private final DownloadAssetServiceImpl service = new DownloadAssetServiceImpl(mapper, settingService);

    private DownloadAsset windowsAsset() {
        DownloadAsset asset = new DownloadAsset();
        asset.setId(1L);
        asset.setCode("WINDOWS_X64");
        asset.setName("Windows（64 位）");
        asset.setPlatform("windows");
        asset.setArch("x64");
        asset.setVersion("1.0.0");
        asset.setSort(3);
        asset.setEnabled(true);
        return asset;
    }

    @Test
    void updatesVersionUrlAndChecksum() {
        DownloadAsset asset = windowsAsset();
        when(mapper.selectOne(any())).thenReturn(asset);
        String sha = "a".repeat(64);

        service.update("windows_x64", new UpdateDownloadAssetRequest(
                "Windows", "1.2.3", "https://example.com/cognia.exe", sha, 128L * 1024 * 1024, true
        ), 9L);

        assertEquals("1.2.3", asset.getVersion());
        assertEquals("https://example.com/cognia.exe", asset.getDownloadUrl());
        assertEquals(sha, asset.getSha256());
        assertEquals(128L * 1024 * 1024, asset.getFileSize());
        verify(mapper).updateById(asset);
    }

    @Test
    void rejectsInvalidChecksum() {
        when(mapper.selectOne(any())).thenReturn(windowsAsset());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.update("WINDOWS_X64", new UpdateDownloadAssetRequest(
                        "Windows", "1.0.0", "https://example.com/cognia.exe", "abc", null, true
                ), 9L));

        assertEquals("SHA256 必须是 64 位十六进制字符串", exception.getMessage());
    }
}
