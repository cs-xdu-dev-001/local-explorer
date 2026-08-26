package com.localexplorer.model;

import com.localexplorer.dto.CategoryPageQueryDTO;
import com.localexplorer.dto.EmployeePageQueryDTO;
import com.localexplorer.dto.ExploreItemPageQueryDTO;
import com.localexplorer.dto.ExploreOrderPageQueryDTO;
import com.localexplorer.dto.ExplorePackagePageQueryDTO;
import com.localexplorer.dto.OperationLogPageQueryDTO;
import com.localexplorer.dto.ReviewPageQueryDTO;
import com.localexplorer.dto.UserPageQueryDTO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class PageQueryValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void pageQueriesDefaultToFirstPageAndTenRows() {
        pageQuerySuppliers().forEach(supplier -> {
            Object dto = supplier.get();
            assertThat(ReflectionTestUtils.getField(dto, "page")).isEqualTo(1);
            assertThat(ReflectionTestUtils.getField(dto, "pageSize")).isEqualTo(10);
        });
    }

    @Test
    void pageQueriesRejectUnsafePageRanges() {
        pageQuerySuppliers().forEach(supplier -> {
            assertInvalidProperty(supplier.get(), "page", 0);
            assertInvalidProperty(supplier.get(), "page", 100001);
            assertInvalidProperty(supplier.get(), "pageSize", 0);
            assertInvalidProperty(supplier.get(), "pageSize", 101);
        });
    }

    @Test
    void pageQueriesValidateFilterRanges() {
        assertInvalidProperty(new CategoryPageQueryDTO(), "type", 3);
        assertInvalidProperty(new ExploreItemPageQueryDTO(), "status", 2);
        assertInvalidProperty(new ExplorePackagePageQueryDTO(), "categoryId", 0);
        assertInvalidProperty(new ExploreOrderPageQueryDTO(), "orderType", 3);
        assertInvalidProperty(new ExploreOrderPageQueryDTO(), "status", 5);
        assertInvalidProperty(new ReviewPageQueryDTO(), "minRating", 6);
        assertInvalidProperty(new OperationLogPageQueryDTO(), "operatorId", 0L);
        assertInvalidProperty(new OperationLogPageQueryDTO(), "keyword", repeat('查', 129));
        assertInvalidProperty(new OperationLogPageQueryDTO(), "requestMethod", "TRACE");
        assertInvalidProperty(new UserPageQueryDTO(), "phone", repeat('1', 33));
    }

    private List<Supplier<Object>> pageQuerySuppliers() {
        return Arrays.asList(
                CategoryPageQueryDTO::new,
                EmployeePageQueryDTO::new,
                ExploreItemPageQueryDTO::new,
                ExplorePackagePageQueryDTO::new,
                ExploreOrderPageQueryDTO::new,
                ReviewPageQueryDTO::new,
                OperationLogPageQueryDTO::new,
                UserPageQueryDTO::new
        );
    }

    private void assertInvalidProperty(Object bean, String property, Object invalidValue) {
        ReflectionTestUtils.setField(bean, property, invalidValue);
        Set<ConstraintViolation<Object>> violations = validator.validate(bean);
        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(property);
    }

    private String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }
}
