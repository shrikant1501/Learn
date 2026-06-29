package com.learnjava.todo.actuator;

import com.learnjava.todo.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

// HealthIndicator: the Actuator extension point for custom health checks.
// Spring discovers every @Component that implements HealthIndicator and rolls it
// up into GET /actuator/health under a key derived from the class name:
//   TodoHealthIndicator → "todo" (strips "HealthIndicator" suffix, lowercases).
//
// Kubernetes uses /actuator/health for liveness and readiness probes —
// if this returns DOWN, K8s will restart or remove the pod from the load balancer.
@Component
@RequiredArgsConstructor
public class TodoHealthIndicator implements HealthIndicator {

    private final TodoRepository todoRepository;

    // health() is called every time /actuator/health is hit (or on a scheduled refresh
    // if you configure management.endpoint.health.cache.time-to-live).
    // Return Health.up() to signal this component is healthy.
    // Return Health.down() to signal a problem — this makes the whole /health status DOWN.
    @Override
    public Health health() {
        try {
            long count = todoRepository.count();
            // withDetail() adds key/value metadata to the health response.
            // These appear under the "details" object in the JSON response.
            // Useful for at-a-glance diagnostics without querying the DB separately.
            return Health.up()
                    .withDetail("totalTodos", count)
                    .withDetail("status", "Todo repository is reachable")
                    .build();
        } catch (Exception e) {
            // If the DB call throws, the repository is unreachable — report DOWN.
            // withDetail("error", ...) surfaces the error message in the health response
            // so operators can diagnose without digging through logs.
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
