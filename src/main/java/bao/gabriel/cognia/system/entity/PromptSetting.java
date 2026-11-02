package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_prompt_setting")
public class PromptSetting {

    public static final long SINGLETON_ID = 1L;

    @TableId(type = IdType.INPUT)
    private Long id;
    private String systemPrompt;
    private String ragPrompt;
    private String expiredPrompt;
    private String toolPrompt;
    private Long updatedBy;
    private LocalDateTime updateTime;
}
