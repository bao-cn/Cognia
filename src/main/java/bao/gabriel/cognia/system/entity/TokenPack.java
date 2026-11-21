package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_token_pack")
public class TokenPack {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String description;
    private Long priceMinor;
    private String currency;
    private Long tokens;
    private Integer expireDays;
    private String status;
    private Integer sort;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
