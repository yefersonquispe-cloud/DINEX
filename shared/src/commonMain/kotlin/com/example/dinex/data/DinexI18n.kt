package com.example.dinex

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// ═══════════════════════════════════════════════════════════════════════════
// IDIOMAS DE DINEX: español, inglés y quechua (runasimi, quechua sureño).
// Cada texto se escribe en español y pasa por t("…"). Si falta una traducción,
// se muestra el español: nunca aparece un texto vacío. Al cambiar el idioma en el
// perfil, la app se redibuja sola porque [DinexI18n.language] es un estado de Compose.
// Las traducciones al quechua deben ser revisadas por un hablante nativo.
// ═══════════════════════════════════════════════════════════════════════════

enum class DinexLanguage(val code: String, val label: String) {
    ES("es", "Español"),
    EN("en", "English"),
    QU("qu", "Runasimi"),
}

internal object DinexI18n {
    var language by mutableStateOf(DinexLanguage.ES)

    fun fromCode(code: String?): DinexLanguage = DinexLanguage.entries.firstOrNull { it.code == code } ?: DinexLanguage.ES
}

/** Texto en el idioma elegido. [es] es el texto original en español. */
internal fun t(es: String): String = when (DinexI18n.language) {
    DinexLanguage.ES -> es
    DinexLanguage.EN -> DinexEnglish[es] ?: es
    DinexLanguage.QU -> DinexQuechua[es] ?: es
}

/** Texto con valores: t("Hoy gastaste {0}", monto). */
internal fun t(es: String, vararg args: Any?): String {
    var text = t(es)
    args.forEachIndexed { i, value -> text = text.replace("{$i}", value.toString()) }
    return text
}

/** Nombre del mes en el idioma elegido (1 = enero). */
internal fun localizedMonthName(month: Int): String {
    val index = (month - 1).coerceIn(0, 11)
    return when (DinexI18n.language) {
        DinexLanguage.ES -> listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")[index]
        DinexLanguage.EN -> listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")[index]
        // Nombres andinos de los meses, usados en el quechua sureño.
        DinexLanguage.QU -> listOf("Qhapaq Raymi", "Hatun Puquy", "Pawqar Waray", "Ayriway", "Aymuray", "Inti Raymi", "Anta Situwa", "Qhapaq Situwa", "Quya Raymi", "Uma Raymi", "Ayamarq'a", "Kapaq Inti Raymi")[index]
    }
}

internal fun localizedMonthShort(month: Int): String {
    val index = (month - 1).coerceIn(0, 11)
    return when (DinexI18n.language) {
        DinexLanguage.ES -> listOf("ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SET", "OCT", "NOV", "DIC")[index]
        DinexLanguage.EN -> listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")[index]
        DinexLanguage.QU -> localizedMonthName(month).take(3).uppercase()
    }
}

