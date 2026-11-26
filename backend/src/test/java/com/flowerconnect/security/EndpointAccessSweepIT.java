package com.flowerconnect.security;

import com.flowerconnect.test.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Fail-closed endpoint access sweep (plan task 13.6).
 *
 * <p>Enumerates every handler mapping registered in the running application
 * context via {@link RequestMappingHandlerMapping} and compares it against the
 * reviewed {@link EndpointAccessMatrix}. The test fails if:
 * <ul>
 *   <li>a registered handler is not covered by any matrix row;</li>
 *   <li>a concrete matrix row matches no registered handler (stale row);</li>
 *   <li>two concrete matrix rows overlap on the same path + method;</li>
 *   <li>a matrix row has no explicit access rule.</li>
 * </ul>
 *
 * <p>This catches a newly added controller or route that lacks a matrix entry,
 * and a matrix row left behind after a route is removed. Rows registered only
 * in the {@code dev} profile (Swagger) and the catch-all {@code anyRequest()}
 * rule are excluded from the stale-row check. Rows registered only outside
 * {@code prod} (the payment simulator) are exercised normally in the test
 * profile because {@code test} is not {@code prod}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EndpointAccessSweepIT extends AbstractIntegrationTest {

    private record Ref(String path, HttpMethod method) {}

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyRegisteredHandlerIsCoveredByTheAccessMatrix() {
        Set<Ref> handlers = registeredHandlers();
        List<Ref> uncovered = handlers.stream()
                .filter(h -> EndpointAccessMatrix.ENTRIES.stream()
                        .noneMatch(e -> e.rule() != null
                                && (h.method == null || e.method() == h.method)
                                && antMatches(normalize(e.pathPattern()), h.path)))
                .sorted(Comparator.comparing((Ref r) -> r.method == null ? "ANY" : r.method.name())
                        .thenComparing(r -> r.path))
                .toList();
        if (!uncovered.isEmpty()) {
            String detail = uncovered.stream().map(r -> r.method + " " + r.path).collect(Collectors.joining("\n  "));
            throw new AssertionError(
                    "Handlers registered in the application context are not covered by the access matrix. "
                            + "Add a row to EndpointAccessMatrix for each:\n  " + detail);
        }
    }

    @Test
    void everyConcreteMatrixEntryMatchesARegisteredHandler() {
        Set<Ref> handlers = registeredHandlers();
        List<String> stale = EndpointAccessMatrix.ENTRIES.stream()
                .filter(e -> e.method() != null)
                .filter(e -> !isActuator(e.pathPattern()))
                .filter(e -> !isCatchAll(e.pathPattern()))
                .filter(e -> handlers.stream()
                        .noneMatch(h -> e.method() == h.method
                                && antMatches(normalize(e.pathPattern()), h.path)))
                .map(EndpointAccessSweepIT::desc)
                .sorted()
                .toList();
        if (!stale.isEmpty()) {
            throw new AssertionError(
                    "Access matrix rows match no registered handler (stale rows). Remove them or add the route:\n  "
                            + String.join("\n  ", stale));
        }
    }

    @Test
    void noDuplicateOrOverlappingMatrixRows() {
        Set<String> seen = new LinkedHashSet<>();
        List<String> duplicates = EndpointAccessMatrix.ENTRIES.stream()
                .filter(e -> e.method() != null)
                .filter(e -> !isCatchAll(e.pathPattern()))
                .map(e -> normalize(e.pathPattern()) + "|" + e.method())
                .filter(k -> !seen.add(k))
                .sorted()
                .toList();
        if (!duplicates.isEmpty()) {
            throw new AssertionError("Duplicate matrix rows for the same path + method:\n  " + String.join("\n  ", duplicates));
        }
    }

    @Test
    void everyMatrixEntryHasAnExplicitAccessRule() {
        List<String> ruleless = EndpointAccessMatrix.ENTRIES.stream()
                .filter(e -> e.rule() == null)
                .map(EndpointAccessSweepIT::desc)
                .sorted()
                .toList();
        if (!ruleless.isEmpty()) {
            throw new AssertionError("Matrix rows without an explicit access rule:\n  " + String.join("\n  ", ruleless));
        }
    }

    @Test
    void theCatchAllRuleExists() {
        boolean catchAll = EndpointAccessMatrix.ENTRIES.stream()
                .anyMatch(e -> isCatchAll(e.pathPattern()));
        if (!catchAll) {
            throw new AssertionError("The access matrix must define a catch-all row for anyRequest().authenticated().");
        }
    }

    // ---------------------------------------------------------------- helpers

    private Set<Ref> registeredHandlers() {
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();
        Set<Ref> refs = new LinkedHashSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            RequestMappingInfo info = entry.getKey();
            Set<String> paths = pathStrings(info);
            Set<HttpMethod> methods = httpMethods(info);
            if (methods.isEmpty()) {
                methods = Collections.singleton(null);
            }
            for (String path : paths) {
                for (HttpMethod method : methods) {
                    refs.add(new Ref(path, method));
                }
            }
        }
        return refs;
    }

    private static Set<String> pathStrings(RequestMappingInfo info) {
        Set<PathPattern> patterns = info.getPathPatternsCondition().getPatterns();
        return patterns.stream()
                .map(PathPattern::getPatternString)
                .map(EndpointAccessSweepIT::normalize)
                .collect(Collectors.toSet());
    }

    private static Set<HttpMethod> httpMethods(RequestMappingInfo info) {
        return info.getMethodsCondition().getMethods().stream()
                .map(EndpointAccessSweepIT::toHttpMethod)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static HttpMethod toHttpMethod(RequestMethod rm) {
        return switch (rm) {
            case GET -> HttpMethod.GET;
            case POST -> HttpMethod.POST;
            case PUT -> HttpMethod.PUT;
            case PATCH -> HttpMethod.PATCH;
            case DELETE -> HttpMethod.DELETE;
            case OPTIONS -> HttpMethod.OPTIONS;
            case HEAD -> HttpMethod.HEAD;
            case TRACE -> HttpMethod.TRACE;
            default -> null;
        };
    }

    private static boolean isActuator(String pathPattern) {
        return pathPattern.startsWith("/actuator");
    }

    private static boolean isCatchAll(String pathPattern) {
        return "/**".equals(pathPattern);
    }

    private static String normalize(String s) {
        return s.replaceAll("/\\{[^/]+\\}", "/{}");
    }

    /**
     * Ant-style match where {@code {}} matches exactly one path segment and
     * {@code **} matches zero or more segments.
     */
    private static boolean antMatches(String pattern, String path) {
        String[] p = pattern.split("/");
        String[] a = path.split("/");
        return match(p, 0, a, 0);
    }

    private static boolean match(String[] p, int pi, String[] a, int ai) {
        while (pi < p.length && ai < a.length) {
            if (p[pi].equals("**")) {
                for (int k = ai; k <= a.length; k++) {
                    if (matchRest(p, pi + 1, a, k)) {
                        return true;
                    }
                }
                return false;
            }
            if (!p[pi].equals(a[ai]) && !p[pi].equals("{}")) {
                return false;
            }
            pi++;
            ai++;
        }
        return pi == p.length && ai == a.length;
    }

    private static boolean matchRest(String[] p, int pi, String[] a, int ai) {
        while (pi < p.length && ai < a.length) {
            if (p[pi].equals("**")) {
                for (int k = ai; k <= a.length; k++) {
                    if (matchRest(p, pi + 1, a, k)) {
                        return true;
                    }
                }
                return false;
            }
            if (!p[pi].equals(a[ai]) && !p[pi].equals("{}")) {
                return false;
            }
            pi++;
            ai++;
        }
        return pi == p.length && ai == a.length;
    }

    private static String desc(EndpointAccessMatrix.Entry e) {
        return (e.method() == null ? "ANY" : e.method()) + " " + e.pathPattern() + " -> " + e.rule();
    }
}

