package com.localexplorer.mapper;

import com.github.pagehelper.Page;
import com.localexplorer.dto.OperationLogPageQueryDTO;
import com.localexplorer.domain.ExportQuerySnapshot;
import com.localexplorer.entity.OperationLogEntity;
import com.localexplorer.vo.OperationLogVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OperationLogMapper {

    void insert(OperationLogEntity log);

    Page<OperationLogVO> pageQuery(OperationLogPageQueryDTO dto);

    @Select("select count(*) from operation_log where operator_id = #{operatorId}")
    long countByOperatorId(Long operatorId);

    Long findMaxIdForExport(ExportQuerySnapshot snapshot);

    long countForExport(ExportQuerySnapshot snapshot);

    List<OperationLogVO> findExportChunk(@Param("snapshot") ExportQuerySnapshot snapshot,
                                         @Param("lastId") long lastId,
                                         @Param("limit") int limit);
}
