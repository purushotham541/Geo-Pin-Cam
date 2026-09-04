package com.letscode.geopincam.ui

import androidx.annotation.StringRes

/**
 * A one-shot message for a snackbar.
 *
 * The id makes two identical messages distinct, so repeating the same error
 * still triggers a new snackbar instead of being swallowed as "no change".
 */
data class UserMessage(
    @param:StringRes val textRes: Int,
    val id: Long = System.currentTimeMillis()
)
