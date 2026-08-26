package com.localexplorer.controller;

import com.localexplorer.controller.admin.CategoryController;
import com.localexplorer.controller.admin.EmployeeController;
import com.localexplorer.controller.admin.ExploreItemController;
import com.localexplorer.controller.admin.ExplorePackageController;
import com.localexplorer.controller.admin.OperationLogController;
import com.localexplorer.controller.admin.UserManageController;
import com.localexplorer.handler.GlobalExceptionHandler;
import com.localexplorer.mapper.UserMapper;
import com.localexplorer.service.CategoryService;
import com.localexplorer.service.EmployeeService;
import com.localexplorer.service.ExploreItemService;
import com.localexplorer.service.ExploreOrderService;
import com.localexplorer.service.ExplorePackageService;
import com.localexplorer.service.OperationLogService;
import com.localexplorer.service.ReviewService;
import com.localexplorer.service.UserInteractionService;
import com.localexplorer.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PageControllerValidationTest {

    private MockMvc mockMvc;

    @Mock
    private CategoryService categoryService;
    @Mock
    private ExploreItemService itemService;
    @Mock
    private ExplorePackageService packageService;
    @Mock
    private ExploreOrderService orderService;
    @Mock
    private EmployeeService employeeService;
    @Mock
    private ReviewService reviewService;
    @Mock
    private OperationLogService operationLogService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserInteractionService interactionService;
    @Mock
    private UserService userService;

    @BeforeEach
    void setUp() {
        CategoryController categoryController = new CategoryController();
        ReflectionTestUtils.setField(categoryController, "categoryService", categoryService);

        ExploreItemController itemController = new ExploreItemController();
        ReflectionTestUtils.setField(itemController, "itemService", itemService);

        ExplorePackageController packageController = new ExplorePackageController();
        ReflectionTestUtils.setField(packageController, "packageService", packageService);

        com.localexplorer.controller.admin.ExploreOrderController adminOrderController =
                new com.localexplorer.controller.admin.ExploreOrderController();
        ReflectionTestUtils.setField(adminOrderController, "orderService", orderService);

        EmployeeController employeeController = new EmployeeController();
        ReflectionTestUtils.setField(employeeController, "employeeService", employeeService);

        com.localexplorer.controller.admin.ReviewController adminReviewController =
                new com.localexplorer.controller.admin.ReviewController();
        ReflectionTestUtils.setField(adminReviewController, "reviewService", reviewService);

        OperationLogController operationLogController = new OperationLogController();
        ReflectionTestUtils.setField(operationLogController, "logService", operationLogService);

        UserManageController userManageController = new UserManageController();
        ReflectionTestUtils.setField(userManageController, "userMapper", userMapper);
        ReflectionTestUtils.setField(userManageController, "orderService", orderService);
        ReflectionTestUtils.setField(userManageController, "interactionService", interactionService);
        ReflectionTestUtils.setField(userManageController, "userService", userService);

        com.localexplorer.controller.user.ExploreOrderController userOrderController =
                new com.localexplorer.controller.user.ExploreOrderController();
        ReflectionTestUtils.setField(userOrderController, "orderService", orderService);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        categoryController,
                        itemController,
                        packageController,
                        adminOrderController,
                        employeeController,
                        adminReviewController,
                        operationLogController,
                        userManageController,
                        userOrderController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void pageEndpointsRejectPageBelowOneBeforeBusinessCode() throws Exception {
        assertPageRejected("/admin/category/page");
        assertPageRejected("/admin/explore-item/page");
        assertPageRejected("/admin/explore-package/page");
        assertPageRejected("/admin/explore-order/page");
        assertPageRejected("/admin/employee/page");
        assertPageRejected("/admin/review/page");
        assertPageRejected("/admin/operation-log/page");
        assertPageRejected("/admin/user-manage/page");
        assertPageRejected("/user/explore-order/page");

        verifyNoInteractions(
                categoryService,
                itemService,
                packageService,
                orderService,
                employeeService,
                reviewService,
                operationLogService,
                userMapper,
                interactionService,
                userService);
    }

    private void assertPageRejected(String path) throws Exception {
        mockMvc.perform(get(path).param("page", "0").param("pageSize", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.msg").value("页码不能小于1"));
    }
}
