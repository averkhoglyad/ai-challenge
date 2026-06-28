package io.averkhogliad.ai.challenge.week3.events.core.persistence

import com.github.f4b6a3.uuid.UuidCreator
import io.averkhogliad.ai.challenge.week3.events.core.model.Event
import org.springframework.data.relational.core.mapping.event.BeforeConvertCallback
import org.springframework.stereotype.Component

@Component
class EventIdGenerator : BeforeConvertCallback<Event> {

    override fun onBeforeConvert(event: Event): Event {
        return if (event.id == null) {
            event.copy(id = UuidCreator.getTimeOrderedEpoch())
        } else {
            event
        }
    }
}