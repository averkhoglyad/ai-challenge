package io.averkhogliad.ai.challenge.week6.application.review

import io.averkhogliad.ai.challenge.week6.domain.review.Review
import io.averkhogliad.ai.challenge.week6.domain.review.ReviewRepository

open class SaveReviewUseCase(
    private val reviewRepository: ReviewRepository,
) {
    suspend fun execute(review: Review) {
        reviewRepository.save(review)
    }
}
