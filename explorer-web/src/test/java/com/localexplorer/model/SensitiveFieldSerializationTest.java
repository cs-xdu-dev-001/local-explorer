package com.localexplorer.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localexplorer.entity.Employee;
import com.localexplorer.entity.User;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveFieldSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void employeePasswordIsNeverSerialized() throws Exception {
        Employee employee = Employee.builder()
                .id(1L)
                .username("admin")
                .password("e10adc3949ba59abbe56e057f20f883e")
                .build();

        String json = objectMapper.writeValueAsString(employee);

        assertThat(json).doesNotContain("password", "e10adc3949ba59abbe56e057f20f883e");
    }

    @Test
    void userPasswordIsNeverSerialized() throws Exception {
        User user = User.builder()
                .id(1L)
                .phone("13800001111")
                .password("e10adc3949ba59abbe56e057f20f883e")
                .build();

        String json = objectMapper.writeValueAsString(user);

        assertThat(json).doesNotContain("password", "e10adc3949ba59abbe56e057f20f883e");
    }

    @Test
    void employeePageQueryDoesNotReadPasswordColumn() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("mapper/EmployeeMapper.xml")) {
            assertThat(input).isNotNull();
            org.w3c.dom.Document document = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(input);
            org.w3c.dom.NodeList selects = document.getElementsByTagName("select");
            String pageQuerySql = "";
            for (int index = 0; index < selects.getLength(); index++) {
                org.w3c.dom.Node select = selects.item(index);
                if ("pageQuery".equals(select.getAttributes().getNamedItem("id").getNodeValue())) {
                    pageQuerySql = select.getTextContent().replaceAll("\\s+", " ").trim().toLowerCase();
                    break;
                }
            }

            assertThat(pageQuerySql)
                    .isNotBlank()
                    .doesNotContain("select *", "password");
        }
    }
}
