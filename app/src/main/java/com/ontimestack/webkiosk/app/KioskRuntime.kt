package com.ontimestack.webkiosk.app

object KioskRuntime {
    @Volatile
    var adminMode = false

    @Volatile
    var exitRequested = false
}
