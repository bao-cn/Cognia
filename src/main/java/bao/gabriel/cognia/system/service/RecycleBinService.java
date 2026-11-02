package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.system.dto.recycle.RecycleBinResponse;

import java.util.List;

public interface RecycleBinService {

    List<RecycleBinResponse> list(String ownerType, Long ownerId, Long viewerId);

    void purge(Long id, Long viewerId);
}
