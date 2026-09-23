package r3.http

import org.nanohttpd.protocols.http.response.Status

open class HttpException(
	val status: Status,
	message: String,
	val headers: Map<String, String> = emptyMap()
) : RuntimeException(message)

class UnauthorizedException(
	message: String = "Unauthorized",
	headers: Map<String, String> = emptyMap()
) : HttpException(Status.UNAUTHORIZED, message, headers)

class ForbiddenException(
	message: String = "Forbidden",
	headers: Map<String, String> = emptyMap()
) : HttpException(Status.FORBIDDEN, message, headers)

class BadRequestException(
	message: String = "Bad Request",
	headers: Map<String, String> = emptyMap()
) : HttpException(Status.BAD_REQUEST, message, headers)

class NotFoundException(
	message: String = "Not Found",
	headers: Map<String, String> = emptyMap()
) : HttpException(Status.NOT_FOUND, message, headers)