// ── Traducciones (clave: texto original en español) ──────────────────────────
// Español → [inglés, quechua sureño]. Ordenadas por pantalla.
private val DinexTranslations: Map<String, Pair<String, String>> = mapOf(
    // Marca y navegación
    "Tu dinero, con propósito." to ("Your money, with purpose." to "Qullqiyki, munasqaykipaq."),
    "DINEX · Tu dinero, con propósito." to ("DINEX · Your money, with purpose." to "DINEX · Qullqiyki, munasqaykipaq."),
    "Inicio" to ("Home" to "Qallariy"),
    "Movimientos" to ("Transactions" to "Kuyuykuna"),
    "Plan" to ("Plan" to "Yuyaykuy"),
    "Análisis" to ("Insights" to "T'aqwiy"),
    "Dinex IA" to ("Dinex AI" to "Dinex IA"),
    "Consejos" to ("Tips" to "Yuyaychaykuna"),
    "Configuración" to ("Settings" to "Allichay"),
    "Registrar movimiento" to ("Add transaction" to "Kuyuyta qillqay"),
    "Volver" to ("Back" to "Kutiy"),
    "Hola, {0}" to ("Hi, {0}" to "Napaykullayki, {0}"),
    // Perfil
    "MI PERFIL" to ("MY PROFILE" to "ÑUQAMANTA"),
    "Quitar foto" to ("Remove photo" to "Siq'iyta qichuy"),
    "Preferencias" to ("Preferences" to "Akllanakuna"),
    "Idioma" to ("Language" to "Simi"),
    "Elige el idioma de toda la app" to ("Choose the language for the whole app" to "Llapan app-paq simita akllay"),
    "Recordatorios" to ("Reminders" to "Yuyarichiykuna"),
    "Registro automático" to ("Automatic logging" to "Kikillanmanta qillqay"),
    "Ayuda y privacidad" to ("Help and privacy" to "Yanapay, pakasqa kaypas"),
    "Cuenta" to ("Account" to "Cuenta"),
    "Voz y notificaciones" to ("Voice and notifications" to "Kunka, willakuykunapas"),
    "Registro automático con Yape, Plin y tu voz" to ("Automatic logging with Yape, Plin and your voice" to "Yape, Plin, kunkaykiwanpas kikillanmanta qillqay"),
    "Ver guía de Dinex" to ("View Dinex guide" to "Dinex pusaqta qhaway"),
    "Repasa todas las funciones" to ("Review every feature" to "Llapan ruwanakunata qhawariy"),
    "Términos y condiciones" to ("Terms and conditions" to "Kamachiykuna"),
    "Reglas de uso de la app" to ("Rules for using the app" to "App llamk'achinapaq kamachiykuna"),
    "Política de privacidad" to ("Privacy policy" to "Pakasqa kaypa kamachiynin"),
    "Cómo cuidamos tus datos (Ley N.° 29733)" to ("How we protect your data (Law No. 29733)" to "Willakuyniykita imayna waqaychayku (Kamachiy 29733)"),
    "Cerrar sesión" to ("Log out" to "Lluqsiy"),
    "Tus datos quedan guardados en la nube" to ("Your data stays saved in the cloud" to "Willakuyniyki phuyupi waqaychasqa qhipan"),
    "Eliminar mi cuenta" to ("Delete my account" to "Cuentayta chinkachiy"),
    "Borra tu cuenta y todos tus datos" to ("Deletes your account and all your data" to "Cuentaykita, llapan willakuyniykitapas pichan"),
    "Modo oscuro" to ("Dark mode" to "Tutayaq rikch'ay"),
    "Verde bosque" to ("Forest green" to "Sach'a q'umir"),
    "Verde y blanco" to ("Green and white" to "Q'umir, yuraqpas"),
    "Reducir movimiento" to ("Reduce motion" to "Kuyuyta pisiyachiy"),
    "Menos animaciones" to ("Fewer animations" to "Aswan pisi kuyuykuna"),
    "Texto más grande" to ("Larger text" to "Aswan hatun qillqa"),
    "Respeta el tamaño del sistema" to ("Follows the system size" to "Sistemapa sayayninta qatin"),
    "Aviso de presupuesto" to ("Budget alert" to "Qullqi yupaypa willakuynin"),
    "Cuando superes tu límite diario" to ("When you go over your daily limit" to "P'unchaw saywaykita llallispa"),
    "Activa tus alertas" to ("Turn on your alerts" to "Willakuyniykikunata kichay"),
    "Te avisamos si superas tu presupuesto diario y te recordamos ahorrar antes de que termine el día." to
        ("We let you know if you go over your daily budget and remind you to save before the day ends." to
            "P'unchaw qullqi yupayniykita llallispaqa willasqayki, p'unchaw manaraq tukukuchkaptin waqaychanaykita yuyarichisqayki."),
    "Las notificaciones están bloqueadas en Android. Actívalas para que Dinex pueda avisarte." to
        ("Notifications are blocked on Android. Turn them on so Dinex can alert you." to "Android-pi willakuykuna wichq'asqa kachkan. Kichay Dinex willasunaykipaq."),
    "Permitir" to ("Allow" to "Saqiy"),
    "Abrir ajustes" to ("Open settings" to "Allichayta kichay"),
    "Activar" to ("Turn on" to "Kichay"),
    "¿Eliminar tu cuenta?" to ("Delete your account?" to "¿Cuentaykita chinkachinkichu?"),
    "Se borrarán tu cuenta, tus movimientos, metas y recordatorios de la nube y de este equipo. No se puede deshacer." to
        ("Your account, transactions, goals and reminders will be erased from the cloud and this device. This can't be undone." to
            "Cuentayki, kuyuyniykikuna, munasqaykikuna, yuyarichiyniykikunapas phuyumanta kay equipomantapas pichakunqa. Manañam kutichiyta atikunchu."),
    "Si quieres guardar una copia, primero exporta tu reporte desde Movimientos." to
        ("To keep a copy, first export your report from Transactions." to "Huk copiata waqaychayta munaspaqa, ñawpaqta Kuyuykunamanta willakuyniykita urquy."),
    "Eliminando…" to ("Deleting…" to "Chinkachichkan…"),
    "Eliminar para siempre" to ("Delete forever" to "Wiñaypaq chinkachiy"),
    "Cancelar" to ("Cancel" to "Saqiy"),
    "Cerrar" to ("Close" to "Wichq'ay"),
    "Eliminar" to ("Delete" to "Chinkachiy"),
    "Limpiar" to ("Clear" to "Pichay"),
    "Nivel {0} · {1} de racha" to ("{0} level · {1} streak" to "{0} pata · {1} siqi"),
    "{0} para {1}" to ("{0} to {1}" to "{1}-paq {0}"),
    "{0} sincronizados con tu cuenta" to ("{0} synced with your account" to "{0} cuentaykiwan tupachisqa"),
    "movimiento" to ("transaction" to "kuyuy"),
    "movimientos" to ("transactions" to "kuyuykuna"),
    "Saldo disponible" to ("Available balance" to "Kapuq qullqi"),
    "Ahorrado" to ("Saved" to "Waqaychasqa"),
    "Egresos" to ("Expenses" to "Lluqsiq qullqi"),
    "Cobre" to ("Copper" to "Anta"),
    "Plata" to ("Silver" to "Qullqi"),
    "Oro" to ("Gold" to "Quri"),
    "Platino" to ("Platinum" to "Platino"),
    "Diamante" to ("Diamond" to "Diamante"),
    "Esmeralda" to ("Emerald" to "Esmeralda"),
    "Rubí" to ("Ruby" to "Rubí"),
    "Amatista" to ("Amethyst" to "Amatista"),
    "Obsidiana" to ("Obsidian" to "Obsidiana"),
    "Perla" to ("Pearl" to "Perla"),
    "Madera" to ("Wood" to "K'aspi"),
    "Piedra" to ("Stone" to "Rumi"),
    "Acero" to ("Steel" to "Acero"),
    "Cristal" to ("Crystal" to "Cristal"),
    "Leyenda" to ("Legend" to "Willakuy"),
    "Sin metas" to ("No goals" to "Mana munasqakuna"),
    "1 meta" to ("1 goal" to "1 munasqa"),
    "{0} metas" to ("{0} goals" to "{0} munasqakuna"),
    "Meta total {0}" to ("Total goal {0}" to "Llapan munasqa {0}"),
    "Faltan {0}" to ("{0} to go" to "Pisichkan {0}"),
    " de {0}" to (" of {0}" to " {0}-manta"),
    // Inicio
    "SALDO DISPONIBLE" to ("AVAILABLE BALANCE" to "KAPUQ QULLQI"),
    "Ingresos" to ("Income" to "Yaykuq qullqi"),
    "Gastos" to ("Expenses" to "Lluqsiq qullqi"),
    "Mis ahorros" to ("My savings" to "Waqaychasqay"),
    "Cada sol te acerca a tu meta" to ("Every sol brings you closer to your goal" to "Sapa sol munasqaykiman asuykachisunki"),
    "Total ahorrado" to ("Total saved" to "Llapan waqaychasqa"),
    "Crea una meta para darle un propósito a tu ahorro." to ("Create a goal to give your savings a purpose." to "Huk munasqata ruway waqaychasqaykiq imapaq kananpaq."),
    " ahorrados" to (" saved" to " waqaychasqa"),
    "¡Metas cumplidas!" to ("Goals reached!" to "¡Munasqakuna hunt'asqa!"),
    "meta más" to ("more goal" to "huk munasqa"),
    "metas más" to ("more goals" to "aswan munasqakuna"),
    "Crear mi primera meta" to ("Create my first goal" to "Ñawpaq munasqayta ruway"),
    "Ver todas mis metas" to ("See all my goals" to "Llapan munasqaykunata qhaway"),
    "Racha actual" to ("Current streak" to "Kunan siqi"),
    "día" to ("day" to "p'unchaw"),
    "días" to ("days" to "p'unchawkuna"),
    "Completaste los 365 días" to ("You completed all 365 days" to "365 p'unchawta hunt'achinki"),
    "Registrado" to ("Logged" to "Qillqasqa"),
    "Ahorré hoy" to ("I saved today" to "Kunan waqaycharqani"),
    "Ahorra hoy y suma +1 a tu racha. Aunque sea S/ 1, cuenta." to
        ("Save today and add +1 to your streak. Even S/ 1 counts." to "Kunan waqaychay, siqiykiman +1 yapay. S/ 1 kaptinpas, yupakunmi."),
    "Camino de niveles" to ("Level path" to "Patakunapa ñan"),
    "Tu presupuesto" to ("Your budget" to "Qullqi yupayniyki"),
    "Ajustar categorías" to ("Adjust categories" to "Rakikunata allichay"),
    "¿A dónde se fue tu dinero?" to ("Where did your money go?" to "¿Maymantaq qullqiyki rirqa?"),
    "Toca una porción para filtrar" to ("Tap a slice to filter" to "Huk t'aqata llamiy akllanapaq"),
    "Filtro activo" to ("Filter on" to "Akllay kichasqa"),
    "1 movimiento" to ("1 transaction" to "1 kuyuy"),
    "Todavía no hay gastos registrados este mes." to ("No expenses logged this month yet." to "Kay killapi manaraq lluqsiq qullqi qillqasqachu."),
    "Ver todas" to ("See all" to "Llapanta qhaway"),
    "Ver todos" to ("See all" to "Llapanta qhaway"),
    "Diario" to ("Daily" to "Sapa p'unchaw"),
    "Gastado" to ("Spent" to "Tukusqa"),
    "Disponible" to ("Available" to "Kapuq"),
    "Cada movimiento cuenta. Dale forma a tu próximo objetivo." to
        ("Every transaction counts. Shape your next goal." to "Sapa kuyuy yupakun. Hamuq munasqaykita ruway."),
    "Últimos movimientos" to ("Recent transactions" to "Qhipa kuyuykuna"),
    "Ocultar detalle" to ("Hide details" to "Sut'inchayta pakay"),
    "Ver detalle" to ("View details" to "Sut'inchayta qhaway"),
    // Movimientos
    "Tu actividad" to ("Your activity" to "Ruwasqayki"),
    "Todo lo que entra y sale, en un solo lugar." to ("Everything coming in and going out, in one place." to "Llapan yaykuq lluqsiqpas, huk kitillapi."),
    "Buscar por nombre, categoría o método" to ("Search by name, category or method" to "Sutiwan, rakiwan, imayna pagasqawanpas maskay"),
    "Importar Excel/CSV" to ("Import Excel/CSV" to "Excel/CSV apamuy"),
    "Exportar reporte" to ("Export report" to "Willakuyta urquy"),
    "BALANCE DE LOS MOVIMIENTOS VISIBLES" to ("BALANCE OF VISIBLE TRANSACTIONS" to "RIKUKUQ KUYUYKUNAPA PUCHUN"),
    "Entró" to ("In" to "Yaykurqa"),
    "Salió" to ("Out" to "Lluqsirqa"),
    // Plan
    "Construye tu futuro" to ("Build your future" to "Paqariyniykita hatarichiy"),
    "Tu plan" to ("Your plan" to "Yuyaykuyniyki"),
    "Organiza hoy. Disfruta de un mañana con menos sorpresas." to ("Get organized today. Enjoy a tomorrow with fewer surprises." to "Kunan allichakuy. Paqarinqa mana muspaypi kawsay."),
    "PRESUPUESTO DEL MES" to ("MONTHLY BUDGET" to "KILLAPA QULLQI YUPAYNIN"),
    "Presupuestos por categoría" to ("Budgets by category" to "Rakikunaman hina qullqi yupay"),
    "Ajustar presupuestos" to ("Adjust budgets" to "Qullqi yupaykunata allichay"),
    "Estos importes se sugieren a partir de tus ingresos del mes. Cámbialos solo si lo deseas; no se mueve dinero." to
        ("These amounts are suggested from your monthly income. Change them only if you want; no money is moved." to
            "Kay yupaykunaqa killapi yaykuq qullqiykimanta yuyaychasqa. Munaspalla tikray; mana qullqi kuyunchu."),
    "El total supera tus ingresos registrados." to ("The total is higher than your logged income." to "Llapan yupay yaykuq qullqiykita llallin."),
    "Volver a sugerencias automáticas" to ("Back to automatic suggestions" to "Kikillanmanta yuyaychaykunaman kutiy"),
    "Guardar cambios" to ("Save changes" to "Tikraykunata waqaychay"),
    "RECORDATORIOS" to ("REMINDERS" to "YUYARICHIYKUNA"),
    "Próximos pagos" to ("Upcoming payments" to "Hamuq pagaykuna"),
    "Por pagar" to ("To pay" to "Pagana"),
    "Estás al día" to ("You're up to date" to "Allinmi kachkanki"),
    "Anota la luz, el internet o tu pensión y te avisamos a tiempo." to
        ("Add your electricity, internet or tuition and we'll remind you on time." to "K'anchayta, internetta, yachaywasi pagaytapas qillqay, pachallanpi willasqayki."),
    "Agregar recordatorio" to ("Add reminder" to "Yuyarichiyta yapay"),
    "¿Este pago ya se realizó?" to ("Has this payment been made?" to "¿Kay pagay ruwasqañachu?"),
    "Si ya pagaste, Dinex puede quitar el recordatorio y registrarlo como egreso." to
        ("If you already paid, Dinex can remove the reminder and log it as an expense." to "Pagarqankiña chayqa, Dinex yuyarichiyta qichunman, lluqsiq qullqi hina qillqanmanpas."),
    "Sí, agregar egreso" to ("Yes, add expense" to "Arí, lluqsiq qullqita yapay"),
    "No, solo eliminar" to ("No, just delete" to "Mana, chinkachiylla"),
    "{0} personalizados" to ("{0} custom" to "{0} kikiykipa"),
    "Sugeridos" to ("Suggested" to "Yuyaychasqa"),
    "  de {0}" to ("  of {0}" to "  {0}-manta"),
    "Repartido según tus ingresos del mes ({0})" to ("Split based on your monthly income ({0})" to "Killapi yaykuq qullqiykiman hina rakisqa ({0})"),
    "Registra tus ingresos para afinar estas sugerencias" to ("Log your income to fine-tune these suggestions" to "Yaykuq qullqiykita qillqay kay yuyaychaykuna allinyananpaq"),
    "Sin límite" to ("No limit" to "Mana saywayuq"),
    "Cerca del límite" to ("Near the limit" to "Saywaman qayllachkan"),
    "Sin gastos" to ("No spending" to "Mana tukusqa"),
    "Va bien" to ("On track" to "Allinmi richkan"),
    "{0} gastados" to ("{0} spent" to "{0} tukusqa"),
    "{0} de {1} · {2} de más" to ("{0} of {1} · {2} over" to "{1}-manta {0} · {2} aswan"),
    "{0} de {1} · quedan {2}" to ("{0} of {1} · {2} left" to "{1}-manta {0} · {2} puchun"),
    "Estás cerca del límite de hoy" to ("You're close to today's limit" to "Kunan p'unchaw saywaman qayllachkanki"),
    "Aún tienes {0} hoy" to ("You still have {0} today" to "Kunanqa {0} kapusunkiraq"),
    // Categorías (solo para mostrar; los datos guardados siguen en español)
    "Comida" to ("Food" to "Mikuy"),
    "Transporte" to ("Transport" to "Puriy"),
    "Estudios" to ("Studies" to "Yachay"),
    "Entretenimiento" to ("Entertainment" to "Kusikuy"),
    "Otros" to ("Other" to "Wakinkuna"),
    "Ingreso" to ("Income" to "Yaykuq qullqi"),

    // ── Ventanas, guía, exportar e importar, Dinex IA ──
    "¿Qué quieres registrar?" to ("What do you want to log?" to "¿Imatataq qillqayta munanki?"),
    "Elige cómo añadir tu próximo movimiento" to ("Choose how to add your next transaction" to "Hamuq kuyuyniykita imayna yapanaykita akllay"),
    "Registrar gasto" to ("Log expense" to "Lluqsiq qullqita qillqay"),
    "Registrar ingreso" to ("Log income" to "Yaykuq qullqita qillqay"),
    "Monto, categoría y listo" to ("Amount, category and done" to "Yupay, raki, chaylla"),
    "Aumenta tu saldo" to ("Increase your balance" to "Qullqiykita yapay"),
    "Registra sin escribir: con tu voz o con las alertas de tu banco" to ("Log without typing: with your voice or your bank alerts" to "Mana qillqaspa qillqay: kunkaykiwan, bancoykiq willakuyninwanpas"),
    "Comando de voz" to ("Voice command" to "Kunkawan kamachiy"),
    "Dicta un gasto o ingreso" to ("Say an expense or income" to "Huk lluqsiq utaq yaykuq qullqita niy"),
    "Listo" to ("Ready" to "Listo"),
    "No disponible" to ("Not available" to "Mana kanchu"),
    "“Dinex, registra 20 soles en comida”" to ("“Dinex, log 20 soles for food”" to "“Dinex, mikuypaq 20 solta qillqay”"),
    "Escuchando..." to ("Listening..." to "Uyarichkani..."),
    "Hablar con Dinex" to ("Talk to Dinex" to "Dinex-wan rimay"),
    "Alertas del teléfono" to ("Phone alerts" to "Celularpa willakuyninkuna"),
    "Yape, Plin y tu banco" to ("Yape, Plin and your bank" to "Yape, Plin, bancoykipas"),
    "Bloqueado" to ("Blocked" to "Wichq'asqa"),
    "Activo" to ("On" to "Kichasqa"),
    "Conectando" to ("Connecting" to "Tinkuchkan"),
    "Apagado" to ("Off" to "Wañusqa"),
    "Activa" to ("On" to "Kichasqa"),
    "Apagada" to ("Off" to "Wañusqa"),
    "Detecta ingresos (“te yapearon”, “recibiste”) y gastos (“yapeaste”, “pagaste”) cuando la alerta trae un monto en soles." to
        ("Detects income (“you received”) and expenses (“you paid”) when the alert includes an amount in soles." to
            "Yaykuq qullqita (“chaskirqanki”), lluqsiq qullqitapas (“pagarqanki”) tarin, willakuy solpi yupayniyuq kaptin."),
    "Desbloquea el permiso" to ("Unlock the permission" to "Saqiyta kichay"),
    "Se abrirá la información de Dinex. Toca ⋮ (arriba a la derecha) y luego “Permitir ajustes restringidos”." to
        ("Dinex's app info will open. Tap ⋮ (top right) and then “Allow restricted settings”." to "Dinex-pa willakuynin kicharikunqa. ⋮ (hanaq paña ladupi) llamiy, chaymanta “Permitir ajustes restringidos”."),
    "Abrir" to ("Open" to "Kichay"),
    "Enciende Dinex en Acceso a notificaciones" to ("Turn on Dinex in Notification access" to "Willakuykunaman yaykuypi Dinex-ta kichay"),
    "Busca Dinex en la lista y activa el interruptor. Al volver, Dinex lo detecta solo." to
        ("Find Dinex in the list and turn on the switch. When you come back, Dinex detects it automatically." to "Listapi Dinex-ta maskay, kichay. Kutimuspa Dinex kikillan tarinqa."),
    "Ya lo activé, comprobar" to ("I turned it on, check" to "Kicharqaniña, qhawariy"),
    "Reconectar lector" to ("Reconnect reader" to "Ñawiriqta kaqmanta tinkuchiy"),
    "Revisar alertas detectadas" to ("Review detected alerts" to "Tarisqa willakuykunata qhaway"),
    "alerta" to ("alert" to "willakuy"),
    "alertas" to ("alerts" to "willakuykuna"),
    "Oye Dinex" to ("Hey Dinex" to "Uyariy Dinex"),
    "Sin abrir la app" to ("Without opening the app" to "Mana app-ta kichaspa"),
    "Di “Oye Dinex” o toca la burbuja con la mascota para registrar gastos, ingresos o preguntar cuánto gastaste. Android muestra una notificación mientras escucha." to
        ("Say “Hey Dinex” or tap the bubble with the mascot to log expenses, income or ask how much you spent. Android shows a notification while it listens." to
            "“Uyariy Dinex” niy utaq mascotayuq burbujata llamiy, lluqsiq yaykuq qullqita qillqanaykipaq utaq hayk'ata tukurqanki tapunaykipaq. Uyarichkaptin Android willakuyta rikuchin."),
    "Micrófono" to ("Microphone" to "Micrófono"),
    "Para escucharte solo cuando Oye Dinex está activo." to ("To hear you only while Hey Dinex is on." to "Uyariy Dinex kichasqa kaptillan uyarinaypaq."),
    "Burbuja flotante" to ("Floating bubble" to "Phawaq burbuja"),
    "Busca Dinex y activa “Mostrar sobre otras apps”." to ("Find Dinex and turn on “Display over other apps”." to "Dinex-ta maskay, “Mostrar sobre otras apps” kichay."),
    "Permisos de Xiaomi" to ("Xiaomi permissions" to "Xiaomi saqiykuna"),
    "En “Otros permisos” activa “Mostrar ventanas emergentes mientras se ejecuta en segundo plano”. Sin esto, tu Xiaomi esconde la burbuja." to
        ("In “Other permissions” turn on “Display pop-up windows while running in the background”. Without it, your Xiaomi hides the bubble." to
            "“Otros permisos” nisqapi “Mostrar ventanas emergentes mientras se ejecuta en segundo plano” kichay. Mana kaptinqa Xiaomi-yki burbujata pakan."),
    "Inicio automático y batería" to ("Autostart and battery" to "Kikillan qallariy, bateríapas"),
    "Activa “Inicio automático” y pon el ahorro de batería de Dinex en “Sin restricciones” para que no se apague solo." to
        ("Turn on “Autostart” and set Dinex's battery saver to “No restrictions” so it doesn't turn off by itself." to
            "“Inicio automático” kichay, Dinex-pa batería waqaychayninta “Sin restricciones” churay, kikillan ama wañunanpaq."),
    "Activar Oye Dinex" to ("Turn on Hey Dinex" to "Uyariy Dinex-ta kichay"),
    "Desactivar Oye Dinex" to ("Turn off Hey Dinex" to "Uyariy Dinex-ta wañuchiy"),
    "Siguiente: permitir micrófono" to ("Next: allow microphone" to "Qatiq: micrófonota saqiy"),
    "Siguiente: permitir burbuja" to ("Next: allow bubble" to "Qatiq: burbujata saqiy"),
    "Listo. Sal de Dinex y verás la burbuja con la mascota. Tócala para hablar." to
        ("Done. Leave Dinex and you'll see the bubble with the mascot. Tap it to talk." to "Listo. Dinex-manta lluqsiy, mascotayuq burbujata rikunki. Rimanaykipaq llamiy."),
    "Revisar batería e inicio automático" to ("Check battery and autostart" to "Bateríata, kikillan qallariytapas qhaway"),
    "Ubicación" to ("Location" to "Maypi kasqa"),
    "Opcional, para tu mapa de gastos" to ("Optional, for your spending map" to "Munaspalla, tukusqayki mapapaq"),
    "Solo se pide con tu permiso y se guarda en tu teléfono." to ("Only requested with your permission and saved on your phone." to "Saqisqaykillawan mañakun, celularniykipitaq waqaychakun."),
    "Activar ubicación" to ("Turn on location" to "Maypi kasqata kichay"),
    "Dinex solo lee alertas con tu autorización. “Oye Dinex” usa el micrófono únicamente mientras está activo." to
        ("Dinex only reads alerts with your permission. “Hey Dinex” uses the microphone only while it's on." to "Dinex saqisqaykillawan willakuykunata ñawirin. “Uyariy Dinex” kichasqa kaptillan micrófonota llamk'achin."),
    "Monto" to ("Amount" to "Yupay"),
    "¿En qué fue?" to ("What was it for?" to "¿Imapaqtaq karqa?"),
    "Categoría" to ("Category" to "Raki"),
    "Método de pago" to ("Payment method" to "Imaynata pagasqa"),
    "Día del mes" to ("Day of the month" to "Killapa p'unchawnin"),
    "Nota (opcional)" to ("Note (optional)" to "Willay (munaspalla)"),
    "Ej. con tarjeta de crédito" to ("E.g. with a credit card" to "Ahina: tarjetawan"),
    "Escanear comprobante" to ("Scan receipt" to "Boletata escaneay"),
    "Ingresa un monto válido" to ("Enter a valid amount" to "Allin yupayta qillqay"),
    "Guardar" to ("Save" to "Waqaychay"),
    "Gasto" to ("Expense" to "Lluqsiq"),
    "Guardar {0}" to ("Save {0}" to "{0} waqaychay"),
    "Nuevo gasto" to ("New expense" to "Musuq lluqsiq qullqi"),
    "Nuevo ingreso" to ("New income" to "Musuq yaykuq qullqi"),
    "Dinero que sale" to ("Money going out" to "Lluqsiq qullqi"),
    "Dinero que entra" to ("Money coming in" to "Yaykuq qullqi"),
    "Más detalles" to ("More details" to "Aswan willakuy"),
    "Menos detalles" to ("Fewer details" to "Aswan pisi willakuy"),
    "¿Cuánto ahorraste?" to ("How much did you save?" to "¿Hayk'atataq waqaycharqanki?"),
    "Este monto irá a tu tarjeta de ahorro" to ("This amount goes to your savings card" to "Kay yupayqa waqaychana tarjetaykimanmi rinqa"),
    "TUS METAS" to ("YOUR GOALS" to "MUNASQAYKIKUNA"),
    "Nueva meta de ahorro" to ("New savings goal" to "Musuq waqaychana munasqa"),
    "Monto ahorrado" to ("Amount saved" to "Waqaychasqa yupay"),
    "¿Para qué estás ahorrando?" to ("What are you saving for?" to "¿Imapaqtaq waqaychachkanki?"),
    "Ej. PlayStation, celular, emergencia" to ("E.g. PlayStation, phone, emergency" to "Ahina: PlayStation, celular, ch'aqway"),
    "Meta total (opcional)" to ("Total goal (optional)" to "Llapan munasqa (munaspalla)"),
    "¿Cuánto necesitas reunir?" to ("How much do you need?" to "¿Hayk'atataq huñunayki?"),
    "Registrar un ahorro activa el día de tu racha. Solo cuenta una vez por día, aunque puedes agregar varios montos." to
        ("Logging a saving keeps your streak for the day. It counts once per day, but you can add several amounts." to
            "Waqaychayta qillqaspa siqiykiq p'unchawnin kichakun. Sapa p'unchaw huk kutilla yupakun, achka yupaykunata yapaspapas."),
    "Ingresa un monto válido que no supere tu saldo disponible." to ("Enter a valid amount that doesn't exceed your available balance." to "Kapuq qullqiykita mana llallinanpaq allin yupayta qillqay."),
    "Guardar ahorro" to ("Save" to "Waqaychay"),
    "Comencemos con tu saldo" to ("Let's start with your balance" to "Qullqiykiwan qallarisun"),
    "Así tus reportes empiezan desde cero y reflejan tu realidad." to ("This way your reports start from zero and reflect your reality." to "Chhaynapi willakuyniykikuna ch'usaqmanta qallarin, kawsayniykitataq rikuchin."),
    "Saldo disponible este mes" to ("Available balance this month" to "Kay killapi kapuq qullqi"),
    "Puedes empezar sin saldo y registrar tus ingresos después desde el botón +." to
        ("You can start with no balance and log your income later with the + button." to "Mana qullqiyuq qallariyta atinki, yaykuq qullqiykitataq qhipaman + botonwan qillqanki."),
    "Guardar y entrar" to ("Save and continue" to "Waqaychay, yaykuy"),
    "Empezar con S/ 0" to ("Start with S/ 0" to "S/ 0-wan qallariy"),
    "META CUMPLIDA" to ("GOAL REACHED" to "MUNASQA HUNT'ASQA"),
    "¡Enhorabuena!" to ("Congratulations!" to "¡Allinmi kachun!"),
    "Disfruta tu logro y sigue construyendo tus próximas metas financieras." to
        ("Enjoy your achievement and keep building your next financial goals." to "Hunt'asqaykita kusikuy, hamuq qullqi munasqaykikunata hatarichiy."),
    "Celebrar y continuar" to ("Celebrate and continue" to "Raymichay, qatipay"),
    "Estamos afinando los datos para darte una mejor alternativa." to ("We're refining the data to give you a better option." to "Willakuykunata allichachkayku aswan allinta qunaykupaq."),
    "Nuevo recordatorio" to ("New reminder" to "Musuq yuyarichiy"),
    "No olvides tus próximos pagos" to ("Don't forget your upcoming payments" to "Hamuq pagayniykikunata ama qunqaychu"),
    "Nombre del pago" to ("Payment name" to "Pagaypa sutin"),
    "Ej. Internet" to ("E.g. Internet" to "Ahina: Internet"),
    "Fecha" to ("Date" to "P'unchaw"),
    "Ej. 25 SEP" to ("E.g. SEP 25" to "Ahina: 25 SEP"),
    "Completa el nombre, la fecha y un monto válido." to ("Fill in the name, the date and a valid amount." to "Sutita, p'unchawta, allin yupaytapas hunt'achiy."),
    "Guardar recordatorio" to ("Save reminder" to "Yuyarichiyta waqaychay"),
    "Compra detectada" to ("Purchase detected" to "Rantisqa tarisqa"),
    "Revísala antes de registrarla. Nada se guarda sin tu confirmación." to ("Review it before logging. Nothing is saved without your confirmation." to "Manaraq qillqaspa qhaway. Mana arí niptiykiqa manam imapas waqaychakunchu."),
    "Analizado con Dinex IA" to ("Analyzed with Dinex AI" to "Dinex IA-wan t'aqwisqa"),
    "Análisis local completado" to ("Local analysis complete" to "Kaypi t'aqwiy tukusqa"),
    " · precio estimado" to (" · estimated price" to " · yupasqa chanin"),
    "REVISAR" to ("REVIEW" to "QHAWAY"),
    "Producto o comercio" to ("Product or store" to "Imapas utaq tienda"),
    "Precio estimado" to ("Estimated price" to "Yupasqa chanin"),
    "Precio detectado" to ("Detected price" to "Tarisqa chanin"),
    "Categoría sugerida" to ("Suggested category" to "Yuyaychasqa raki"),
    "Gemini procesa la foto para extraer los datos; Dinex no conserva la imagen." to ("Gemini processes the photo to extract the data; Dinex doesn't keep the image." to "Gemini siq'iyta llamk'achin willakuykunata urqunanpaq; Dinex-qa mana siq'iyta waqaychanchu."),
    "El reconocimiento de respaldo ocurrió en el dispositivo." to ("The backup recognition ran on the device." to "Yanapaq reqsiyqa kay equipopi karqa."),
    "Descartar foto" to ("Discard photo" to "Siq'iyta wikch'uy"),
    "PRESUPUESTO DE HOY" to ("TODAY'S BUDGET" to "KUNAN P'UNCHAWPA QULLQI YUPAYNIN"),
    "Superaste tu límite diario" to ("You went over your daily limit" to "P'unchaw saywaykita llallirqanki"),
    "Gastaste hoy" to ("Spent today" to "Kunan tukurqanki"),
    "Tu límite" to ("Your limit" to "Saywayki"),
    "No pasa nada: mañana tu límite empieza de cero. Si este gasto era necesario, puedes ajustar tu presupuesto en el Plan." to
        ("It's okay: tomorrow your limit starts from zero. If this expense was necessary, you can adjust your budget in Plan." to
            "Ama llakikuychu: paqarin saywayki ch'usaqmanta qallarin. Kay tukuy necesitasqa karqa chayqa, Yuyaykuypi qullqi yupayniykita allichay."),
    "Ver mi plan" to ("See my plan" to "Yuyaykuyniyta qhaway"),
    "Entendido" to ("Got it" to "Entiendeni"),
    "{0} de {1}" to ("{0} of {1}" to "{1}-manta {0}"),
    "Saltar" to ("Skip" to "Phinkiy"),
    "Empezar a usar Dinex" to ("Start using Dinex" to "Dinex-ta llamk'achiyta qallariy"),
    "Comenzar" to ("Start" to "Qallariy"),
    "Siguiente" to ("Next" to "Qatiq"),
    "Hola, {0}. Bienvenido a Dinex" to ("Hi, {0}. Welcome to Dinex" to "Napaykullayki, {0}. Dinex-man sumaq hamusqayki"),
    "Te damos la bienvenida a Dinex" to ("Welcome to Dinex" to "Dinex-man sumaq hamusqayki"),
    "Bienvenida" to ("Welcome" to "Chaskiy"),
    "En menos de un minuto verás cómo ordenar tu dinero, ahorrar para tus metas y entender tus gastos." to
        ("In less than a minute you'll see how to organize your money, save for your goals and understand your spending." to
            "Huk minuto ukhullapi qullqiykita imayna allichanaykita, munasqaykipaq waqaychanaykita, tukusqaykita entiendenaykitapas rikunki."),
    "Registrar" to ("Log" to "Qillqay"),
    "Anota cada movimiento en segundos" to ("Log every transaction in seconds" to "Sapa kuyuyta usqhaylla qillqay"),
    "Elige gasto o ingreso, escribe el monto y listo. Funciona con Yape, Plin, efectivo, tarjeta o transferencia." to
        ("Pick expense or income, enter the amount and you're done. Works with Yape, Plin, cash, card or transfer." to
            "Lluqsiq utaq yaykuq qullqita akllay, yupayta qillqay, chaylla. Yape, Plin, efectivo, tarjeta, transferenciawanpas llamk'an."),
    "Botón Nuevo movimiento" to ("New transaction button" to "Musuq kuyuy botón"),
    "Botón + de la barra inferior" to ("+ button on the bottom bar" to "Uraypi kaq + botón"),
    "Importa o exporta tus movimientos en Excel o CSV." to ("Import or export your transactions in Excel or CSV." to "Kuyuyniykikunata Excel utaq CSV-pi apamuy utaq urquy."),
    "Registro rápido" to ("Quick logging" to "Usqhay qillqay"),
    "Escanea tus comprobantes" to ("Scan your receipts" to "Boletaykikunata escaneay"),
    "Escanea boletas o usa tu voz" to ("Scan receipts or use your voice" to "Boletakunata escaneay utaq kunkaykiwan"),
    "Sube la foto de una boleta y Dinex lee el total por ti. Siempre revisas antes de guardar." to
        ("Upload a photo of a receipt and Dinex reads the total for you. You always review before saving." to "Huk boletapa siq'iynin churay, Dinex-taq llapan yupayta ñawirisunki. Manaraq waqaychaspa qhawanki."),
    "Toma una foto de la boleta o di \"registra 20 soles en comida\". También puede anotar tus avisos de Yape y Plin si le das permiso." to
        ("Take a photo of the receipt or say \"log 20 soles for food\". It can also log your Yape and Plin alerts if you allow it." to
            "Boletata siq'iy utaq \"mikuypaq 20 solta qillqay\" niy. Saqiptiykiqa Yape, Plin willakuyniykikunatapas qillqanmanmi."),
    "Nuevo movimiento → Escanear comprobante" to ("New transaction → Scan receipt" to "Musuq kuyuy → Boletata escaneay"),
    "Inicio → Escanear o Voz" to ("Home → Scan or Voice" to "Qallariy → Escaneay utaq Kunka"),
    "Solo detecta avisos de pago; no lee tus chats." to ("It only detects payment alerts; it doesn't read your chats." to "Pagaypa willakuyninkunallata tarin; chatniykikunataqa mana ñawirinchu."),
    "Puedes apagarlo cuando quieras." to ("You can turn it off anytime." to "Munasqaykipi wañuchiyta atinki."),
    "Activar registro automático" to ("Turn on automatic logging" to "Kikillanmanta qillqayta kichay"),
    "Metas y racha" to ("Goals and streak" to "Munasqakuna, siqipas"),
    "Ahorra con un propósito" to ("Save with a purpose" to "Imapaq kananpaq waqaychay"),
    "Crea metas como un viaje o un celular y separa dinero de tu saldo. Cada día que ahorras sube tu racha y ganas medallas." to
        ("Create goals like a trip or a phone and set money aside. Every day you save, your streak grows and you earn medals." to
            "Huk puriypaq utaq celularpaq munasqakunata ruway, qullqitataq t'aqay. Sapa p'unchaw waqaychaspa siqiyki wiñan, medallakunatataq chaskinki."),
    "Menú → Metas" to ("Menu → Goals" to "Menú → Munasqakuna"),
    "Inicio → Metas" to ("Home → Goals" to "Qallariy → Munasqakuna"),
    "Un recordatorio diario te ayuda a mantener la racha." to ("A daily reminder helps you keep your streak." to "Sapa p'unchaw yuyarichiy siqiykita hap'inaykipaq yanapasunki."),
    "Presupuesto" to ("Budget" to "Qullqi yupay"),
    "Pon un límite y Dinex te avisa" to ("Set a limit and Dinex lets you know" to "Saywata churay, Dinex-taq willasunki"),
    "Elige cuánto quieres gastar al día y por categoría. Si te pasas, te avisamos antes de guardar el gasto." to
        ("Choose how much you want to spend per day and per category. If you go over, we warn you before saving the expense." to
            "Sapa p'unchaw, sapa rakipipas hayk'ata tukuyta munasqaykita akllay. Llallispaqa manaraq waqaychaspa willasqayki."),
    "Menú → Presupuesto" to ("Menu → Budget" to "Menú → Qullqi yupay"),
    "Barra inferior → Plan" to ("Bottom bar → Plan" to "Uray barra → Yuyaykuy"),
    "También guarda recordatorios de pagos como luz, agua o internet." to ("It also saves payment reminders like electricity, water or internet." to "K'anchay, yaku, internet pagaykunapa yuyarichiyninkunatapas waqaychan."),
    "Mira a dónde se va tu dinero" to ("See where your money goes" to "Qullqiyki mayman risqanta qhaway"),
    "Revisa gráficos por categoría y por mes, y un mapa con los lugares donde más gastas. Además, consulta el dólar y otras monedas." to
        ("Check charts by category and month, and a map of the places where you spend the most. You can also check the dollar and other currencies." to
            "Rakiman, killaman hina siq'ikunata, aswanta tukusqayki kitikunapa mapantapas qhaway. Dólartapas, huk qullqikunatapas tapuy."),
    "Menú → Análisis" to ("Menu → Insights" to "Menú → T'aqwiy"),
    "Barra inferior → Análisis" to ("Bottom bar → Insights" to "Uray barra → T'aqwiy"),
    "Abrir el mercado" to ("Open the market" to "Qhatuta kichay"),
    "Pregunta lo que quieras" to ("Ask anything" to "Munasqaykita tapuy"),
    "Es una orientación: la decisión siempre es tuya." to ("It's guidance: the decision is always yours." to "Yuyaychaylla: akllayqa qampatam."),
    "Probar Dinex IA" to ("Try Dinex AI" to "Dinex IA-ta malliy"),
    "Privacidad" to ("Privacy" to "Pakasqa kay"),
    "Tus datos son tuyos" to ("Your data is yours" to "Willakuyniykiqa qampatam"),
    "Solo tú puedes ver tu información. Cada permiso es opcional y puedes eliminar tu cuenta cuando quieras." to
        ("Only you can see your information. Every permission is optional and you can delete your account anytime." to
            "Qamllam willakuyniykita rikuyta atinki. Sapa saqiy munaspalla, cuentaykitataq munasqaykipi chinkachiyta atinki."),
    "Pago de clases" to ("Class payment" to "Yachaypa pagaynin"),
    "Almuerzo" to ("Lunch" to "Chawpi p'unchaw mikuy"),
    "Gasto guardado: Comida · S/ 20.00" to ("Expense saved: Food · S/ 20.00" to "Lluqsiq qullqi waqaychasqa: Mikuy · S/ 20.00"),
    "Viaje a Cusco" to ("Trip to Cusco" to "Qusquman puriy"),
    "¡Meta cumplida!" to ("Goal reached!" to "¡Munasqa hunt'asqa!"),
    "Mueve la barra para ahorrar" to ("Move the bar to save" to "Barrata kuyuchiy waqaychanaykipaq"),
    "¿En qué gasté más esta semana?" to ("What did I spend the most on this week?" to "¿Kay simanapi imapitaq aswanta tukurqani?"),
    "Toca la pregunta" to ("Tap the question" to "Tapuyta llamiy"),
    "En Comida: S/ 96. Si llevas almuerzo 2 días, ahorras unos S/ 30" to ("Food: S/ 96. If you bring lunch 2 days, you save about S/ 30" to "Mikuypi: S/ 96. Iskay p'unchaw mikuyniykita apaspaqa S/ 30 hinata waqaychanki"),
    "Importar movimientos" to ("Import transactions" to "Kuyuykunata apamuy"),
    "Abre tu Excel o CSV de Yape, Plin o tu banco. Revisa la vista previa y confirma qué vas a registrar." to
        ("Open your Excel or CSV from Yape, Plin or your bank. Check the preview and confirm what to log." to "Yape, Plin utaq bancoykiq Excel utaq CSV-ninta kichay. Ñawpaq qhawayta qhaway, ima qillqanaykitataq arí niy."),
    "Seleccionar Excel o CSV" to ("Select Excel or CSV" to "Excel utaq CSV akllay"),
    "Ocultar texto CSV" to ("Hide CSV text" to "CSV qillqata pakay"),
    "O pegar texto CSV" to ("Or paste CSV text" to "Utaq CSV qillqata k'askachiy"),
    "Contenido CSV" to ("CSV content" to "CSV ukhun"),
    "Revisar texto" to ("Review text" to "Qillqata qhaway"),
    "Preparando la vista previa…" to ("Preparing the preview…" to "Ñawpaq qhawayta wakichichkan…"),
    "Omitir movimientos repetidos" to ("Skip repeated transactions" to "Kutipasqa kuyuykunata saqiy"),
    "Egreso" to ("Expense" to "Lluqsiq qullqi"),
    "Nada se guarda sin tu confirmación. Formatos: .xlsx, .csv y .tsv; hasta 20 MB." to
        ("Nothing is saved without your confirmation. Formats: .xlsx, .csv and .tsv; up to 20 MB." to "Mana arí niptiykiqa manam imapas waqaychakunchu. Formatokuna: .xlsx, .csv, .tsv; 20 MB-kama."),
    "Datos simples para abrir en otras apps" to ("Simple data to open in other apps" to "Huk app-kunapi kichanapaq willakuy"),
    "Incluye tu plan de ahorro personalizado" to ("Includes your personalized savings plan" to "Waqaychana yuyaykuyniykita apakun"),
    "Desde" to ("From" to "Qallariy"),
    "AAAA-MM-DD" to ("YYYY-MM-DD" to "WWWW-KK-PP"),
    "Hasta" to ("To" to "Tukukuy"),
    "Ingresa fechas válidas y coloca la fecha inicial antes de la final." to ("Enter valid dates and put the start date before the end date." to "Allin p'unchawkunata qillqay, qallariq p'unchawta tukukuq p'unchawmanta ñawpaqman churay."),
    "No hay movimientos en este periodo. Elige otro o registra uno primero." to ("There are no transactions in this period. Choose another or log one first." to "Kay pachapi mana kuyuykuna kanchu. Hukta akllay utaq ñawpaqta hukta qillqay."),
    "Preparando y guardando tu reporte…" to ("Preparing and saving your report…" to "Willakuyniykita wakichispa waqaychachkan…"),
    "Formato" to ("Format" to "Formato"),
    "Periodo" to ("Period" to "Pacha"),
    "Este mes" to ("This month" to "Kay killa"),
    "Últimos 3 meses" to ("Last 3 months" to "Qhipa 3 killa"),
    "Este año" to ("This year" to "Kay wata"),
    "Elegir fechas" to ("Choose dates" to "P'unchawkunata akllay"),
    "Todo el historial" to ("All history" to "Llapan willakuy"),
    "Un aliado para tus decisiones." to ("An ally for your decisions." to "Akllayniykikunapaq yanapaq."),
    "Convierte tus movimientos en una conversación más clara sobre tu dinero." to ("Turn your transactions into a clearer conversation about your money." to "Kuyuyniykikunata qullqiykimanta aswan sut'i rimanakuyman tikray."),
    "TU CONTEXTO" to ("YOUR CONTEXT" to "KAWSAYNIYKI"),
    "Usa tu resumen de Dinex. No accede a tus bancos ni solicita claves." to ("It uses your Dinex summary. It doesn't access your banks or ask for passwords." to "Dinex huñusqaykita llamk'achin. Bancoykikunaman mana yaykunchu, pakasqa siminkunatapas mana mañakunchu."),
    "MENOS DUDAS. MÁS CLARIDAD." to ("FEWER DOUBTS. MORE CLARITY." to "PISI ISKAYCHAKUY. ASWAN SUT'I."),
    "¿Qué quieres descubrir hoy?" to ("What do you want to discover today?" to "¿Kunan imatataq tariyta munanki?"),
    "Revisa tus gastos, piensa en tu próxima compra o empieza un plan de ahorro." to ("Review your spending, think about your next purchase or start a savings plan." to "Tukusqaykita qhaway, hamuq rantinaykipi yuyay utaq waqaychana yuyaykuyta qallariy."),
    "Escribe tu pregunta…" to ("Type your question…" to "Tapuyniykita qillqay…"),
    "Vuelve mañana para preguntar" to ("Come back tomorrow to ask" to "Paqarin kutimuy tapunaykipaq"),
    "Puede equivocarse. Revisa cualquier decisión financiera." to ("It can make mistakes. Double-check any financial decision." to "Pantanmanmi. Sapa qullqi akllayta qhawariy."),
    "Gemini Nano en tu teléfono" to ("Gemini Nano on your phone" to "Gemini Nano celularniykipi"),
    "Descargar" to ("Download" to "Uraykachiy"),
    "Preparando la descarga…" to ("Preparing the download…" to "Uraykachiyta wakichichkan…"),
    "Analizando tus movimientos…" to ("Analyzing your transactions…" to "Kuyuyniykikunata t'aqwichkan…"),
    "Reportada · gracias" to ("Reported · thanks" to "Willasqa · sulpayki"),
    "Reportar" to ("Report" to "Willay"),
    "Reportar respuesta" to ("Report answer" to "Kutichiyta willay"),
    "¿Qué problema tiene?" to ("What's the problem?" to "¿Ima sasachakuytaq kan?"),
    "Comentario (opcional)" to ("Comment (optional)" to "Rimay (munaspalla)"),
    "Se enviará el texto de esta respuesta para revisarla. No se envían tus movimientos." to
        ("The text of this answer will be sent for review. Your transactions are not sent." to "Kay kutichiypa qillqan qhawanapaq kachakunqa. Kuyuyniykikunaqa mana kachakunchu."),
    "Enviar reporte" to ("Send report" to "Willayta kachay"),
    "Ofensiva o irrespetuosa" to ("Offensive or disrespectful" to "K'amiq utaq mana respetoyuq"),
    "Peligrosa o dañina" to ("Dangerous or harmful" to "Manchakuypaq utaq damuq"),
    "Información financiera incorrecta" to ("Incorrect financial information" to "Mana chiqap qullqi willakuy"),
    "Otro motivo" to ("Other reason" to "Huk imarayku"),
    "¡SUBISTE DE NIVEL!" to ("YOU LEVELED UP!" to "¡PATAMAN WICHARQANKI!"),
    "Ganaste una nueva medalla de ahorro" to ("You earned a new savings medal" to "Musuq waqaychay medallata chaskirqanki"),
    "Seguir mi racha" to ("Keep my streak" to "Siqiyta qatipay"),
    "¿Guardo este ingreso?" to ("Save this income?" to "¿Kay yaykuq qullqita waqaychanichu?"),
    "¿Guardo este gasto?" to ("Save this expense?" to "¿Kay lluqsiq qullqita waqaychanichu?"),
    "Eliminar meta" to ("Delete goal" to "Munasqata chinkachiy"),
    "Conservar meta" to ("Keep goal" to "Munasqata waqaychay"),
    "¿Deseas continuar?" to ("Do you want to continue?" to "¿Qatipayta munankichu?"),
    "Sí, registrar gasto" to ("Yes, log expense" to "Arí, lluqsiq qullqita qillqay"),
    "Cancelar gasto" to ("Cancel expense" to "Lluqsiq qullqita saqiy"),
    "Dinex IA · tu guía de dinero" to ("Dinex AI · your money guide" to "Dinex IA · qullqiyki pusaq"),
    "¿Eliminar movimiento?" to ("Delete transaction?" to "¿Kuyuyta chinkachinkichu?"),
    // Análisis
    "Mensual" to ("Monthly" to "Sapa killa"),
    "Últimos 7 días" to ("Last 7 days" to "Qhipa 7 p'unchaw"),
    "Exportar" to ("Export" to "Urquy"),
    "Una mirada más clara" to ("A clearer view" to "Aswan sut'i qhaway"),
    "Descubre tus patrones. Decide tu siguiente paso." to ("Discover your patterns. Decide your next step." to "Imayna kawsasqaykita tariy. Hamuq puriyniykita akllay."),
    "Comienza a descubrir tus patrones" to ("Start discovering your patterns" to "Imayna kawsasqaykita tariyta qallariy"),
    "Gastaste {0}% más que {1}" to ("You spent {0}% more than {1}" to "{1}-manta {0}% aswanta tukurqanki"),
    "Gastaste {0}% menos que {1}" to ("You spent {0}% less than {1}" to "{1}-manta {0}% aswan pisita tukurqanki"),
    "Tu gasto se mantuvo estable" to ("Your spending stayed steady" to "Tukusqayki kaqllam qhiparqa"),
    "el mes anterior" to ("last month" to "ñawpaq killa"),
    "la semana anterior" to ("last week" to "ñawpaq simana"),
    "{0} en gastos · {1} en ingresos" to ("{0} spent · {1} earned" to "{0} lluqsiq · {1} yaykuq"),
    "Usaste {0}% de tus ingresos registrados" to ("You used {0}% of your logged income" to "Yaykuq qullqiykimanta {0}% llamk'achirqanki"),
    "Mes: {0}" to ("Month: {0}" to "Killa: {0}"),
    "¿Dónde gastas más?" to ("Where do you spend the most?" to "¿Maypitaq aswanta tukunki?"),
    "Mapa de calor de tus lugares" to ("Heat map of your places" to "Tukusqayki kitikunapa mapan"),
    "TU RITMO" to ("YOUR PACE" to "PURIYNIYKI"),
    "Proyección de fin de mes" to ("End-of-month projection" to "Killa tukukuypi yuyaykusqa"),
    "Estimación según los ingresos registrados y tu gasto diario promedio" to
        ("Estimate based on logged income and your average daily spending" to "Yaykuq qullqiykiman, sapa p'unchaw tukusqaykimanpas hina yupasqa"),
    "PULSO DEL DINERO" to ("MONEY PULSE" to "QULLQIPA SIRK'AN"),
    "Ingresos y gastos del periodo" to ("Income and expenses for the period" to "Pachapi yaykuq lluqsiq qullqi"),
    "Balance" to ("Balance" to "Puchu"),
    "Saldo acumulado" to ("Running balance" to "Huñusqa puchu"),
    "CALENDARIO DE GASTO" to ("SPENDING CALENDAR" to "TUKUYPA WATA QHAWANAN"),
    "No registraste gastos este día." to ("You didn't log expenses this day." to "Kay p'unchawqa mana lluqsiq qullqita qillqarqankichu."),
    "Total" to ("Total" to "Llapan"),
    "Promedio/día" to ("Average/day" to "Chawpi/p'unchaw"),
    "En tu límite" to ("Within limit" to "Saywaykipi"),
    "Dentro del límite" to ("Within limit" to "Saywa ukhupi"),
    "Poco gasto" to ("Low spending" to "Pisi tukuy"),
    "Cerca" to ("Close" to "Qayllapi"),
    "Te pasaste" to ("Over" to "Llallinki"),
    "Mucho gasto" to ("High spending" to "Ancha tukuy"),
    // Entrar y crear cuenta
    "Crea tu cuenta" to ("Create your account" to "Cuentaykita ruway"),
    "Bienvenido de nuevo" to ("Welcome back" to "Sumaq kutimusqayki"),
    "Un espacio financiero personal, privado y hecho para ti." to ("A personal, private financial space made for you." to "Qampaq ruwasqa, pakasqa qullqi kiti."),
    "Inicia sesión para continuar con tu dashboard." to ("Log in to continue to your dashboard." to "Yaykuy qatipanaykipaq."),
    "NOMBRE" to ("NAME" to "SUTI"),
    "EMAIL" to ("EMAIL" to "CORREO"),
    "CONTRASEÑA" to ("PASSWORD" to "PAKASQA SIMI"),
    "CONFIRMAR CONTRASEÑA" to ("CONFIRM PASSWORD" to "PAKASQA SIMITA KASKAN"),
    "Tu contraseña segura" to ("Your secure password" to "Allin pakasqa simiyki"),
    "Repítela" to ("Repeat it" to "Kaqmanta qillqay"),
    "Requisito:" to ("Required:" to "Mañakun:"),
    "Cumple:" to ("Done:" to "Hunt'an:"),
    "Mínimo 8 caracteres" to ("At least 8 characters" to "Pisimanta 8 qillqakuna"),
    "Una letra mayúscula (A-Z)" to ("One uppercase letter (A-Z)" to "Huk hatun qillqa (A-Z)"),
    "Una letra minúscula (a-z)" to ("One lowercase letter (a-z)" to "Huk huch'uy qillqa (a-z)"),
    "Un número (0-9)" to ("One number (0-9)" to "Huk yupay (0-9)"),
    "Un símbolo (ej. ! @ # $ %)" to ("One symbol (e.g. ! @ # $ %)" to "Huk unanchay (! @ # $ %)"),
    "Leí y acepto los Términos y la Política de Privacidad, y autorizo a Dinex a tratar mis datos personales y financieros según la Ley N.° 29733." to
        ("I have read and accept the Terms and the Privacy Policy, and I authorize Dinex to process my personal and financial data under Law No. 29733." to
            "Kamachiykunata, Pakasqa kaypa kamachiynintapas ñawirispa chaskini, Dinex-tataq willakuyniyta, qullqiymanta willakuytapas Kamachiy 29733-man hina llamk'achinanpaq saqini."),
    "Crear cuenta" to ("Create account" to "Cuentata ruway"),
    "Iniciar sesión" to ("Log in" to "Yaykuy"),
    "¿Olvidaste tu contraseña?" to ("Forgot your password?" to "¿Pakasqa simiykita qunqarqankichu?"),
    "¿Ya tienes cuenta? Iniciar sesión" to ("Already have an account? Log in" to "¿Cuentayki kapunñachu? Yaykuy"),
    "¿Primera vez? Crear nueva cuenta" to ("First time? Create a new account" to "¿Ñawpaq kutichu? Musuq cuentata ruway"),
    "Continuar con Google" to ("Continue with Google" to "Google-wan qatipay"),
    "Conectando con Google…" to ("Connecting to Google…" to "Google-wan tinkuchkan…"),
    "Al iniciar sesión aceptas los Términos y la Política de Privacidad vigentes." to
        ("By logging in you accept the current Terms and Privacy Policy." to "Yaykuspaqa kunan Kamachiykunata, Pakasqa kaypa kamachiynintapas chaskinki."),
    "Un último paso" to ("One last step" to "Qhipa puriylla"),
    "Estás creando tu cuenta de Dinex con Google. Solo te lo pedimos esta vez." to
        ("You're creating your Dinex account with Google. We'll only ask you this once." to "Google-wan Dinex cuentaykita ruwachkanki. Kay kutillam mañakuyki."),
    "Crear mi cuenta" to ("Create my account" to "Cuentayta ruway"),
    "Convierte cada sol\nen una mejor decisión." to ("Turn every sol\ninto a better decision." to "Sapa solta tikray\naswan allin akllaymanta."),
    "Controla tus gastos, crea metas y deja que Dinex te acompañe con una visión clara de tu dinero." to
        ("Track your spending, create goals and let Dinex guide you with a clear view of your money." to
            "Tukusqaykita qhaway, munasqakunata ruway, Dinex-taq qullqiykita sut'ita rikuchisunki."),
    "Tus finanzas, en soles y a tu ritmo." to ("Your finances, in soles and at your pace." to "Qullqiyki, solpi, puriynikiman hina."),
    // Eliminar mi cuenta.
    "Tu sesión expiró. Vuelve a iniciar sesión." to
        ("Your session expired. Please sign in again." to "Sesionniyki tukurqun. Huktawan yaykuy."),
    "Vas a eliminar la cuenta {0} y sus datos. Es permanente: no podremos recuperarla." to
        ("You are about to delete the account {0} and its data. It's permanent: we won't be able to recover it." to "{0} cuentata willakuyninkunatawan chinkachinki. Wiñaypaqmi: manañam kutichiyta atisunchu."),
    "Se borrará" to
        ("Will be deleted" to "Chinkachisqa kanqa"),
    "Tu cuenta de acceso y la posibilidad de volver a entrar con ella." to
        ("Your login account and the ability to sign in with it again." to "Yaykunaykipaq cuentayki, manañataq chaywan yaykuyta atinkichu."),
    "Tus ingresos y gastos, con su lugar en el mapa, tus presupuestos, metas de ahorro y recordatorios." to
        ("Your income and expenses with their place on the map, your budgets, savings goals and reminders." to "Yaykumuq qullqiyki, tukusqaykipas mapapi maypi kasqanwan, presupuestoykikuna, waqaychay munasqaykikuna, yuyarichiykunapas."),
    "Los reportes que enviaste sobre respuestas de Dinex IA y tu conteo de consultas." to
        ("The reports you sent about Dinex AI answers and your question count." to "Dinex IA kutichiyninkunamanta apachisqayki willakuykuna, tapukusqayki yupaypas."),
    "En este teléfono: tus datos guardados, tu foto de perfil, los avisos de pago detectados, las fotos temporales de comprobantes y Oye Dinex." to
        ("On this phone: your saved data, your profile photo, detected payment alerts, temporary receipt photos and Hey Dinex." to "Kay celularpi: waqaychasqa willakuyniykikuna, fotoyki, tarisqa qullqi willakuykuna, comprobantekunapa pisi pachalla fotonkuna, Oye Dinex-pas."),
    "Lo que no podemos borrar desde aquí" to
        ("What we can't delete from here" to "Kaymanta mana chinkachiyta atisqanchik"),
    "La copia en otros teléfonos o navegadores donde usaste tu cuenta: se borra cuando DINEX se conecte a internet en ellos." to
        ("The copy on other phones or browsers where you used your account: it's deleted when DINEX connects to the internet on them." to "Huk celularkunapi utaq navegadorkunapi cuentaykita llamk'achisqaykipi copia: DINEX internetman chaykunapi tinkuptin chinkachisqa kanqa."),
    "Los archivos que exportaste (PDF, Excel o CSV): están donde los guardaste." to
        ("Files you exported (PDF, Excel or CSV): they stay where you saved them." to "Hurqusqayki archivokuna (PDF, Excel utaq CSV): waqaychasqayki chiqapim kachkan."),
    "Las consultas ya enviadas a Google Gemini, que siguen las condiciones de Google." to
        ("Questions already sent to Google Gemini, which follow Google's terms." to "Google Gemini-man ña apachisqa tapukuykuna, Google-pa kamachiyninkunaman hina."),
    "Si quieres conservar tus movimientos, primero exporta un reporte desde Movimientos." to
        ("If you want to keep your transactions, first export a report from Transactions." to "Kuyuyniykikunata waqaychayta munaspaqa, ñawpaqta Kuyuykunamanta huk willakuyta hurquy."),
    "Entiendo que esta acción es permanente y no se puede deshacer." to
        ("I understand this action is permanent and can't be undone." to "Yachanim kayqa wiñaypaq kasqanta, manataq kutichiy atikunchu."),
    "Reintentar" to
        ("Try again" to "Huktawan ruway"),
    "Revisando cómo confirmar que eres tú…" to
        ("Checking how to confirm it's you…" to "Qampuni kasqaykita imaynata takyachinapaq qhawachkan…"),
    "Para tu seguridad, confirma que eres tú." to
        ("For your security, confirm it's you." to "Allin kanaykipaq, qampuni kasqaykita takyachiy."),
    "Tu contraseña de DINEX" to
        ("Your DINEX password" to "DINEX pakasqa simiyki"),
    "Eliminar mi cuenta para siempre" to
        ("Delete my account forever" to "Cuentayta wiñaypaq chinkachiy"),
    "o" to
        ("or" to "utaq"),
    "Confirmar con Google y eliminar" to
        ("Confirm with Google and delete" to "Google-wan takyachiy, chinkachiytaq"),
    "No pudimos confirmar tu identidad desde aquí. Escribe a dinex401@gmail.com para eliminar tu cuenta." to
        ("We couldn't confirm your identity from here. Write to dinex401@gmail.com to delete your account." to "Kaymanta mana takyachiyta atirqanchikchu qampuni kasqaykita. Cuentayki chinkachinapaq dinex401@gmail.com-man qillqamuway."),
    "No, quiero conservar mi cuenta" to
        ("No, I want to keep my account" to "Manam, cuentayta waqaychayta munani"),
    "¿Dudas? Escríbenos a dinex401@gmail.com." to
        ("Questions? Write to us at dinex401@gmail.com." to "¿Tapukuyniyki kanchu? dinex401@gmail.com-man qillqamuway."),
    "Tu cuenta no se eliminó." to
        ("Your account was not deleted." to "Cuentaykiqa manam chinkachisqachu."),
    "La contraseña no es correcta." to
        ("The password is not correct." to "Pakasqa simiqa manam allinchu."),
    "Tu sesión expiró. Cierra sesión, vuelve a entrar y repite la eliminación." to
        ("Your session expired. Log out, sign in again and repeat the deletion." to "Sesionniyki tukurqun. Lluqsiy, huktawan yaykuy, chinkachiytataq kutipay."),
    "Necesitas internet para eliminar tu cuenta." to
        ("You need internet to delete your account." to "Cuentayki chinkachinapaqqa internet kanan."),
    "Firebase pidió confirmar tu identidad otra vez. Vuelve a intentarlo." to
        ("Firebase asked to confirm your identity again. Please try again." to "Firebase huktawan qampuni kasqaykita takyachiyta mañakun. Huktawan ruway."),
    "No hay conexión a internet. Revisa tu conexión e inténtalo otra vez." to
        ("No internet connection. Check your connection and try again." to "Mana internet kanchu. Tinkuyniykita qhaway, huktawan ruway."),
    "Confirma con Google para eliminar tu cuenta." to
        ("Confirm with Google to delete your account." to "Cuentayki chinkachinapaq Google-wan takyachiy."),
    "Escribe tu contraseña o confirma con Google." to
        ("Enter your password or confirm with Google." to "Pakasqa simiykita qillqay utaq Google-wan takyachiy."),
    "Esa no es la cuenta que quieres eliminar. Usa la misma cuenta con la que entraste." to
        ("That's not the account you want to delete. Use the same account you signed in with." to "Manam chayqa chinkachiy munasqayki cuentachu. Yaykusqayki kikin cuentallata llamk'achiy."),
    "Firebase no permitió borrar tus datos. Inténtalo más tarde o escribe a dinex401@gmail.com." to
        ("Firebase didn't allow deleting your data. Try later or write to dinex401@gmail.com." to "Firebase mana willakuyniykikunata chinkachiyta saqirqanchu. Qhipaman ruway utaq dinex401@gmail.com-man qillqamuway."),
    "No pudimos terminar de borrar tus reportes de Dinex IA. Escribe a dinex401@gmail.com." to
        ("We couldn't finish deleting your Dinex AI reports. Write to dinex401@gmail.com." to "Dinex IA willakuyniykikunata mana tukuyta chinkachiyta atirqanchikchu. dinex401@gmail.com-man qillqamuway."),
    "Tu cuenta y tus datos fueron eliminados." to
        ("Your account and your data were deleted." to "Cuentayki willakuyniykikunapas chinkachisqañam."),
    "Esta cuenta fue eliminada. Borramos también sus datos de este equipo." to
        ("This account was deleted. We also removed its data from this device." to "Kay cuentaqa chinkachisqañam. Kay equipomantapas willakuyninkunata chinkachirqanchikmi."),
)

// Las claves del diccionario principal ganan si se repiten en DinexAppTranslations.
private val DinexAllTranslations: Map<String, Pair<String, String>> = DinexAppTranslations + DinexTranslations
private val DinexEnglish: Map<String, String> = DinexAllTranslations.mapValues { it.value.first }
private val DinexQuechua: Map<String, String> = DinexAllTranslations.mapValues { it.value.second }
