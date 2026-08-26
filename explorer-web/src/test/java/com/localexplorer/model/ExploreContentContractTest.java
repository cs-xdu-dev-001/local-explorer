package com.localexplorer.model;

import com.localexplorer.dto.ExploreItemDTO;
import com.localexplorer.dto.ExplorePackageDTO;
import com.localexplorer.entity.ExploreItem;
import com.localexplorer.entity.ExplorePackage;
import com.localexplorer.vo.ExploreItemVO;
import com.localexplorer.vo.ExplorePackageVO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExploreContentContractTest {

    @Test
    void itemModelCarriesOperationalFieldsShownInTheFrontend() {
        assertField(ExploreItemDTO.class, "durationMinutes", Integer.class);
        assertField(ExploreItemDTO.class, "capacity", Integer.class);
        assertField(ExploreItemDTO.class, "booked", Integer.class);
        assertField(ExploreItemDTO.class, "district", String.class);
        assertField(ExploreItemDTO.class, "address", String.class);
        assertField(ExploreItemDTO.class, "meetingPoint", String.class);
        assertField(ExploreItemDTO.class, "cancelPolicy", String.class);

        assertField(ExploreItem.class, "durationMinutes", Integer.class);
        assertField(ExploreItem.class, "capacity", Integer.class);
        assertField(ExploreItem.class, "booked", Integer.class);
        assertField(ExploreItem.class, "district", String.class);
        assertField(ExploreItem.class, "address", String.class);
        assertField(ExploreItem.class, "meetingPoint", String.class);
        assertField(ExploreItem.class, "cancelPolicy", String.class);

        assertField(ExploreItemVO.class, "durationMinutes", Integer.class);
        assertField(ExploreItemVO.class, "capacity", Integer.class);
        assertField(ExploreItemVO.class, "booked", Integer.class);
        assertField(ExploreItemVO.class, "district", String.class);
        assertField(ExploreItemVO.class, "address", String.class);
        assertField(ExploreItemVO.class, "meetingPoint", String.class);
        assertField(ExploreItemVO.class, "cancelPolicy", String.class);
    }

    @Test
    void packageModelCarriesOperationalFieldsShownInTheFrontend() {
        assertField(ExplorePackageDTO.class, "durationMinutes", Integer.class);
        assertField(ExplorePackageDTO.class, "capacity", Integer.class);
        assertField(ExplorePackageDTO.class, "booked", Integer.class);
        assertField(ExplorePackageDTO.class, "district", String.class);
        assertField(ExplorePackageDTO.class, "address", String.class);
        assertField(ExplorePackageDTO.class, "meetingPoint", String.class);
        assertField(ExplorePackageDTO.class, "cancelPolicy", String.class);

        assertField(ExplorePackage.class, "durationMinutes", Integer.class);
        assertField(ExplorePackage.class, "capacity", Integer.class);
        assertField(ExplorePackage.class, "booked", Integer.class);
        assertField(ExplorePackage.class, "district", String.class);
        assertField(ExplorePackage.class, "address", String.class);
        assertField(ExplorePackage.class, "meetingPoint", String.class);
        assertField(ExplorePackage.class, "cancelPolicy", String.class);

        assertField(ExplorePackageVO.class, "durationMinutes", Integer.class);
        assertField(ExplorePackageVO.class, "capacity", Integer.class);
        assertField(ExplorePackageVO.class, "booked", Integer.class);
        assertField(ExplorePackageVO.class, "district", String.class);
        assertField(ExplorePackageVO.class, "address", String.class);
        assertField(ExplorePackageVO.class, "meetingPoint", String.class);
        assertField(ExplorePackageVO.class, "cancelPolicy", String.class);
    }

    private void assertField(Class<?> type, String fieldName, Class<?> fieldType) {
        Field field = null;
        try {
            field = type.getDeclaredField(fieldName);
        } catch (NoSuchFieldException ex) {
            // Assertion below keeps the failure message focused on the missing contract.
        }
        assertNotNull(field, type.getSimpleName() + " missing field " + fieldName);
        assertEquals(fieldType, field.getType(), type.getSimpleName() + "." + fieldName + " type");
    }
}
