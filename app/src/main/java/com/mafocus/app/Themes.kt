package com.mafocus.app

class Th(
    val name: String, val bg: String, val card: String, val line: String, val tx: String,
    val mu: String, val ac: String, val btn2: String, val field: String, val light: Boolean = false
)

val THEMES = listOf(
    Th("Midnight", "#0B0D12", "#141821", "#232938", "#E8EAF0", "#7A8194", "#4C8DFF", "#1C2130", "#0E1118"),
    Th("AMOLED", "#000000", "#0A0A0A", "#1E1E1E", "#FFFFFF", "#8A8A8A", "#00D69B", "#161616", "#050505"),
    Th("Light", "#F4F5F9", "#FFFFFF", "#E3E6EE", "#14161F", "#6B7080", "#3B6FE0", "#E9ECF4", "#F4F5F9", true),
    Th("Forest", "#0C1410", "#13201A", "#1F332A", "#E6F2EA", "#7FA08C", "#3DDC84", "#1A2C23", "#0A110D"),
    Th("Sunset", "#140D12", "#1F131B", "#36212F", "#F6E9EE", "#A98A98", "#FF7A59", "#2B1A25", "#0F0A0E"),
    Th("Royal", "#0E0B18", "#181327", "#2A2144", "#ECE8F8", "#8E86AD", "#8B7CF6", "#221B38", "#0A0812")
)
