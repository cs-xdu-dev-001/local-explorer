package com.localexplorer.model;

import com.localexplorer.dto.CategoryDTO;
import com.localexplorer.dto.EmployeeDTO;
import com.localexplorer.dto.ExploreItemDTO;
import com.localexplorer.dto.ExplorePackageDTO;
import com.localexplorer.dto.MerchantInfoDTO;
import com.localexplorer.dto.ReviewDTO;
import com.localexplorer.dto.UserDTO;
import com.localexplorer.entity.ExplorePackageItem;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WriteDtoValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void categoryRejectsBlankNameInvalidTypeAndNegativeSort() {
        CategoryDTO dto = validCategory();
        dto.setName("  ");
        assertInvalidProperty(dto, "name");

        dto = validCategory();
        dto.setType(3);
        assertInvalidProperty(dto, "type");

        dto = validCategory();
        dto.setSort(-1);
        assertInvalidProperty(dto, "sort");
    }

    @Test
    void itemRejectsMissingCoreFieldsAndUnsafeRanges() {
        ExploreItemDTO dto = validItem();
        dto.setName(" ");
        assertInvalidProperty(dto, "name");

        dto = validItem();
        dto.setCategoryId(null);
        assertInvalidProperty(dto, "categoryId");

        dto = validItem();
        dto.setPrice(new BigDecimal("-0.01"));
        assertInvalidProperty(dto, "price");

        dto = validItem();
        dto.setCapacity(0);
        assertInvalidProperty(dto, "capacity");

        dto = validItem();
        dto.setDescription(repeat('x', 256));
        assertInvalidProperty(dto, "description");
    }

    @Test
    void packageRequiresValidCoreFieldsAndAtLeastOneItem() {
        ExplorePackageDTO dto = validPackage();
        dto.setName("");
        assertInvalidProperty(dto, "name");

        dto = validPackage();
        dto.setPrice(new BigDecimal("0"));
        assertInvalidProperty(dto, "price");

        dto = validPackage();
        dto.setPackageItems(Collections.emptyList());
        assertInvalidProperty(dto, "packageItems");
    }

    @Test
    void packageValidatesNestedItemIdAndCopies() {
        ExplorePackageDTO dto = validPackage();
        dto.getPackageItems().get(0).setItemId(null);
        assertInvalidProperty(dto, "packageItems[0].itemId");

        dto = validPackage();
        dto.getPackageItems().get(0).setCopies(0);
        assertInvalidProperty(dto, "packageItems[0].copies");
    }

    @Test
    void employeeRejectsBlankIdentityAndMalformedOptionalFields() {
        EmployeeDTO dto = validEmployee();
        dto.setUsername(" ");
        assertInvalidProperty(dto, "username");

        dto = validEmployee();
        dto.setName("");
        assertInvalidProperty(dto, "name");

        dto = validEmployee();
        dto.setPhone("123");
        assertInvalidProperty(dto, "phone");

        dto = validEmployee();
        dto.setSex("unknown");
        assertInvalidProperty(dto, "sex");
    }

    @Test
    void userRejectsBlankNameMalformedPhoneAndOversizedAvatar() {
        UserDTO dto = validUser();
        dto.setName(" ");
        assertInvalidProperty(dto, "name");

        dto = validUser();
        dto.setPhone("123");
        assertInvalidProperty(dto, "phone");

        dto = validUser();
        dto.setAvatar(repeat('x', 501));
        assertInvalidProperty(dto, "avatar");
    }

    @Test
    void reviewRejectsInvalidRatingAndOversizedContent() {
        ReviewDTO dto = new ReviewDTO();
        dto.setOrderId(1L);
        dto.setRating(6);
        dto.setContent("good");
        assertInvalidProperty(dto, "rating");

        dto.setRating(5);
        dto.setContent(repeat('x', 501));
        assertInvalidProperty(dto, "content");

        dto.setContent("good");
        dto.setReplyContent(repeat('x', 501));
        assertInvalidProperty(dto, "replyContent");
    }

    @Test
    void merchantRejectsBlankRequiredFieldsAndOversizedNotice() {
        MerchantInfoDTO dto = validMerchant();
        dto.setName(" ");
        assertInvalidProperty(dto, "name");

        dto = validMerchant();
        dto.setPhone("");
        assertInvalidProperty(dto, "phone");

        dto = validMerchant();
        dto.setNotice(repeat('x', 256));
        assertInvalidProperty(dto, "notice");
    }

    private void assertInvalidProperty(Object bean, String property) {
        Set<ConstraintViolation<Object>> violations = validator.validate(bean);
        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(property);
    }

    private CategoryDTO validCategory() {
        CategoryDTO dto = new CategoryDTO();
        dto.setType(1);
        dto.setName("城市漫游");
        dto.setSort(0);
        return dto;
    }

    private ExploreItemDTO validItem() {
        ExploreItemDTO dto = new ExploreItemDTO();
        dto.setName("城市漫步");
        dto.setCategoryId(1L);
        dto.setPrice(new BigDecimal("39.00"));
        dto.setDurationMinutes(90);
        dto.setCapacity(20);
        dto.setStatus(1);
        return dto;
    }

    private ExplorePackageDTO validPackage() {
        ExplorePackageDTO dto = new ExplorePackageDTO();
        dto.setName("周末体验套餐");
        dto.setCategoryId(2L);
        dto.setPrice(new BigDecimal("99.00"));
        dto.setDurationMinutes(180);
        dto.setCapacity(20);
        dto.setStatus(1);
        dto.setPackageItems(Collections.singletonList(ExplorePackageItem.builder()
                .itemId(1001L)
                .copies(1)
                .build()));
        return dto;
    }

    private EmployeeDTO validEmployee() {
        EmployeeDTO dto = new EmployeeDTO();
        dto.setUsername("operator");
        dto.setName("运营人员");
        dto.setPhone("13800001111");
        dto.setSex("1");
        dto.setIdNumber("11010519491231002X");
        return dto;
    }

    private UserDTO validUser() {
        UserDTO dto = new UserDTO();
        dto.setName("张三");
        dto.setPhone("13800001111");
        dto.setSex("1");
        dto.setIdNumber("11010519491231002X");
        return dto;
    }

    private MerchantInfoDTO validMerchant() {
        MerchantInfoDTO dto = new MerchantInfoDTO();
        dto.setName("本地探索");
        dto.setPhone("13800001111");
        dto.setAddress("城市中心广场");
        dto.setBusinessHours("09:00-21:00");
        return dto;
    }

    private String repeat(char value, int count) {
        return String.join("", Collections.nCopies(count, String.valueOf(value)));
    }
}
