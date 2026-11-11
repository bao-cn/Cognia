package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_debug_setting")
public class DebugSetting {
    public static final long SINGLETON_ID = 1L;

    @TableId(type = IdType.INPUT)
    private Long id;
    private Boolean enabled;
    private String hardcodedUsername;
    private Long updatedBy;
    private LocalDateTime updatedAt;
    private LocalDateTime createTime;

    public boolean enabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public String username() {
        return hardcodedUsername == null || hardcodedUsername.isBlank() ? "admin" : hardcodedUsername;
    }
}
