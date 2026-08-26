package com.localexplorer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.localexplorer.config.ExportJobProperties;
import com.localexplorer.domain.ExportQuerySnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ExportSnapshotCipher {

    private static final String VERSION = "v1";
    private static final byte[] AAD = "local-explorer-export-snapshot-v1".getBytes(StandardCharsets.UTF_8);
    private static final int IV_BYTES = 12;

    @Autowired private ExportJobProperties properties;
    @Autowired private ObjectMapper objectMapper;

    private final SecureRandom secureRandom = new SecureRandom();

    public void protect(ExportQuerySnapshot snapshot) {
        if (snapshot == null || !hasPii(snapshot)) return;
        Map<String, String> pii = new LinkedHashMap<>();
        pii.put("contactName", snapshot.getContactName());
        pii.put("name", snapshot.getName());
        pii.put("phone", snapshot.getPhone());
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, iv);
            byte[] encrypted = cipher.doFinal(objectMapper.writeValueAsBytes(pii));
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            snapshot.setEncryptedPii(VERSION + "." + encoder.encodeToString(iv) + "."
                    + encoder.encodeToString(encrypted));
        } catch (Exception ex) {
            throw new IllegalStateException("无法加密导出查询快照", ex);
        }
    }

    public void reveal(ExportQuerySnapshot snapshot) {
        if (snapshot == null || snapshot.getEncryptedPii() == null || snapshot.getEncryptedPii().isEmpty()) return;
        try {
            String[] parts = snapshot.getEncryptedPii().split("\\.", -1);
            if (parts.length != 3 || !VERSION.equals(parts[0])) {
                throw new IllegalArgumentException("unsupported snapshot cipher version");
            }
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] iv = decoder.decode(parts[1]);
            byte[] encrypted = decoder.decode(parts[2]);
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, iv);
            Map<String, String> pii = objectMapper.readValue(cipher.doFinal(encrypted),
                    new TypeReference<Map<String, String>>() { });
            snapshot.setContactName(pii.get("contactName"));
            snapshot.setName(pii.get("name"));
            snapshot.setPhone(pii.get("phone"));
        } catch (Exception ex) {
            throw new IllegalStateException("无法解密导出查询快照", ex);
        }
    }

    private Cipher cipher(int mode, byte[] iv) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] key = digest.digest(properties.getSnapshotSecret().getBytes(StandardCharsets.UTF_8));
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(AAD);
        return cipher;
    }

    private boolean hasPii(ExportQuerySnapshot snapshot) {
        return snapshot.getContactName() != null || snapshot.getName() != null || snapshot.getPhone() != null;
    }
}
