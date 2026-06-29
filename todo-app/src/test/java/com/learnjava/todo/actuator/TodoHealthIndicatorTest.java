package com.learnjava.todo.actuator;

import com.learnjava.todo.repository.TodoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// Pure unit test — no Spring context, no DB.
// TodoHealthIndicator has a single dependency (TodoRepository) which Mockito mocks.
// @InjectMocks works here because the constructor only needs the repository —
// no MeterRegistry or other framework types are involved.
@ExtendWith(MockitoExtension.class)
class TodoHealthIndicatorTest {

    @Mock
    private TodoRepository todoRepository;

    @InjectMocks
    private TodoHealthIndicator healthIndicator;

    // -----------------------------------------------------------------------
    // UP — repository is reachable
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("health: returns UP with totalTodos detail when repository is reachable")
    void health_repositoryReachable_returnsUp() {
        // Arrange: repository.count() returns 5 successfully
        when(todoRepository.count()).thenReturn(5L);

        // Act
        Health health = healthIndicator.health();

        // Assert: status is UP
        assertThat(health.getStatus()).isEqualTo(Status.UP);

        // Assert: detail contains totalTodos with the count from the repository
        // getDetails() returns Map<String, Object> — we cast to Long for comparison.
        assertThat(health.getDetails()).containsKey("totalTodos");
        assertThat(health.getDetails().get("totalTodos")).isEqualTo(5L);

        // Assert: a human-readable status message is present
        assertThat(health.getDetails()).containsKey("status");
    }

    @Test
    @DisplayName("health: returns UP with 0 todos when repository is empty")
    void health_emptyRepository_returnsUpWithZero() {
        when(todoRepository.count()).thenReturn(0L);

        Health health = healthIndicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails().get("totalTodos")).isEqualTo(0L);
    }

    // -----------------------------------------------------------------------
    // DOWN — repository throws
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("health: returns DOWN with error detail when repository throws")
    void health_repositoryThrows_returnsDown() {
        // Arrange: simulate a DB connectivity failure
        when(todoRepository.count()).thenThrow(new RuntimeException("Connection refused"));

        // Act
        Health health = healthIndicator.health();

        // Assert: status is DOWN — this is what triggers K8s to restart the pod
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);

        // Assert: error detail is present so operators can diagnose without log diving
        assertThat(health.getDetails()).containsKey("error");
        assertThat(health.getDetails().get("error")).isEqualTo("Connection refused");
    }
}
