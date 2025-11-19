package xyz.blacksheep.mjolnir.home

/**
 * Returns a human-readable label for a given [Action].
 */
fun actionLabel(action: Action): String {
    return when (action) {
        Action.NONE -> "Do nothing"
        Action.TOP_HOME -> "Top screen home"
        Action.BOTTOM_HOME -> "Bottom screen home"
        Action.BOTH_HOME -> "Both screens home"
        Action.APP_SWITCH -> "Recent apps"
    }
}

/**
 * Returns a human-readable label for a given [Gesture].
 */
fun gestureLabel(gesture: Gesture): String {
    return when (gesture) {
        Gesture.SINGLE_HOME -> "Single press Home"
        Gesture.DOUBLE_HOME -> "Double-tap Home"
        Gesture.TRIPLE_HOME -> "Triple-tap Home"
        Gesture.LONG_HOME -> "Long-press Home"
    }
}
