package dev.marcal.mediapulse.server.repository

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository

@Repository
class PersonFilmographyRepository(
    private val entityManager: EntityManager,
) {
    enum class MediaType { MOVIE, SHOW }

    data class PersonRecord(
        val id: Long,
        val tmdbId: String,
        val name: String,
        val profileUrl: String?,
    )

    data class MemberSnapshot(
        val tmdbId: String,
        val title: String,
        val originalTitle: String?,
        val year: Int?,
        val overview: String?,
        val posterUrl: String?,
        val backdropUrl: String?,
        val roleLabel: String,
    )

    data class MemberRecord(
        val snapshot: MemberSnapshot,
        val localId: Long?,
        val localSlug: String?,
    )

    fun findPerson(personId: Long): PersonRecord? =
        (
            entityManager
                .createNativeQuery(
                    """SELECT id, tmdb_id, name, profile_url FROM people WHERE id = :personId LIMIT 1""",
                ).setParameter("personId", personId)
                .resultList
                .firstOrNull() as Array<*>?
        )?.let {
            PersonRecord((it[0] as Number).toLong(), it[1] as String, it[2] as String, it[3] as String?)
        }

    fun findPendingPersonIds(
        mediaType: MediaType,
        limit: Int,
    ): List<Long> =
        entityManager
            .createNativeQuery(
                """
                SELECT person.id
                FROM people person
                LEFT JOIN person_filmography_syncs sync
                  ON sync.person_id = person.id AND sync.media_type = :mediaType
                WHERE sync.synced_at IS NULL
                  AND (sync.sync_attempted_at IS NULL OR sync.sync_attempted_at <= NOW() - INTERVAL '1 day')
                ORDER BY sync.sync_attempted_at NULLS FIRST, person.id
                LIMIT :limit
                """.trimIndent(),
            ).setParameter("mediaType", mediaType.name)
            .setParameter("limit", limit)
            .resultList
            .map { (it as Number).toLong() }

    fun findMembers(
        personId: Long,
        mediaType: MediaType,
    ): List<MemberRecord> {
        val localTable = if (mediaType == MediaType.MOVIE) "movies" else "tv_shows"
        return entityManager
            .createNativeQuery(
                """
                SELECT member.tmdb_id, member.title, member.original_title, member.release_year,
                       member.overview, member.poster_url, member.backdrop_url, member.role_label,
                       local.id, local.slug
                FROM person_filmography_members member
                LEFT JOIN $localTable local ON local.tmdb_id = member.tmdb_id
                WHERE member.person_id = :personId AND member.media_type = :mediaType
                ORDER BY member.position, member.id
                """.trimIndent(),
            ).setParameter("personId", personId)
            .setParameter("mediaType", mediaType.name)
            .resultList
            .map { row ->
                val fields = row as Array<*>
                MemberRecord(
                    MemberSnapshot(
                        fields[0] as String,
                        fields[1] as String,
                        fields[2] as String?,
                        (fields[3] as Number?)?.toInt(),
                        fields[4] as String?,
                        fields[5] as String?,
                        fields[6] as String?,
                        fields[7] as String,
                    ),
                    (fields[8] as Number?)?.toLong(),
                    fields[9] as String?,
                )
            }
    }

    fun replaceSnapshot(
        personId: Long,
        mediaType: MediaType,
        members: List<MemberSnapshot>,
    ) {
        entityManager
            .createNativeQuery(
                """DELETE FROM person_filmography_members WHERE person_id = :personId AND media_type = :mediaType""",
            ).setParameter("personId", personId)
            .setParameter("mediaType", mediaType.name)
            .executeUpdate()
        members.forEachIndexed { position, member ->
            entityManager
                .createNativeQuery(
                    """
                    INSERT INTO person_filmography_members(
                      person_id, media_type, tmdb_id, title, original_title, release_year, overview,
                      poster_url, backdrop_url, role_label, position, updated_at
                    ) VALUES (
                      :personId, :mediaType, :tmdbId, :title, :originalTitle, :releaseYear, :overview,
                      :posterUrl, :backdropUrl, :roleLabel, :position, NOW()
                    )
                    """.trimIndent(),
                ).setParameter("personId", personId)
                .setParameter("mediaType", mediaType.name)
                .setParameter("tmdbId", member.tmdbId)
                .setParameter("title", member.title)
                .setParameter("originalTitle", member.originalTitle)
                .setParameter("releaseYear", member.year)
                .setParameter("overview", member.overview)
                .setParameter("posterUrl", member.posterUrl)
                .setParameter("backdropUrl", member.backdropUrl)
                .setParameter("roleLabel", member.roleLabel)
                .setParameter("position", position)
                .executeUpdate()
        }
        entityManager
            .createNativeQuery(
                """
                INSERT INTO person_filmography_syncs(person_id, media_type, synced_at, sync_attempted_at)
                VALUES (:personId, :mediaType, NOW(), NOW())
                ON CONFLICT (person_id, media_type) DO UPDATE
                SET synced_at = NOW(), sync_attempted_at = NOW(), sync_error = NULL
                """.trimIndent(),
            ).setParameter("personId", personId)
            .setParameter("mediaType", mediaType.name)
            .executeUpdate()
    }

    fun markFailure(
        personId: Long,
        mediaType: MediaType,
        error: String,
    ) {
        entityManager
            .createNativeQuery(
                """
                INSERT INTO person_filmography_syncs(person_id, media_type, sync_attempted_at, sync_error)
                VALUES (:personId, :mediaType, NOW(), :error)
                ON CONFLICT (person_id, media_type) DO UPDATE
                SET sync_attempted_at = NOW(), sync_error = EXCLUDED.sync_error
                """.trimIndent(),
            ).setParameter("personId", personId)
            .setParameter("mediaType", mediaType.name)
            .setParameter("error", error.take(500))
            .executeUpdate()
    }
}
