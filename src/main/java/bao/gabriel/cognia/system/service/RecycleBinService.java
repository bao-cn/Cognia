package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.system.dto.recycle.AdminRecycleBinResponse;
import bao.gabriel.cognia.system.dto.recycle.RecycleBinResponse;
import bao.gabriel.cognia.system.dto.recycle.RecycleChannelOption;
import bao.gabriel.cognia.system.dto.recycle.RecycleFileContent;

import java.util.List;

public interface RecycleBinService {

    List<RecycleBinResponse> list(String ownerType, Long ownerId, Long viewerId);

    List<RecycleBinResponse> listByChannel(Long channelId);

    void restore(Long id, Long channelId);

    void purge(Long id, Long viewerId);

    PageResult<AdminRecycleBinResponse> adminPage(long page, long size, Long channelId, String keyword, String channelKeyword);

    List<RecycleChannelOption> adminChannels();

    RecycleFileContent adminContent(Long id);

    void adminRestore(Long id);

    void adminPurge(Long id);
}
