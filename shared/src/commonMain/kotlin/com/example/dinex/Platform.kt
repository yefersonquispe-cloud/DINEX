package com.example.dinex

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform