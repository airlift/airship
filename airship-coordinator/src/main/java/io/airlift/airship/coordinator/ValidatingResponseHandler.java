package io.airlift.airship.coordinator;

import com.google.common.collect.ImmutableMap;
import io.airlift.http.client.Request;
import io.airlift.http.client.Response;
import io.airlift.http.client.ResponseHandler;
import org.apache.bval.jsr303.ApacheValidationProvider;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class ValidatingResponseHandler<T, E extends Exception>
        implements ResponseHandler<T, E>
{
    private static final Validator VALIDATOR = Validation.byProvider(ApacheValidationProvider.class).configure().buildValidatorFactory().getValidator();

    private final ResponseHandler<T, E> handler;

    public static <T, E extends Exception> ValidatingResponseHandler<T, E> validate(ResponseHandler<T, E> handler)
    {
        return new ValidatingResponseHandler<>(handler);
    }

    private ValidatingResponseHandler(ResponseHandler<T, E> handler)
    {
        this.handler = checkNotNull(handler, "handler is null");
    }

    @Override
    public E handleException(Request request, Exception exception)
    {
        return handler.handleException(request, exception);
    }

    @Override
    public T handle(Request request, Response response)
            throws E
    {
        T result = handler.handle(request, response);
        Map<String, String> violations = validateObject(result);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException("Response result is invalid: " + violations);
        }
        return result;
    }

    private static <T> Map<String, String> validateObject(T object)
    {
        ImmutableMap.Builder<String, String> map = ImmutableMap.builder();
        for (ConstraintViolation<T> violation : VALIDATOR.validate(object)) {
            map.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        return map.build();
    }
}
