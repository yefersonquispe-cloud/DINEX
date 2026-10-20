package com.example.dinex

data class DinexWidgetSnapshot(
    val title: String,
    val balance: String,
    val savings: String,
    val darkMode: Boolean,
)

/** Only the signed-in local profile is exposed to the home-screen widget. */
fun readDinexWidgetSnapshot(): DinexWidgetSnapshot {
    val repository = DinexRepository()
    val email = repository.session()
    val data = email?.let(repository::profile)
    return DinexWidgetSnapshot(
        title = if (email == null) "Abre DINEX para empezar" else "Tu saldo disponible",
        balance = money(data?.availableBalance() ?: 0.0),
        savings = "Ahorrado ${money(data?.savings ?: 0.0)}",
        darkMode = repository.darkMode(),
    )
}
