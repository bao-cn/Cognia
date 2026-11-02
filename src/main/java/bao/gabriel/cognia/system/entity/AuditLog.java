package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Immutable record of an auditable operation. Written by the web interceptor
 * for authenticated mutating requests and by the scheduled task runner.
 */
@Data
@NoArgsConstructor
@TableName("sys_audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String username;
    private String module;
    private String action;
    private String description;
    private String resourceType;
    private String resourceId;
    private String method;
    private String path;
    private String ip;
    private String userAgent;
    private String requestPayload;
    private String status;
    private String errorMessage;
    private Integer durationMs;
    private LocalDateTime createTime;
}
