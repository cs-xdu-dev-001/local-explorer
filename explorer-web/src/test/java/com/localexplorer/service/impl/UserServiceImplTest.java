package com.localexplorer.service.impl;

import com.localexplorer.constant.MessageConstant;
import com.localexplorer.constant.PasswordConstant;
import com.localexplorer.constant.StatusConstant;
import com.localexplorer.context.BaseContext;
import com.localexplorer.dto.UserDTO;
import com.localexplorer.entity.Employee;
import com.localexplorer.dto.UserLoginDTO;
import com.localexplorer.entity.User;
import com.localexplorer.exception.AccountLockedException;
import com.localexplorer.exception.AccountNotFoundException;
import com.localexplorer.exception.PasswordErrorException;
import com.localexplorer.mapper.UserMapper;
import com.localexplorer.mapper.EmployeeMapper;
import com.localexplorer.service.AdminPermissionService;
import com.localexplorer.service.AuthSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.DigestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private UserServiceImpl userService;

    @Mock
    private UserMapper userMapper;
    @Mock
    private EmployeeMapper employeeMapper;
    @Mock
    private AuthSessionService authSessionService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl();
        ReflectionTestUtils.setField(userService, "userMapper", userMapper);
        ReflectionTestUtils.setField(userService, "authSessionService", authSessionService);
        ReflectionTestUtils.setField(
                userService,
                "adminPermissionService",
                new AdminPermissionService(employeeMapper));
        BaseContext.setCurrentId(1L);
        org.mockito.Mockito.lenient().when(employeeMapper.getById(1L))
                .thenReturn(Employee.builder().id(1L).role("ADMIN").build());
    }

    @AfterEach
    void tearDown() {
        BaseContext.removeCurrentId();
    }

    @Test
    void loginReturnsUserWhenPhoneAndPasswordAreValid() {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setPhone("13800001111");
        dto.setPassword("123456");
        User user = User.builder()
                .id(1L)
                .phone("13800001111")
                .password(md5("123456"))
                .name("张小明")
                .build();
        when(userMapper.getByPhone("13800001111")).thenReturn(user);

        User result = userService.login(dto);

        assertThat(result).isSameAs(user);
    }

    @Test
    void loginThrowsWhenPhoneDoesNotExist() {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setPhone("13900000000");
        dto.setPassword("123456");
        when(userMapper.getByPhone("13900000000")).thenReturn(null);

        assertThatThrownBy(() -> userService.login(dto))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage(MessageConstant.ACCOUNT_NOT_FOUND);
    }

    @Test
    void loginThrowsWhenPasswordIsWrong() {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setPhone("13800001111");
        dto.setPassword("bad-password");
        User user = User.builder()
                .phone("13800001111")
                .password(md5("123456"))
                .build();
        when(userMapper.getByPhone("13800001111")).thenReturn(user);

        assertThatThrownBy(() -> userService.login(dto))
                .isInstanceOf(PasswordErrorException.class)
                .hasMessage(MessageConstant.PASSWORD_ERROR);
    }

    @Test
    void loginThrowsWhenAccountIsDisabled() {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setPhone("13800001111");
        dto.setPassword("123456");
        User user = User.builder()
                .phone("13800001111")
                .password(md5("123456"))
                .status(StatusConstant.DISABLE)
                .build();
        when(userMapper.getByPhone("13800001111")).thenReturn(user);

        assertThatThrownBy(() -> userService.login(dto))
                .isInstanceOf(AccountLockedException.class)
                .hasMessage(MessageConstant.ACCOUNT_LOCKED);
    }

    @Test
    void resetPasswordStoresDefaultPasswordAsMd5() {
        when(userMapper.getById(7L)).thenReturn(User.builder().id(7L).phone("13800001111").build());

        userService.resetPassword(7L);

        verify(userMapper).resetPassword(7L, md5(PasswordConstant.DEFAULT_PASSWORD));
    }

    @Test
    void resetPasswordThrowsWhenUserDoesNotExist() {
        when(userMapper.getById(404L)).thenReturn(null);

        assertThatThrownBy(() -> userService.resetPassword(404L))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage(MessageConstant.ACCOUNT_NOT_FOUND);
    }

    @Test
    void resetPasswordRejectsStaffOperator() {
        BaseContext.setCurrentId(2L);
        when(employeeMapper.getById(2L)).thenReturn(Employee.builder().id(2L).role("STAFF").build());

        assertThatThrownBy(() -> userService.resetPassword(7L))
                .isInstanceOf(com.localexplorer.exception.BaseException.class)
                .hasMessage("当前员工没有权限执行该操作");
    }

    @Test
    void updateRejectsStaffOperator() {
        BaseContext.setCurrentId(2L);
        when(employeeMapper.getById(2L)).thenReturn(Employee.builder().id(2L).role("STAFF").build());
        UserDTO dto = new UserDTO();
        dto.setId(7L);
        dto.setName("普通员工编辑用户");
        dto.setPhone("13800001111");

        assertThatThrownBy(() -> userService.update(dto))
                .isInstanceOf(com.localexplorer.exception.BaseException.class)
                .hasMessage("当前员工没有权限执行该操作");

        verify(userMapper, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateCopiesEditableProfileFields() {
        when(userMapper.getById(7L)).thenReturn(User.builder().id(7L).phone("13800001111").build());
        UserDTO dto = new UserDTO();
        dto.setId(7L);
        dto.setName("林夏");
        dto.setPhone("13800001111");
        dto.setSex("0");
        dto.setIdNumber("330100199901010011");
        dto.setAvatar("https://example.com/a.png");

        userService.update(dto);

        User expected = User.builder()
                .id(7L)
                .name("林夏")
                .phone("13800001111")
                .sex("0")
                .idNumber("330100199901010011")
                .avatar("https://example.com/a.png")
                .build();
        verify(userMapper).update(expected);
    }

    @Test
    void startOrStopUpdatesUserStatus() {
        when(userMapper.getById(7L)).thenReturn(User.builder().id(7L).build());

        userService.startOrStop(StatusConstant.DISABLE, 7L);

        User expected = User.builder()
                .id(7L)
                .status(StatusConstant.DISABLE)
                .build();
        verify(userMapper).update(expected);
    }

    @Test
    void startOrStopRejectsStaffOperator() {
        BaseContext.setCurrentId(2L);
        when(employeeMapper.getById(2L)).thenReturn(Employee.builder().id(2L).role("STAFF").build());

        assertThatThrownBy(() -> userService.startOrStop(StatusConstant.DISABLE, 7L))
                .isInstanceOf(com.localexplorer.exception.BaseException.class)
                .hasMessage("当前员工没有权限执行该操作");

        verify(userMapper, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void startOrStopRejectsMissingUser() {
        assertThatThrownBy(() -> userService.startOrStop(StatusConstant.DISABLE, 404L))
                .isInstanceOf(com.localexplorer.exception.BaseException.class)
                .hasMessage("账号不存在");
    }

    @Test
    void startOrStopRejectsInvalidStatus() {
        assertThatThrownBy(() -> userService.startOrStop(2, 7L))
                .isInstanceOf(com.localexplorer.exception.BaseException.class)
                .hasMessage("状态参数只能为0或1");
    }

    @Test
    void updateThrowsWhenUserDoesNotExist() {
        UserDTO dto = new UserDTO();
        dto.setId(404L);
        when(userMapper.getById(404L)).thenReturn(null);

        assertThatThrownBy(() -> userService.update(dto))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage(MessageConstant.ACCOUNT_NOT_FOUND);
    }

    private static String md5(String text) {
        return DigestUtils.md5DigestAsHex(text.getBytes());
    }
}
