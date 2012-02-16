package com.proofpoint.galaxy.shared;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;

public class VersionConflictExceptionMapper implements ExceptionMapper<VersionConflictException>
{
    @Override
    public Response toResponse(VersionConflictException exception)
    {
        return Response.status(Status.CONFLICT)
                .header(exception.getName(), exception.getVersion())
                .build();
    }
}
