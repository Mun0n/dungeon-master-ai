# Visual redesign scope

Status: scope note, revised 12 November 2026. This is not a milestone, and [ROADMAP.md](ROADMAP.md) stays where it is. Milestone 11 remains the last completed milestone. The menu, character creation, the scene screen, the journal, the inventory, and the status sheet now follow this note. They are not a new milestone.

The three screenshots from 12 November 2026 are the layout: the Unwritten menu with the framed picture and the three rows, the character sheet with the three class cards, and the scene with the picture, the chronicle, the stored check, and the decision rows. The YAML tokens in the Stitch `DESIGN.md` are the colors, type, and spacing. Where that file's prose names a different hex, the YAML wins. `code.html` is a measurement reference for spacing when a screenshot is hard to read. Its play screen is a different composition, with vials, audio, and a perception formula, and that composition is not the layout.

This pass restyles presentation. `AdventureEngine` and `GuardiansBridgeEngine` stay the writers of state. The screen does not roll, and it does not change health, inventory, clock, clues, evidence, or the ending. Room, `valdumbria.json`, and the backend stay as they are. No Gradle dependency is added. The three font files are already bundled under `res/font`.

## Product decisions

Three decisions govern the menu and the later scene. The menu slice implements the first two. It does not implement the third.

1. The Guardian's Bridge is a development prototype. Its engine, its screen, and its tests stay in the project. The public Unwritten menu does not open it, in debug or in release. There is no second, quieter control that leads there.
2. The title is the Unwritten menu, not a restyle of the old two-game picker. The menu reads a list of published adventures. That list has one entry, Las campanas de Valdumbría. A later adventure is another entry in that list. This note does not add a second adventure, a coming-soon slot, or a catalog screen.
3. Play becomes a modern gamebook. A scene shows two to four relevant decisions. When that scene allows a written action, the player reaches it through "Intentar otra cosa". The new scene design has no permanent text field. That change is not part of the menu slice, and this slice does not change the narrative engine.

## What the three screens already are

| Stitch screen | Surface that exists | What the player can do |
| --- | --- | --- |
| Title, "Continuar partida" | The Unwritten menu, then the Valdumbría scene when that save exists | Continue the save, start a new game after confirmation when a save exists, or read how to play. The bridge is not offered. |
| "Crea tu personaje" | Valdumbría creation, and the bridge creation beside it | Name and class. `Character.create` still starts the game. |
| "La plaza vacía" | The Valdumbría scene in `ValdumbriaScreen` | Read the committed narration, see the stored check, send an existing action id, or send free text. Inventario, Diario, and Estado stay read-only pages. |

Section 3 is the scene that is on screen now, including "Intentar otra cosa" and no permanent text field.

## Tokens

Use these YAML values. Do not also introduce the prose palette.

| role | hex | where |
| --- | --- | --- |
| background, surface | `#13131B` | the screen behind every panel. The root surface paints this. |
| surface lowest | `#0D0D16` | `surfaceContainerLowest`. The header and the free-text slab. |
| surface low | `#1B1B24` | `surfaceContainerLow`. The chronicle panel. |
| surface container | `#1F1F28` | `surfaceContainer`. A choice row and a class card. |
| surface high | `#292933` | `surfaceContainerHigh`. A pressed or selected slab. |
| primary | `#E9C176` | titles, the clock, borders, Enviar |
| on primary | `#412D00` | the label on a gold control |
| primary container | `#C5A059` | the gold edge of a selected card |
| life | `#8B1E2D` | the filled part of the life bar |
| on surface | `#E4E1EE` | chronicle text and choice labels |
| on surface variant | `#D1C5B4` | secondary labels |
| outline | `#4E4639` | a hairline that is not gold |

Playfair Display is for the wordmark, the adventure title, and the place name. Newsreader is for the chronicle and the name field. Space Grotesk is for the clock digits, life, roll numbers, and the small labels. Body text stays at least the current readable size. A large font scale wraps. It does not clip.

Cards use an 8dp corner. Chips and the life bar use a 4dp corner. The outer margin is 12dp. Controls that send an action stay at least 48dp tall.

