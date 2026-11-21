package bao.gabriel.cognia.system.mapper;
import bao.gabriel.cognia.system.entity.BillingSetting;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
@Mapper
public interface BillingSettingMapper extends BaseMapper<BillingSetting> {
    @Select("SELECT * FROM sys_billing_setting WHERE id = #{id} FOR UPDATE")
    BillingSetting selectForUpdate(@Param("id") Long id);
}
