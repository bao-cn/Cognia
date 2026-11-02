package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * The version of a legal document a user has explicitly accepted.
 */
@Data
@NoArgsConstructor
@TableName("sys_user_consent")
public class UserConsent {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String docType;

    private Integer version;

    private LocalDateTime consentedAt;

    public UserConsent(Long userId, String docType, Integer version, LocalDateTime consentedAt) {
        this.userId = userId;
        this.docType = docType;
        this.version = version;
        this.consentedAt = consentedAt;
    }
}