## 1. Title

The app opens on the Unwritten menu. The menu draws `publishedAdventures()`, which is the extension point. Today that list is one record: id `valdumbria`, title "Las campanas de Valdumbría", genre "Terror gótico", and the premise below. The screen does not invent a second row. "Elegir otra partida" returns here and leaves both saves where they are. It does not reveal the bridge.

The composition follows the Stitch menu HTML.

- The backdrop is the village still, tall enough that the storm and the bell tower stay the larger picture. A light veil keeps that upper stretch readable, and the still fades into the page color under the title. TalkBack does not treat that picture as the player's location. The church still is not the menu picture.
- The helmet is `menu_helmet.png`, the transparent flaming helm. It sits on the lower fade, smaller than the village above it. TalkBack does not treat it as the player's location.
- The wordmark "UNWRITTEN" uses the fixed gold. The English line sits under it, in small capitals.
- The adventure title sits in a bar. The bar has no icons.
- The italic line under the bar is the Stitch hook. It does not name a person, a proof, or a cause.
- "Continuar partida", when a save exists, is the life-colored row. Its second line is the saved place and `clockLabel`. It does not show a chapter, a level, or a progress fill.
- "Nueva partida" and "Cómo jugar" are the two surface rows. Their icons are Material Symbols, the same glyphs as the HTML.
- The footer line about the third bell, and "v1.0.4 • UNWRITTEN STUDIOS", are static text.

The menu reads the adventure save before it shows the actions. "Continuar partida" appears only when that adventure has a save. "Nueva partida" and "Cómo jugar" are always there.

- A save exists. "Continuar partida" is the gold control. The line under it is the saved location name and `clockLabel`, for example "Plaza · 22:00". It opens the scene. It does not show a chapter number.
- "Nueva partida" is always shown, with the line "Forjar un nuevo avatar e iniciar el códice". With no save, it opens creation. With a save, it first asks "¿Borrar esta partida?". Confirming deletes that adventure snapshot and then opens creation. Cancelling leaves the save where it is. The dialog is not skipped. The bridge save is not deleted.
- "Cómo jugar" opens a page on this menu. It explains the decisions, the d20 plus Fuerza, Agilidad, or Inteligencia, the difficulties 8, 12, 16, and 20, the class attributes, the 22:00 clock, and that life at 0 ends that game. It does not add a rule and it does not change a save.

## 2. Creation

Creation still asks for a name and one of the three classes, then calls `Character.create`. "Comenzar aventura" stays disabled until the trimmed name is not blank. A name the constructor rejects still shows "El personaje no puede empezar."

The screen follows the creation capture: heading, name field, three class cards, and a button pinned to the bottom. The class cards show the Spanish names and the numbers the classes already have. Each number is also a bar filled to that value out of the attribute range, 1 to 5. The bars are not out of 10. A portrait choice, Masculino or Femenino, swaps the picture on every card and is stored on the character. Femenino shows Guerrera, Exploradora, and Erudita, including on Estado after the game is saved. Masculino keeps Guerrero, Explorador, and Erudito. A save without a presentation stays masculine. It does not change the class numbers. Each card keeps one portrait on the left and the attributes on the right. The cards do not list oaths or extra skills. The button reads "Comenzar aventura" and stays disabled until the trimmed name is not blank.

| class | Fuerza | Agilidad | Inteligencia |
| --- | --- | --- | --- |
| Guerrero | 4 | 3 | 2 |
| Explorador | 2 | 4 | 3 |
| Erudito | 2 | 2 | 5 |

The selected card uses the gold edge. The other two stay on the surface color. The sentence already shipped stays under the cards: "Crea un personaje. La clase fija la Fuerza, la Agilidad y la Inteligencia." The bridge creation is unchanged. It still starts the bridge.

## 3. Scene

This section is the scene on screen. It does not change the narrative engine. The 11:47 capture is the chrome: the life marks, the place picture, the chronicle, the stored check, the decision rows, and the bar under the column. Audio, notifications, a profile control, "Acto I", "Viaje letrado", "Salud de sangre", "Tañido", a place subtitle, a fog bonus, "Éxito crítico", Percepción, a gold first decision, Tirada, Mapa, and a permanent text field stay off the screen.

