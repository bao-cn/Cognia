package bao.gabriel.cognia.system.task;

import bao.gabriel.cognia.system.entity.*;
import bao.gabriel.cognia.system.mapper.TokenLotMapper;
import bao.gabriel.cognia.system.mapper.TokenReservationMapper;
import bao.gabriel.cognia.system.service.TaskHandler;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class BillingReservationCleanupHandler implements TaskHandler {
    public static final String CODE = "BILLING_RESERVATION_CLEANUP";

    private final TokenReservationMapper reservationMapper;
    private final TokenLotMapper lotMapper;

    @Override public String code() { return CODE; }

    @Override
    public TaskResult execute(ScheduledTask task) {
        int threshold = task.getThresholdMinutes() == null ? 30 : task.getThresholdMinutes();
        int batchSize = task.getBatchSize() == null ? 500 : Math.min(task.getBatchSize(), 1000);
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(threshold);
        List<TokenReservation> reservations = reservationMapper.selectList(new LambdaQueryWrapper<TokenReservation>()
                .eq(TokenReservation::getStatus, "RESERVED")
                .le(TokenReservation::getCreateTime, cutoff)
                .orderByAsc(TokenReservation::getCreateTime).last("LIMIT " + batchSize));
        for (TokenReservation reservation : reservations) {
            try {
                List<Map> snapshot = JSON.parseArray(reservation.getLotSnapshot(), Map.class);
                for (Map part : snapshot) {
                    Long lotId = Long.valueOf(String.valueOf(part.get("lotId")));
                    long held = Long.parseLong(String.valueOf(part.get("tokens")));
                    TokenLot lot = lotMapper.selectById(lotId);
                    if (lot == null) continue;
                    lot.setHeldTokens(Math.max(0L, lot.getHeldTokens() - held));
                    lot.setUpdateTime(LocalDateTime.now());
                    lotMapper.updateById(lot);
                }
                reservation.setSettledTokens(0L);
                reservation.setStatus("RELEASED");
                reservation.setUpdateTime(LocalDateTime.now());
                reservationMapper.updateById(reservation);
            } catch (Exception ex) {
                reservation.setStatus("CLEANUP_FAILED");
                reservation.setUpdateTime(LocalDateTime.now());
                reservationMapper.updateById(reservation);
            }
        }
        return TaskResult.success("释放超时预扣 " + reservations.size() + " 个");
    }
}
