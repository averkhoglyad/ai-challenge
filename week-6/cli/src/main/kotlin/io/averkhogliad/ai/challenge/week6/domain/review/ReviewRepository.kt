package io.averkhogliad.ai.challenge.week6.domain.review

interface ReviewRepository {
    suspend fun save(review: Review)
    suspend fun findById(id: String): Review?
    suspend fun findByProjectId(projectId: String, limit: Int = 50): List<Review>
    suspend fun findLatestByProjectId(projectId: String, limit: Int = 10): List<Review>
}
