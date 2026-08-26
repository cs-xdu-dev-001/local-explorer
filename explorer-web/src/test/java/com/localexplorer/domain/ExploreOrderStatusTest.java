package com.localexplorer.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExploreOrderStatusTest {

    @Test
    void stateMachineAllowsOnlyDocumentedTransitions() {
        assertThat(ExploreOrderStatus.PENDING.canTransitionTo(ExploreOrderStatus.CONFIRMED)).isTrue();
        assertThat(ExploreOrderStatus.PENDING.canTransitionTo(ExploreOrderStatus.CANCELED)).isTrue();
        assertThat(ExploreOrderStatus.PENDING.canTransitionTo(ExploreOrderStatus.EXPIRED)).isTrue();
        assertThat(ExploreOrderStatus.CONFIRMED.canTransitionTo(ExploreOrderStatus.COMPLETED)).isTrue();
        assertThat(ExploreOrderStatus.CONFIRMED.canTransitionTo(ExploreOrderStatus.CANCELED)).isTrue();

        assertThat(ExploreOrderStatus.PENDING.canTransitionTo(ExploreOrderStatus.COMPLETED)).isFalse();
        assertThat(ExploreOrderStatus.COMPLETED.canTransitionTo(ExploreOrderStatus.CANCELED)).isFalse();
        assertThat(ExploreOrderStatus.EXPIRED.canTransitionTo(ExploreOrderStatus.CONFIRMED)).isFalse();
    }

    @Test
    void stateLookupRejectsUnknownDatabaseValue() {
        assertThat(ExploreOrderStatus.fromCode(4)).isEqualTo(ExploreOrderStatus.EXPIRED);
        assertThatThrownBy(() -> ExploreOrderStatus.fromCode(99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }
}
