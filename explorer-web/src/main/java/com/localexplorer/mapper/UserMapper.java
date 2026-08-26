package com.localexplorer.mapper;

import com.github.pagehelper.Page;
import com.localexplorer.dto.UserPageQueryDTO;
import com.localexplorer.domain.ExportQuerySnapshot;
import com.localexplorer.entity.User;
import com.localexplorer.vo.UserVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM user WHERE phone = #{phone}")
    User getByPhone(String phone);

    @Select("SELECT * FROM user WHERE id = #{id}")
    User getById(Long id);

    void insert(User user);

    Page<UserVO> pageQuery(UserPageQueryDTO dto);

    void update(User user);

    @Update("update user set password = #{password} where id = #{id}")
    void resetPassword(@Param("id") Long id, @Param("password") String password);

    @Select("select count(*) from user")
    Long countAll();

    java.util.List<java.util.Map<String, Object>> countByDate(@org.apache.ibatis.annotations.Param("days") int days);

    Long findMaxIdForExport(ExportQuerySnapshot snapshot);

    long countForExport(ExportQuerySnapshot snapshot);

    List<UserVO> findExportChunk(@Param("snapshot") ExportQuerySnapshot snapshot,
                                 @Param("lastId") long lastId,
                                 @Param("limit") int limit);
}
