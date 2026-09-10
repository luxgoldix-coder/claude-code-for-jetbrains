package dev.lain.claudejb.session

import dev.lain.claudejb.protocol.AuthStatusInfo
import dev.lain.claudejb.protocol.ContextUsage
import dev.lain.claudejb.protocol.RateLimitInfo
import kotlinx.serialization.json.JsonObject

class SessionSignals {

    @Volatile var rateLimit: RateLimitInfo? = null
        internal set

    @Volatile var rateLimits: Map<String, RateLimitInfo> = emptyMap()
        internal set

    @Volatile var sessionState: String? = null
        internal set

    @Volatile var authStatus: AuthStatusInfo? = null
        internal set

    @Volatile var lastSessionCost: JsonObject? = null
        internal set

    @Volatile var lastContextUsage: ContextUsage? = null
        internal set
}
