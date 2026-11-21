package bao.gabriel.cognia.system.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@TableName("sys_billing_setting")
public class BillingSetting {
    @TableId
    private Long id;
    private Boolean billingEnabled;
    private Long defaultPlanId;
    private Long registrationTokens;
    private Integer registrationTokenExpireDays;
    private String currency;
    private String walletCurrency;
    private Integer walletFractionalDigits;
    private String walletUnitLabel;
    private Long updatedBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
