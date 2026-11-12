package com.dungeonmasterai.presentation

import com.dungeonmasterai.domain.character.Character
import com.dungeonmasterai.domain.character.CharacterClass

internal object PlayerCopy {
    const val PICK_GAME: String = "Elige una partida. Cada una guarda la suya."
    const val BRIDGE_NAME: String = "El puente del guardián"
    const val OTHER_GAME: String = "Elegir otra partida"
    const val WORDMARK: String = "UNWRITTEN"
    const val MENU_KICKER: String = "Narrativas interactivas"
    const val MENU_TAGLINE: String = "Your story is not written yet."
    const val CONTINUE_GAME: String = "Continuar partida"
    const val NEW_GAME: String = "Nueva partida"
    const val NEW_GAME_DETAIL: String = "Forjar un nuevo avatar e iniciar el códice"
    const val MENU_QUOTE: String =
        "Una aventura de rol y misterio gótico guiada por el destino. Escucha el tañido de la bruma."
    const val MENU_CHIME: String = "Campana N° 3 tañendo en la lejanía..."
    const val MENU_MARK: String = "v1.0.4 • UNWRITTEN STUDIOS"
    const val HOW_TO_PLAY: String = "Cómo jugar"
    const val HOW_TO_PLAY_DETAIL: String = "Decisiones, dados y destino"
    const val HOW_DECISIONS: String =
        "Eliges una de las decisiones de la escena. Si la escena lo permite, «Intentar otra cosa» abre una acción que escribes tú."
    const val HOW_DICE: String =
        "Algunas acciones tiran un dado de 20 y le suman Fuerza, Agilidad o Inteligencia. La dificultad es 8, 12, 16 o 20. El total tiene que alcanzarla."
    const val HOW_FATE: String =
        "La clase fija esos tres números. La noche empieza a las 22:00 y avanza cuando una acción se resuelve. Si la vida llega a 0, esa partida termina."
    const val CONFIRM_ERASE_TITLE: String = "¿Borrar esta partida?"
    const val CONFIRM_ERASE_BODY: String = "La partida guardada se pierde y no se puede recuperar."
    const val CONFIRM_ERASE: String = "Borrar y empezar"
    const val CANCEL: String = "Cancelar"
    const val DISCARD_FAILED: String = "No se ha podido borrar la partida."
    const val VALDUMBRIA_PREMISE: String =
        "El pueblo dejó de tocar a difunto. Nadie recuerda por qué. Esta noche, las campanas suenan solas."
    const val CREATE_CHARACTER: String =
        "Crea un personaje. La clase fija la Fuerza, la Agilidad y la Inteligencia."
    const val CREATE_HEADING: String = "Crea tu personaje"
    const val CREATE_QUESTION: String = "¿Quién se atreve a cruzar la niebla eterna de Valdumbría?"
    const val ADVENTURER_NAME: String = "Nombre del aventurero"
    const val CHOOSE_CLASS: String = "Elige tu vigor arcano"
    const val PORTRAIT: String = "Retrato"
    const val PORTRAIT_MASCULINE: String = "Masculino"
    const val PORTRAIT_FEMININE: String = "Femenino"
    const val BEGIN_ADVENTURE: String = "Comenzar aventura"
    const val NAME: String = "Nombre"
    const val BEGIN: String = "Comenzar"
    const val CHARACTER_CANNOT_START: String = "El personaje no puede empezar."
    const val HOUR: String = "Hora"
    const val LIFE: String = "Vida"
    const val PLACE: String = "Lugar"
    const val VITAL_STATE: String = "Estado vital"
    const val ATTRIBUTES_HEADING: String = "Atributos"
    const val ATTRIBUTE_SCALE: String = "Escala /5"
    const val MIDNIGHT: String = "Medianoche"
    const val INVENTORY: String = "Inventario"
    const val SCENE: String = "Partida"
    const val JOURNAL: String = "Diario"
    const val CHRONICLE: String = "Crónica"
    const val STATUS: String = "Estado"
    const val BACK: String = "Volver"
    const val FROM_THE_RULES: String = "Según las reglas"
    const val TRY_SOMETHING_ELSE: String = "Intentar otra cosa"
    const val CONTINUE: String = "Continuar"
    const val WRITE_ACTION: String = "O escribe una acción"
    const val BRIDGE_WRITE: String = "Acción libre"
    const val SEND: String = "Enviar"
    const val RETRY_NARRATION: String = "Reintentar la narración"
    const val RESOLVING: String = "La partida está resolviendo la acción."
    const val NARRATION_PENDING: String = "La narración está pendiente."
    const val ACTION_LENGTH: String = "La acción debe tener entre 1 y 500 caracteres."
    const val NO_ACTION: String = "No se ha hecho ninguna acción."
    const val NARRATOR_UNAVAILABLE: String =
        "La partida no ha podido consultar al narrador. Puedes intentarlo de nuevo."
    const val ACTION_UNAVAILABLE: String = "Esa acción no está disponible."
    const val SCENE_STILL_WAITING: String = "La escena sigue en espera."
    const val SCENE_WAITING: String = "La escena está en espera."
    const val SCENE_NOT_SAVED: String = "La escena no se ha guardado."
    const val CONFIRMED_UNREADABLE: String = "No se ha podido leer la aventura confirmada."
    const val SAVE_UNREADABLE: String = "No se ha podido leer la aventura guardada."
    const val SAVE_FAILED: String = "El guardado ha fallado. Se mantiene el estado anterior."
    const val OTHER_ACTION_SAVED: String = "Otra acción se ha guardado antes."
    const val ACTION_REJECTED: String = "La acción ha sido rechazada."
    const val NARRATION_MISMATCH: String = "La narración no coincide con el estado guardado."
    const val NARRATION_RETRY: String = "El resultado está guardado. La narración se puede reintentar."
    const val NARRATION_NOT_SAVED: String = "La narración no se ha guardado."
    const val GAME_ALREADY_SAVED: String = "Ya hay una partida guardada."
    const val SKIP_ROLL: String = "Omitir"
    const val DIE: String = "Dado"
    const val ATTRIBUTE: String = "Atributo"
    const val MODIFIER: String = "Modificador"
    const val TOTAL: String = "Total"
    const val DIFFICULTY: String = "Dificultad"
    const val CHECK_SUCCESS: String = "Supera la prueba"
    const val CHECK_FAILURE: String = "No supera la prueba"
    const val COIN_PURSE: String = "Bolsa de monedas"
    const val INVENTORY_LINE: String = "Objetos que pesan en el cinto y en la memoria"
    const val COIN_PURSE_GLOSS: String = "Monedas de cobre y plata gastada"
    const val IRON_PIN_GLOSS: String = "Hierro doblado con tierra de camposanto"
    const val LINEN_GLOSS: String = "Tira limpia para vendar heridas"
    const val CLUES: String = "Pistas"
    const val EVIDENCE: String = "Pruebas"
    const val HYPOTHESES: String = "Hipótesis"
    const val HYPOTHESIS_NOTE: String = "Anotarla no la convierte en un hecho."
    const val NO_HYPOTHESES: String = "Todavía no has anotado una hipótesis."
    const val CONCLUSIONS: String = "Conclusiones demostradas"
    const val NO_CONCLUSIONS: String = "Todavía no hay una conclusión demostrada."
    const val HYPOTHESIS_HINT: String = "Escribe una hipótesis"
    const val NOTE_HYPOTHESIS: String = "Anotar"
    const val HYPOTHESIS_LENGTH: String = "La hipótesis debe tener entre 1 y 500 caracteres."
    const val HYPOTHESIS_DUPLICATE: String = "Esa hipótesis ya está en el diario."
    const val HYPOTHESIS_CLOSED: String = "La partida ya ha terminado."
    const val PLACES: String = "Lugares"
    const val RECENT: String = "Reciente"
    const val NO_CLUES: String = "Todavía no tienes pistas."
    const val NO_EVIDENCE: String = "Todavía no tienes pruebas."
    const val NO_PLACES: String = "Todavía no has descubierto otros lugares."
    const val NO_RECENT: String = "Todavía no hay líneas que recordar."
    const val LOG: String = "Registro"
    const val BRIDGE_ONGOING: String = "El puente del guardián"
    const val BRIDGE_CROSSED: String = "Has cruzado el puente"
    const val BRIDGE_DEFEATED: String = "Has sido derrotado"
    const val PERSUADE: String = "Convencer"
    const val PAY: String = "Pagar"
    const val ATTACK: String = "Atacar"
    const val SCENE_OVER: String = "La escena ya ha terminado."
    const val GUARDIAN_CONFUSED: String = "El guardián no entiende esa acción."
    const val AMBIGUOUS: String = "Esa acción intenta hacer más de una cosa."
    const val NOTHING_TO_PAY: String = "No llevas nada que el guardián acepte."
    const val LOST_OBJECT: String = "Pierdes un objeto."
    const val ACTION_FALLBACK: String = "Acción"
    const val STRENGTH: String = "Fuerza"
    const val AGILITY: String = "Agilidad"
    const val INTELLIGENCE: String = "Inteligencia"
    const val WARRIOR: String = "Guerrero"
    const val SCOUT: String = "Explorador"
    const val SCHOLAR: String = "Erudito"
    const val INES_WARNED: String =
        "Inés está avisada. Las campanas no han sonado, así que su destino no está decidido."
    const val INES_SAVED: String = "Medianoche: Inés está a salvo."
    const val INES_LOST_WITH_BELLS: String =
        "Medianoche: Inés está perdida. Una protección que acabó al sonar las campanas no la salvó."
    const val INES_LOST: String = "Medianoche: Inés está perdida."
    const val INES_UNRECORDED: String = "Ha sonado la medianoche. El destino de Inés no consta."
    const val TEXT_LIMIT: Int = 500