The header stays fixed. It shows the wordmark, the adventure title, a 60dp analog clock, and life. It does not print the digits. TalkBack still announces the hour from that clock. The pictures are not a separate announcement. Its hands are the same pictures as Estado, painted a little thicker so the shaft still reads at that size. The Estado clock is not thickened. The clock is three pictures: the gothic face, the hour hand, and the minute hand. The hands are drawn pointing at twelve and then rotated by the stored hour and minute. Each picture pivots on its medallion. The counterweight below that medallion is not painted, and the hour hand is drawn shorter than the minute hand, so the tip marks the hour and the minute. When this process has just seen midnight, the header still adds "Medianoche" beside the clock. A later visit shows the clock without that word.

Life stays the words "Vida n/10", and TalkBack still announces "Vida n de 10". The ten separate marks may become one bar filled to the stored fraction, because the fraction remains text. Death still shows an empty bar and "Vida 0/10".

The picture is the current location's existing still, full width, at most 180dp tall. On that picture the screen may show the act title already stored for that place, and the location name. The act titles are "Llegada", "Investigación", and "Medianoche y consecuencias". The place name is "Plaza", "Taberna", "Iglesia", "Cementerio", "Archivo", or "Cripta". A small chip may show the character name and the Spanish class name. Those four values are already on the confirmed state or the document.

The scrolling column keeps this order:

1. The picture.
2. The committed narration. A chrome label "Crónica" may sit above it. The prose is the stored line, not a rewrite.
3. The life-change line, the Inés line, and the notice, when the current rules already show them.
4. The stored check, when the latest confirmed resolution has one.
5. The decision buttons, in document order.

The first time a new narration is published during this visit, the column still aligns the top of that narration with the top of the viewport, once. The picture above it is reached by scrolling up. The player's own scroll is then left alone. Opening Inventario, Diario, or Estado still remembers the offset, and a restore still does not move the column.

The check plate shows the stored roll large, then the words already defined for a check: "Dado", "Atributo", "Modificador", "Total", "Dificultad", and "Supera la prueba" or "No supera la prueba". The attribute is Fuerza, Agilidad, or Inteligencia. The modifier is the stored modifier. Skip and an animator scale of zero still show those stored numbers at once. The screen still does not roll.

Each decision is the same row. The label is `choiceLabel`, and the button sends the action id. When that action has a check, the row may also show that check's attribute. The row does not rank the first action with a different color. While a turn is in flight, the rows and "Enviar" stay disabled. The column shows the wait on a plate under the chronicle: a quill, three marks, and "La partida está resolviendo la acción." The marks pulse in turn. When animation is reduced, they stay still. Pending narration, the retry button, and the paused notice stay in that column.

The prompt "¿Qué haces, {name}?" may sit above the rows while the scene is open. The name is the stored character name.

Partida, Diario, Inventario, and Estado stay in the bar under the column. Each shows an outlined icon above its label. The chosen icon and label use the gold. The others stay on the muted text color. Partida shows the scene. The other three replace that column and keep the bar. The chosen label is marked. The system back also returns to the scene. Diario, Inventario, and Estado write nothing, and they remain reachable on an ending. There is no separate page with Volver. While the keyboard is open, that bar yields its space. After "Intentar otra cosa", the field and "Enviar" stay above the keys. The placeholder stays "O escribe una acción". The field still starts at one line, grows to four, and clears after a submit. The 500-character limit stays. Do not draw that field before the player asks for it.

On an ending, the decision rows, "Intentar otra cosa", and the free-text row hide. The ending plate and its existing title stay in the column.

## 4. Inventory

Inventario replaces the scene column and keeps the header and the bar. The page is read-only. It does not use an item, drop an item, or change the bag.

