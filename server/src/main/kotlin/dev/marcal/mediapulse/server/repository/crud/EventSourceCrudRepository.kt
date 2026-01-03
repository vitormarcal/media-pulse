package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.EventSource
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface EventSourceCrudRepository : JpaRepository<EventSource, Long> {
    fun findByFingerprint(fingerprint: String): EventSource?

    @Query(
        """
        select count(es) from EventSource es
        where (:all = true or es.status in :statuses)
          and (:providersEmpty = true or es.provider in :providers)
        """,
    )
    fun countForReprocess(
        @Param("all") all: Boolean,
        @Param("statuses") statuses: List<EventSource.Status>,
        @Param("providersEmpty") providersEmpty: Boolean,
        @Param("providers") providers: List<String>,
    ): Long

    @Query(
        """
        select es from EventSource es
        where es.id > :afterId
          and (:all = true or es.status in :statuses)
          and (:providersEmpty = true or es.provider in :providers)
        order by es.id asc
        """,
    )
    fun findBatchForReprocess(
        @Param("afterId") afterId: Long,
        @Param("all") all: Boolean,
        @Param("statuses") statuses: List<EventSource.Status>,
        @Param("providersEmpty") providersEmpty: Boolean,
        @Param("providers") providers: List<String>,
        pageable: Pageable,
    ): List<EventSource>
}
