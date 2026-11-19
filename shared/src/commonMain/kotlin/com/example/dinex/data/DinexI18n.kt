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
)

private val DinexEnglish: Map<String, String> = DinexTranslations.mapValues { it.value.first }
private val DinexQuechua: Map<String, String> = DinexTranslations.mapValues { it.value.second }