    fun classRole(characterClass: CharacterClass): String = when (characterClass) {
        CharacterClass.WARRIOR -> "Clase marcial"
        CharacterClass.SCOUT -> "Clase sigilo"
        CharacterClass.SCHOLAR -> "Clase mística"
    }

    fun className(characterClass: CharacterClass, feminine: Boolean = false): String = when (characterClass) {
        CharacterClass.WARRIOR -> if (feminine) "Guerrera" else WARRIOR
        CharacterClass.SCOUT -> if (feminine) "Exploradora" else SCOUT
        CharacterClass.SCHOLAR -> if (feminine) "Erudita" else SCHOLAR
    }

    fun classCard(characterClass: CharacterClass, feminine: Boolean = false): String {
        val name = className(characterClass, feminine)
        return "$name. $STRENGTH ${characterClass.strength}, " +
            "$AGILITY ${characterClass.agility}, $INTELLIGENCE ${characterClass.intelligence}."
    }

    fun carrying(count: Int): String = "Llevas $count de ${Character.MAX_INVENTORY}"

    fun carryingMark(count: Int): String = "LLEVAS $count / ${Character.MAX_INVENTORY}"

    fun lifeChange(previous: Int, next: Int): String = "Vida $previous → $next"

    fun lostItem(name: String): String = "Pierdes $name."

    fun lifeAnnouncement(current: Int, max: Int): String = "$LIFE $current de $max"

    fun whatDoYouDo(name: String): String = "¿Qué haces, $name?"

    fun textLimitAnnouncement(): String = "Como máximo $TEXT_LIMIT caracteres."
}
