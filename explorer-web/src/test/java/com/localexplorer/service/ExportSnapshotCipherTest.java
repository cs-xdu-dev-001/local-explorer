package com.localexplorer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localexplorer.config.ExportJobProperties;
import com.localexplorer.domain.ExportQuerySnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExportSnapshotCipherTest {

    private ObjectMapper objectMapper;
    private ExportSnapshotCipher cipher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        cipher = cipher("test-export-snapshot-secret");
    }

    @Test
    void encryptsPiiBeforeSnapshotSerializationAndRestoresItForExecution() throws Exception {
        ExportQuerySnapshot snapshot = ExportQuerySnapshot.builder()
                .exportType("USER").fileFormat("CSV")
                .contactName("张三").name("林夏").phone("13800001111")
                .build();

        cipher.protect(snapshot);
        String json = objectMapper.writeValueAsString(snapshot);

        assertThat(json).contains("encryptedPii")
                .doesNotContain("张三", "林夏", "13800001111", "contactName", "phone");
        ExportQuerySnapshot restored = objectMapper.readValue(json, ExportQuerySnapshot.class);
        cipher.reveal(restored);
        assertThat(restored.getContactName()).isEqualTo("张三");
        assertThat(restored.getName()).isEqualTo("林夏");
        assertThat(restored.getPhone()).isEqualTo("13800001111");
    }

    @Test
    void rejectsSnapshotEncryptedWithAnotherSecret() throws Exception {
        ExportQuerySnapshot snapshot = ExportQuerySnapshot.builder().phone("13800001111").build();
        cipher.protect(snapshot);
        ExportQuerySnapshot restored = objectMapper.readValue(objectMapper.writeValueAsString(snapshot),
                ExportQuerySnapshot.class);

        assertThatThrownBy(() -> cipher("another-secret").reveal(restored))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("无法解密导出查询快照");
    }

    private ExportSnapshotCipher cipher(String secret) {
        ExportJobProperties properties = new ExportJobProperties();
        properties.setSnapshotSecret(secret);
        ExportSnapshotCipher target = new ExportSnapshotCipher();
        ReflectionTestUtils.setField(target, "properties", properties);
        ReflectionTestUtils.setField(target, "objectMapper", objectMapper);
        return target;
    }
}
