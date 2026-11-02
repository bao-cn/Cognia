package bao.gabriel.cognia.system.service;

import bao.gabriel.cognia.common.api.PageResult;
import bao.gabriel.cognia.system.dto.team.AdminTeamResponse;

/**
 * Console-side team management. Distributed teams are still created and
 * maintained by their owners; administrators can inspect and dissolve them.
 */
public interface AdminTeamService {

    PageResult<AdminTeamResponse> page(long page, long size, String keyword);

    void delete(Long teamId);
}
