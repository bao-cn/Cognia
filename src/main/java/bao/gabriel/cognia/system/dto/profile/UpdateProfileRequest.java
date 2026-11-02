package bao.gabriel.cognia.system.dto.profile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateProfileRequest(
        @Size(max = 32, message = "昵称长度不能超过 32 个字符") String nickname,
        @Size(max = 16, message = "性别字段过长") String gender,
        @Size(max = 512, message = "个人简介不能超过 512 个字符") String bio,
        @Size(max = 128, message = "企业名称不能超过 128 个字符") String company,
        @Size(max = 128, message = "家乡不能超过 128 个字符") String hometown,
        @Size(max = 64, message = "时区字段过长") String timezone,
        @Size(max = 20000, message = "README 不能超过 20000 个字符") String readme,
        @Size(max = 4, message = "自定义链接最多 4 个") List<@Valid ProfileLink> links
) {
}
