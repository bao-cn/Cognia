package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("kb_channel_notify_subscription")
public class ChannelNotifySubscription {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long channelId;

    private Long userId;

    private LocalDateTime createTime;
}
