package com.example.dinex

/** Native Android overlay entry point; uses the same repository as the shared UI. */
fun recordDinexVoiceCommand(text: String): String {
    val repository = DinexRepository()
    val email = repository.session() ?: return "Abre Dinex e inicia sesión para guardar."
    val data = repository.profile(email)
    val reminder = parseVoiceReminder(text, (data.reminders.maxOfOrNull { it.id } ?: 0) + 1)
    if (reminder != null) {
        repository.saveProfile(email, data.copy(reminders = listOf(reminder) + data.reminders))
        return "Recordatorio guardado: " + reminder.title
    }
    val movement = parseVoiceCommand(text, (data.movements.maxOfOrNull { it.id } ?: 0) + 1, null)
        ?: return "No entendí el monto. Di: registra 20 soles en comida."
    repository.saveProfile(email, data.copy(movements = listOf(movement) + data.movements))
    return (if (movement.income) "Ingreso" else "Gasto") + " guardado: " + money(movement.amount)
}
