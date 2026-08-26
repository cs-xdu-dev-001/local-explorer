package com.localexplorer.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ExportJobStatusTest {

    @Test
    void allowsOnlyDeclaredLifecycleTransitions() {
        Set<String> allowed = new HashSet<>(Arrays.asList(
                "PENDING->RUNNING", "PENDING->CANCELED",
                "RUNNING->SUCCEEDED", "RUNNING->PENDING", "RUNNING->FAILED", "RUNNING->CANCELED",
                "SUCCEEDED->EXPIRED"));
        for (ExportJobStatus from : ExportJobStatus.values()) {
            for (ExportJobStatus to : ExportJobStatus.values()) {
                assertThat(from.canTransitionTo(to))
                        .as("transition %s->%s", from, to)
                        .isEqualTo(allowed.contains(from + "->" + to));
            }
        }
    }

    @Test
    void exposesTerminalAndDownloadableStatesExplicitly() {
        assertThat(ExportJobStatus.SUCCEEDED.isDownloadable()).isTrue();
        assertThat(ExportJobStatus.PENDING.isDownloadable()).isFalse();
        assertThat(ExportJobStatus.FAILED.isTerminal()).isTrue();
        assertThat(ExportJobStatus.CANCELED.isTerminal()).isTrue();
        assertThat(ExportJobStatus.EXPIRED.isTerminal()).isTrue();
        assertThat(ExportJobStatus.RUNNING.isTerminal()).isFalse();
    }
}
