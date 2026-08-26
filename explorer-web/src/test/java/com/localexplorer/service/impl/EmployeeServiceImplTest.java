package com.localexplorer.service.impl;

import com.localexplorer.constant.MessageConstant;
import com.localexplorer.constant.StatusConstant;
import com.localexplorer.context.BaseContext;
import com.localexplorer.dto.EmployeeDTO;
import com.localexplorer.dto.EmployeeLoginDTO;
import com.localexplorer.entity.Employee;
import com.localexplorer.exception.AccountLockedException;
import com.localexplorer.exception.AccountNotFoundException;
import com.localexplorer.exception.PasswordErrorException;
import com.localexplorer.mapper.EmployeeMapper;
import com.localexplorer.mapper.OperationLogMapper;
import com.localexplorer.service.AdminPermissionService;
import com.localexplorer.service.AuthSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.DigestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    private EmployeeServiceImpl employeeService;

    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private OperationLogMapper operationLogMapper;
    @Mock
    private AuthSessionService authSessionService;

    @BeforeEach
    void setUp() {
        employeeService = new EmployeeServiceImpl();
        ReflectionTestUtils.setField(employeeService, "employeeMapper", employeeMapper);
        ReflectionTestUtils.setField(employeeService, "operationLogMapper", operationLogMapper);
        ReflectionTestUtils.setField(employeeService, "authSessionService", authSessionService);
        ReflectionTestUtils.setField(
                employeeService,
                "adminPermissionService",
                new AdminPermissionService(employeeMapper));
        BaseContext.setCurrentId(1L);
        lenient().when(employeeMapper.getById(1L))
                .thenReturn(Employee.builder().id(1L).role("ADMIN").build());
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    void loginReturnsEmployeeWhenCredentialsAreValid() {
        EmployeeLoginDTO dto = new EmployeeLoginDTO();
        dto.setUsername("admin");
        dto.setPassword("123456");
        Employee employee = Employee.builder()
                .id(1L)
                .username("admin")
                .password(md5("123456"))
                .status(StatusConstant.ENABLE)
                .build();
        when(employeeMapper.getByUsername("admin")).thenReturn(employee);

        Employee result = employeeService.login(dto);

        assertThat(result).isSameAs(employee);
    }

    @Test
    void loginThrowsWhenAccountDoesNotExist() {
        EmployeeLoginDTO dto = new EmployeeLoginDTO();
        dto.setUsername("missing");
        dto.setPassword("123456");
        when(employeeMapper.getByUsername("missing")).thenReturn(null);

        assertThatThrownBy(() -> employeeService.login(dto))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage(MessageConstant.ACCOUNT_NOT_FOUND);
    }

    @Test
    void loginThrowsWhenPasswordIsWrong() {
        EmployeeLoginDTO dto = new EmployeeLoginDTO();
        dto.setUsername("admin");
        dto.setPassword("bad-password");
        Employee employee = Employee.builder()
                .username("admin")
                .password(md5("123456"))
                .status(StatusConstant.ENABLE)
                .build();
        when(employeeMapper.getByUsername("admin")).thenReturn(employee);

        assertThatThrownBy(() -> employeeService.login(dto))
                .isInstanceOf(PasswordErrorException.class)
                .hasMessage(MessageConstant.PASSWORD_ERROR);
    }

    @Test
    void loginThrowsWhenAccountIsDisabled() {
        EmployeeLoginDTO dto = new EmployeeLoginDTO();
        dto.setUsername("admin");
        dto.setPassword("123456");
        Employee employee = Employee.builder()
                .username("admin")
                .password(md5("123456"))
                .status(StatusConstant.DISABLE)
                .build();
        when(employeeMapper.getByUsername("admin")).thenReturn(employee);

        assertThatThrownBy(() -> employeeService.login(dto))
                .isInstanceOf(AccountLockedException.class)
                .hasMessage(MessageConstant.ACCOUNT_LOCKED);
    }

    @Test
    void saveRejectsStaffOperator() {
        BaseContext.setCurrentId(2L);
        when(employeeMapper.getById(2L)).thenReturn(Employee.builder().id(2L).role("STAFF").build());
        EmployeeDTO dto = new EmployeeDTO();
        dto.setName("普通员工新增");
        dto.setUsername("staff-created");
        dto.setRole("ADMIN");

        assertThatThrownBy(() -> employeeService.save(dto))
                .isInstanceOf(com.localexplorer.exception.BaseException.class)
                .hasMessage("当前员工没有权限执行该操作");

        verify(employeeMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteByIdDeletesNonDefaultEmployee() {
        lenient().when(employeeMapper.getById(8L)).thenReturn(Employee.builder().id(8L).build());

        employeeService.deleteById(8L);

        verify(employeeMapper).deleteById(8L);
    }

    @Test
    void deleteByIdRejectsDefaultAdminAccount() {
        assertThatThrownBy(() -> employeeService.deleteById(1L))
                .isInstanceOf(com.localexplorer.exception.DeletionNotAllowedException.class)
                .hasMessage("默认管理员账号不能删除");

        verify(employeeMapper, never()).deleteById(1L);
    }

    @Test
    void startOrStopRejectsDisablingDefaultAdminAccount() {
        assertThatThrownBy(() -> employeeService.startOrStop(StatusConstant.DISABLE, 1L))
                .isInstanceOf(com.localexplorer.exception.DeletionNotAllowedException.class)
                .hasMessage("默认管理员账号不能禁用");

        verify(employeeMapper, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void startOrStopRejectsMissingEmployee() {
        assertThatThrownBy(() -> employeeService.startOrStop(StatusConstant.ENABLE, 404L))
                .isInstanceOf(com.localexplorer.exception.BaseException.class)
                .hasMessage("员工不存在");

        verify(employeeMapper, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void startOrStopRejectsInvalidStatus() {
        assertThatThrownBy(() -> employeeService.startOrStop(2, 8L))
                .isInstanceOf(com.localexplorer.exception.BaseException.class)
                .hasMessage("状态参数只能为0或1");

        verify(employeeMapper, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteByIdRejectsEmployeeReferencedByOperationLog() {
        when(employeeMapper.getById(8L)).thenReturn(Employee.builder().id(8L).build());
        when(operationLogMapper.countByOperatorId(8L)).thenReturn(1L);

        assertThatThrownBy(() -> employeeService.deleteById(8L))
                .isInstanceOf(com.localexplorer.exception.DeletionNotAllowedException.class)
                .hasMessage("当前员工已有操作日志，不能删除，可改为禁用");

        verify(employeeMapper, never()).deleteById(8L);
    }

    @Test
    void deleteByIdRejectsMissingEmployee() {
        when(employeeMapper.getById(8L)).thenReturn(null);

        assertThatThrownBy(() -> employeeService.deleteById(8L))
                .isInstanceOf(com.localexplorer.exception.BaseException.class)
                .hasMessage("员工不存在");

        verify(operationLogMapper, never()).countByOperatorId(8L);
        verify(employeeMapper, never()).deleteById(8L);
    }

    @Test
    void deleteByIdRejectsStaffOperator() {
        BaseContext.setCurrentId(2L);
        when(employeeMapper.getById(2L)).thenReturn(Employee.builder().id(2L).role("STAFF").build());

        assertThatThrownBy(() -> employeeService.deleteById(8L))
                .isInstanceOf(com.localexplorer.exception.BaseException.class)
                .hasMessage("当前员工没有权限执行该操作");

        verify(employeeMapper, never()).deleteById(8L);
    }

    @Test
    void startOrStopRejectsStaffOperator() {
        BaseContext.setCurrentId(2L);
        when(employeeMapper.getById(2L)).thenReturn(Employee.builder().id(2L).role("STAFF").build());

        assertThatThrownBy(() -> employeeService.startOrStop(StatusConstant.DISABLE, 8L))
                .isInstanceOf(com.localexplorer.exception.BaseException.class)
                .hasMessage("当前员工没有权限执行该操作");

        verify(employeeMapper, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateRejectsStaffOperator() {
        BaseContext.setCurrentId(2L);
        when(employeeMapper.getById(2L)).thenReturn(Employee.builder().id(2L).role("STAFF").build());
        EmployeeDTO dto = new EmployeeDTO();
        dto.setId(8L);
        dto.setName("普通员工编辑");
        dto.setUsername("staff-edited");
        dto.setRole("ADMIN");

        assertThatThrownBy(() -> employeeService.update(dto))
                .isInstanceOf(com.localexplorer.exception.BaseException.class)
                .hasMessage("当前员工没有权限执行该操作");

        verify(employeeMapper, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getByIdRejectsMissingEmployee() {
        assertThatThrownBy(() -> employeeService.getById(404L))
                .isInstanceOf(com.localexplorer.exception.BaseException.class)
                .hasMessage("员工不存在");
    }

    @Test
    void updateRejectsMissingEmployee() {
        EmployeeDTO dto = new EmployeeDTO();
        dto.setId(404L);
        dto.setName("不存在的员工");
        dto.setUsername("missing-employee");

        assertThatThrownBy(() -> employeeService.update(dto))
                .isInstanceOf(com.localexplorer.exception.BaseException.class)
                .hasMessage("员工不存在");

        verify(employeeMapper, never()).update(org.mockito.ArgumentMatchers.any());
    }

    private static String md5(String text) {
        return DigestUtils.md5DigestAsHex(text.getBytes());
    }
}
