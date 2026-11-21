package bao.gabriel.cognia.system.mapper;

import bao.gabriel.cognia.system.entity.RedeemCode;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RedeemCodeMapper extends BaseMapper<RedeemCode> {
    @Select("SELECT * FROM sys_billing_redeem_code WHERE code_hash = #{codeHash} FOR UPDATE")
    RedeemCode selectForUpdateByHash(@Param("codeHash") String codeHash);
}
