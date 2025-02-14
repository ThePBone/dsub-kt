package github.daneren2005.dsub.model;

enum class ThemeMode(val value: Int) {
    FollowSystem(0),
    Light(1),
    Dark(2);

    companion object {
        fun fromInt(value: Int) = values().first { it.value == value }
    }
}