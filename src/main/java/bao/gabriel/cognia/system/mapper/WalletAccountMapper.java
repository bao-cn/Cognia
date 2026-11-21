package bao.gabriel.cognia.system.mapper;

import bao.gabriel.cognia.system.entity.WalletAccount;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface WalletAccountMapper extends BaseMapper<WalletAccount> {
    @Insert("""
            INSERT INTO sys_wallet_account (user_id, balance_units, create_time, update_time)
            VALUES (#{userId}, 0, #{now}, #{now})
            ON CONFLICT (user_id) DO NOTHING
            """)
    int insertIfAbsent(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Select("SELECT * FROM sys_wallet_account WHERE user_id = #{userId} FOR UPDATE")
    WalletAccount selectForUpdate(@Param("userId") Long userId);
}
