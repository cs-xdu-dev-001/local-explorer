package com.localexplorer.service.impl;

import com.localexplorer.constant.MessageConstant;
import com.localexplorer.constant.PasswordConstant;
import com.localexplorer.constant.StatusConstant;
import com.localexplorer.dto.UserDTO;
import com.localexplorer.dto.UserLoginDTO;
import com.localexplorer.entity.User;
import com.localexplorer.exception.AccountLockedException;
import com.localexplorer.exception.AccountNotFoundException;
import com.localexplorer.exception.BaseException;
import com.localexplorer.exception.PasswordErrorException;
import com.localexplorer.mapper.UserMapper;
import com.localexplorer.service.AdminPermissionService;
import com.localexplorer.service.UserService;
import com.localexplorer.service.AuthSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private AdminPermissionService adminPermissionService;
    @Autowired
    private AuthSessionService authSessionService;

    @Override
    public User login(UserLoginDTO userLoginDTO) {
        User user = userMapper.getByPhone(userLoginDTO.getPhone());
        if (user == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        String passwordMd5 = DigestUtils.md5DigestAsHex(userLoginDTO.getPassword().getBytes());
        if (!passwordMd5.equals(user.getPassword())) {
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        if (StatusConstant.DISABLE.equals(user.getStatus())) {
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        return user;
    }

    @Override
    @Transactional
    public void resetPassword(Long id) {
        adminPermissionService.requireAdmin();
        User user = userMapper.getById(id);
        if (user == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        String passwordMd5 = DigestUtils.md5DigestAsHex(PasswordConstant.DEFAULT_PASSWORD.getBytes());
        userMapper.resetPassword(id, passwordMd5);
        authSessionService.revokeAll(AuthSessionServiceImpl.USER, id, "PASSWORD_RESET");
    }

    @Override
    public User getById(Long id) {
        User user = userMapper.getById(id);
        if (user == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        user.setPassword("****");
        return user;
    }

    @Override
    public void update(UserDTO userDTO) {
        adminPermissionService.requireAdmin();
        User user = userMapper.getById(userDTO.getId());
        if (user == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        User updateUser = new User();
        BeanUtils.copyProperties(userDTO, updateUser);
        userMapper.update(updateUser);
    }

    @Override
    @Transactional
    public void startOrStop(Integer status, Long id) {
        adminPermissionService.requireAdmin();
        if (!StatusConstant.isValid(status)) {
            throw new BaseException(MessageConstant.STATUS_INVALID);
        }
        if (userMapper.getById(id) == null) {
            throw new BaseException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        User user = User.builder()
                .id(id)
                .status(status)
                .build();
        userMapper.update(user);
        if (StatusConstant.DISABLE.equals(status)) {
            authSessionService.revokeAll(AuthSessionServiceImpl.USER, id, "ACCOUNT_DISABLED");
        }
    }

}
