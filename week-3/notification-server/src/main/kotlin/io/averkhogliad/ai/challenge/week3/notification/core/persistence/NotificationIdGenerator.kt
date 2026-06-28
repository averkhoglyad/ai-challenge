package io.averkhogliad.ai.challenge.week3.notification.core.persistence

import com.github.f4b6a3.uuid.UuidCreator
import io.averkhogliad.ai.challenge.week3.notification.core.model.Notification
import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback
import org.springframework.stereotype.Component

@Component
class NotificationIdGenerator : BeforeConvertCallback<Notification> {

    override fun onBeforeConvert(notification: Notification): Notification {
        return if (notification.id == null) {
            notification.copy(id = UuidCreator.getTimeOrderedEpoch())
        } else {
            notification
        }
    }
}
