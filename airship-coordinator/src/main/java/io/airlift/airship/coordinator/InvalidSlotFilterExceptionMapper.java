package io.airlift.airship.coordinator;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;

public class InvalidSlotFilterExceptionMapper
        implements ExceptionMapper<InvalidSlotFilterException>
{
    @Override
    public Response toResponse(InvalidSlotFilterException exception)
    {
        return Response.status(Status.BAD_REQUEST).entity("No slot filter provided").build();
    }
}