The column shows "Inventario", the line "Objetos que pesan en el cinto y en la memoria", and the mark "LLEVAS n / 6". TalkBack announces the existing sentence "Llevas n de 6". Six slots are always drawn, in inventory order, then empty slots through `Character.MAX_INVENTORY`. A slot shows its roman numeral. A carried item shows its Spanish name. The three known stills are `coin_purse` ("Bolsa de monedas", "Monedas de cobre y plata gastada"), `iron_pin` ("Ganzúa de Tomás", "Hierro doblado con tierra de camposanto"), and `linen_bandage` ("Lino de Mara", "Tira limpia para vendar heridas"). An item without a still shows its name and no picture. An empty slot has no name and is not a button.

## 5. Status

Estado replaces the scene column and keeps the header and the bar. It is a read-only sheet. It shows the stored name, the class portrait already used at creation, the class role, the Spanish class name, the location name, life, the clock, and Fuerza, Agilidad, and Inteligencia. The clock on this sheet is the same three pictures, drawn larger. The hour text stays beside the vital label. TalkBack still reads that text. The pictures are not a separate announcement. The attribute bars are filled out of 5. Life uses the same ten marks as the header. A death ending shows those marks empty and "Vida 0/10". The sheet adds four labels: "Lugar", "Estado vital", "Atributos", and "Escala /5". It does not add a level, a skill, equipment, or a control that writes state.

## 6. Journal

Diario replaces the column and keeps the header and the bar. It stays read-only. The title is "Diario". Four sections follow, in this order, using the copy already in the milestone 10 table.

- Pistas. Each discovered clue is its stored sentence, with a roman numeral for its place in that visible list. The numeral is not an identifier. Empty: "Todavía no tienes pistas."
- Pruebas. Each discovered proof is its stored sentence on a heavier plate, with a mute seal. The seal has no words and no document number. Empty: "Todavía no tienes pruebas."
- Lugares. Each discovered place shows its stored name and the existing still for that location. The row does not travel. Empty: "Todavía no has descubierto otros lugares."
- Reciente. Each narration line already shown, in italic, with no caption. Empty: "Todavía no hay líneas que recordar."

The page does not show undiscovered clues, proofs, or places, and it does not show how many remain. Opening the journal does not read Room. It decodes the still of each discovered place.

## Left out

These are visible on the mock and are not part of this pass.

- Compendio, Ajustes, and the audio pill. They would look like controls and they do not open anything. "Cómo jugar" is a page of the rules already in force.
- The progress strip on "Continuar partida", and any percent. There is no progress counter.
- The daily talisman card. It names an object and a bonus the game does not have.
- "Toca para entrar en la niebla" as a second way in.
- Audio playback, and a notification control.
- Chapter numbers, "Capítulo II", and invented place subtitles such as "La plaza vacía".
- "Salud" in place of "Vida".
- Skill names, "Éxito sigiloso", "Pasar desapercibido", and "Destino". A check has an attribute, a die, a modifier, a total, and a difficulty.
- The class photographs, including the bridge photograph on the Explorador card.
- Class oaths such as "Juras proteger la aldea."
- A clock or "Suena la primera campanada" on creation. The clock is 22:00 when a game starts, and creation has no clock yet.
- A prefilled free-text sentence. The sample line in the mock is not a default.
- A gold row that marks the first decision as the recommended one.
- Vials, an inventory count on the scene, and the perception formula from `code.html`.
- A settings screen, a second adventure, or a landscape layout. The village still from the menu HTML is the menu backdrop.
- The Guardian's Bridge as a public menu entry. Its code and its tests stay. The menu does not open them.

## Later, if implementation is requested

The expected files are the presentation screens, `Theme.kt`, the font files, and the JVM tests that lock any new chrome string. `domain` stays free of Android and Compose. The copy table in the milestone 10 design remains the source for every control this note does not explicitly add. The new strings this note allows are the wordmark, the English tagline, the two static genre lines, the three premise sentences, "Continuar partida", "Nueva partida", "Crónica", the attribute gloss, "¿Qué haces, {name}?", "Objetos que pesan en el cinto y en la memoria", "LLEVAS n / 6", the three item glosses in section 4, and the four status labels in section 5.

A phone check of the menu confirms one published adventure, "Continuar partida" when that save exists, "Nueva partida" in either case, and "Cómo jugar". Confirming a new game removes the adventure save. Cancelling leaves it. The bridge is not on the screen.
