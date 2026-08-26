package com.localexplorer.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RuntimeSettingMapper {

    @Select("select setting_value from runtime_setting where setting_key = #{settingKey}")
    String selectValue(@Param("settingKey") String settingKey);

    @Insert("insert into runtime_setting(setting_key, setting_value, update_time) " +
            "values(#{settingKey}, #{settingValue}, now()) " +
            "on duplicate key update setting_value = values(setting_value), update_time = values(update_time)")
    void upsert(@Param("settingKey") String settingKey, @Param("settingValue") String settingValue);
}
