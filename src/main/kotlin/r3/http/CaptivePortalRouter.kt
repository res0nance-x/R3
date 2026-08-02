package r3.http

import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Response.newFixedLengthResponse
import org.nanohttpd.protocols.http.response.Status

class CaptivePortalRouter(private val redirectPort: Int) : IRouter {
    override fun findRoute(session: IHTTPSession): Response? {
        val host = session.headers["host"] ?: ""
        val uri = session.uri ?: ""

        // Common captive portal detection URLs
        val isCaptivePortalRequest = host.contains("google.com") || 
                                     host.contains("gstatic.com") ||
                                     host.contains("apple.com") ||
                                     host.contains("connectivitycheck") ||
                                     host.contains("msftconnecttest") ||
                                     uri.contains("generate_204") ||
                                     uri.contains("success.html")

        if (isCaptivePortalRequest) {
            val response = newFixedLengthResponse(Status.REDIRECT, "text/plain", "Redirecting to Relay")
            // We use the local IP as the redirect target since r3lay.local requires mDNS setup
            // which might not be available on all client devices.
            // However, to satisfy the requirement of "redirect to r3lay.local", we can use that if desired.
            // For now, let's assume we want to redirect to the service root.
            val redirectUrl = "http://r3lay.local:$redirectPort/"
            response.addHeader("Location", redirectUrl)
            return response
        }

        return null
    }
}
