package com.localexplorer.service;

import com.localexplorer.dto.UserLoginDTO;
import com.localexplorer.dto.UserDTO;
import com.localexplorer.entity.User;

public interface UserService {

    User login(UserLoginDTO userLoginDTO);

    void resetPassword(Long id);

    User getById(Long id);

    void update(UserDTO userDTO);

    void startOrStop(Integer status, Long id);
}
