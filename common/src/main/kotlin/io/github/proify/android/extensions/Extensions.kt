/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.android.extensions

import android.content.Context
import android.content.res.Configuration

 fun Context.isLandScape(): Boolean =
    resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE