package io.github.chan808.reservation.auth.application.port

import io.github.chan808.reservation.auth.domain.RefreshTokenSession

interface TokenStore {
    fun save(sid: String, session: RefreshTokenSession, ttlSeconds: Long)
    fun find(sid: String): RefreshTokenSession?
    fun deleteSession(memberId: Long, sid: String)
    fun tryLock(sid: String): Boolean
    fun releaseLock(sid: String)
    fun addSession(memberId: Long, sid: String)
    fun deleteAllSessionsForMember(memberId: Long)
}
