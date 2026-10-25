package rw.ac.rca.cloudsphere.common.web;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import rw.ac.rca.cloudsphere.common.exception.ApiException;
import rw.ac.rca.cloudsphere.identity.AuthController;
import rw.ac.rca.cloudsphere.identity.LoginRequest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class RestExceptionHandlerTest {

    private final RestExceptionHandler handler = new RestExceptionHandler();

    @Test
    void handle_notFoundAndValidation_returnsStatusAndDetail() throws Exception {
        MDC.put(TraceIdFilter.MDC_KEY, "trace-1");
        try {
            ProblemDetail api = handler.handleApi(ApiException.notFound("missing"), request("/missing"));
            assertEquals(404, api.getStatus());
            assertEquals("not-found", api.getProperties().get("code"));
            assertEquals("trace-1", api.getProperties().get("traceId"));
            assertEquals("missing", api.getDetail());

            MethodParameter parameter = new MethodParameter(
                    AuthController.class.getDeclaredMethod("login", LoginRequest.class, String.class), 0);
            var binding = new BeanPropertyBindingResult(new LoginRequest("", ""), "request");
            binding.addError(new FieldError("request", "email", "must not be blank"));
            binding.addError(new FieldError("request", "password", "must not be blank"));
            ProblemDetail validation = handler.handleValidation(
                    new MethodArgumentNotValidException(parameter, binding), request("/api/v1/auth/login"));
            assertEquals(400, validation.getStatus());
            assertTrue(validation.getDetail().contains("email"));
            assertTrue(validation.getDetail().contains("password"));
        } finally {
            MDC.clear();
        }
    }

    @Test
    void handle_constraintAuthAndUnknown_returnsMappedStatus() {
        ProblemDetail constraint = handler.handleConstraint(
                new ConstraintViolationException("qty must be positive", Set.of()), request("/cart"));
        assertEquals(400, constraint.getStatus());
        assertEquals("qty must be positive", constraint.getDetail());

        ProblemDetail unreadable = handler.handleUnreadable(request("/checkout"));
        assertEquals("malformed-json", unreadable.getProperties().get("code"));

        assertEquals(403, handler.handleDenied(request("/admin")).getStatus());
        assertEquals(401, handler.handleAuth(request("/orders")).getStatus());

        ProblemDetail unknown = handler.handleUnknown(new IllegalStateException("boom"), request("/boom"));
        assertEquals(500, unknown.getStatus());
        assertEquals("internal-error", unknown.getProperties().get("code"));

        assertEquals(HttpStatus.FORBIDDEN, ApiException.forbidden("no").status());
        assertEquals("locked", ApiException.locked("no").code());
        assertEquals(HttpStatus.BAD_REQUEST, ApiException.badRequest("no").status());
        assertEquals(HttpStatus.CONFLICT, ApiException.conflict("no").status());
        assertEquals(HttpStatus.UNAUTHORIZED, ApiException.unauthorized("no").status());
    }

    private static MockHttpServletRequest request(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }
}
