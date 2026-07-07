package com.callbackdev.saldo.core.domain.model

/** How a generated recurring movement is registered. */
enum class RecurrenceMode {
    /** The movement is created automatically on the due date, with an informative notification. */
    AUTOMATIC,

    /** The movement is created as pending and the user confirms, edits, or skips it. */
    CONFIRM,
}
