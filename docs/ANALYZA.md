# PurrtechQuest — architektonická analýza

Verze: 1.0 · Autor: senior architekt/programátor (Claude) · Kontext: Paper plugin, jeden server, survival/RPG s custom itemy, NPC integrace (Citizens + FancyNPCs), zatím bez licencování.

## 1. Shrnutí a cíl

PurrtechQuest je systém pro **tvorbu a plnění questů** na survival serveru s RPG prvky (custom itemy, custom moby). Admin definuje questy (cíle/objektivy + odměny), hráč je přijímá a plní, plugin trackuje progres a vyplácí odměny. Klíčové vlastnosti návrhu:

- **Storage-agnostic** — dnes SQLite (jeden server), zítra MySQL bez přepisování business logiky.
- **NPC-agnostic** — questy lze zadávat/odevzdávat přes Citizens i FancyNPCs (i bez NPC pluginu vůbec — GUI/příkazy fungují vždy).
- **RPG-ready** — objektivy a odměny umí pracovat s custom itemy (ItemsAdder/Oraxen) a custom moby (MythicMobs), ale nejsou na nich závislé (fallback na vanilla `Material`/`EntityType`).
- **Rozšiřitelný** — veřejné API a event systém, aby na PurrtechQuest mohly navazovat další pluginy/addony.

## 2. Technologický stack

| Vrstva | Volba | Poznámka |
|---|---|---|
| Platforma | Paper API 1.21.11, `api-version: 1.21.11` | už nastaveno v projektu |
| Jazyk / build | Java 21, Gradle Kotlin DSL | už nastaveno |
| Distribuce | `com.gradleup.shadow` (shadowJar) | nutné shadovat HikariCP + sqlite-jdbc, relocation do `eu.purrtech.purrtechQuest.libs.*` |
| Perzistence progresu | SQLite (výchozí) přes HikariCP, `PlayerDataRepository` interface s druhou implementací pro MySQL (Phase 4) | žádný ORM — čisté JDBC, jednoduché a rychlé |
| Definice questů | YAML soubory (`quests/*.yml`) + in-game GUI editor zapisující do stejných souborů | verzovatelné, čitelné, editovatelné i mimo hru |
| Text/lokalizace | Adventure `MiniMessage`, `lang/cs.yml` + `lang/en.yml`, per-hráč `Player#locale()` | konzistentní s CZ/EN přístupem v my-shopu |
| Příkazy | Paper Brigadier (`Commands` lifecycle event, `LifecycleEventManager`) | nativní tab-completion, žádná externí knihovna |
| Soft-depends | Vault (ekonomika), PlaceholderAPI, Citizens, FancyNpcs, ItemsAdder, Oraxen, MythicMobs | vše `softdepend` v `paper-plugin.yml`, detekce za běhu, nikdy hard dependency |
| Metriky | bStats (volitelně, Phase 6) | |
| Testy | JUnit 5 + MockBukkit pro service/logic vrstvu | GUI a eventy ověřovat přes `run-paper` (task už v build.gradle.kts) |

## 3. Doménový model

```
Quest
 ├─ id: String (stabilní klíč, např. "mine_iron_1")
 ├─ displayName, description (MiniMessage stringy nebo lang klíče)
 ├─ category: String (volitelné třídění v GUI)
 ├─ objectives: List<QuestObjective>
 ├─ rewards: List<QuestReward>
 ├─ requiredQuests: List<String>  (prerekvizity — chain/návaznost)
 ├─ repeatable: boolean, cooldown: Duration
 ├─ autoStart: boolean (bez nutnosti "přijmout")
 └─ questGiver: QuestGiverRef? (napojení na NPC, viz kap. 6)

QuestObjective (sealed interface, per-typová implementace)
 ├─ type: ObjectiveType (KILL_ENTITY, BREAK_BLOCK, PLACE_BLOCK, COLLECT_ITEM,
 │                        CRAFT_ITEM, FISH, REACH_LOCATION, INTERACT_BLOCK,
 │                        TALK_TO_NPC, CUSTOM)
 ├─ target: String (Material/EntityType id, custom-item namespace id, MythicMobs id, …)
 ├─ amount: int
 └─ meta: Map<String,String> (rozšiřitelné — např. radius pro REACH_LOCATION)

QuestReward (sealed interface)
 ├─ MoneyReward(amount)         — přes Vault
 ├─ ItemReward(ItemStack)       — podporuje i custom itemy (serializace přes IA/Oraxen id)
 ├─ CommandReward(command)      — spuštěno jako konzole, `%player%` placeholder
 ├─ ExperienceReward(amount)
 └─ PermissionReward(node, duration?) — volitelné, přes LuckPerms API (soft-depend)

PlayerQuestData (per hráč, cachováno v paměti, perzistováno do DB)
 ├─ playerId: UUID
 └─ states: Map<questId, QuestProgress>

QuestProgress
 ├─ status: NOT_ACCEPTED | IN_PROGRESS | COMPLETED | TURNED_IN
 ├─ objectiveProgress: int[] (index odpovídá pořadí objektivů v Quest)
 ├─ startedAt, completedAt: Instant
 └─ timesCompleted: int (pro repeatable + cooldown)
```

**Klíčové rozhodnutí:** *definice* questu (statická, sdílená) a *progres* hráče (dynamický, per-instance) jsou striktně oddělené třídy. Nikdy se nemutuje `Quest` za běhu kvůli hráči — jen `QuestProgress`. Díky tomu je `Quest` bezpečně sdílený/immutable a cache-friendly.

## 4. Typy objektivů — priorita pro survival/RPG

MVP (Phase 1): `KILL_ENTITY`, `BREAK_BLOCK`, `PLACE_BLOCK`, `COLLECT_ITEM`, `REACH_LOCATION`.
Rozšíření (Phase 2–3): `CRAFT_ITEM`, `FISH`, `INTERACT_BLOCK`, `TALK_TO_NPC`.
Custom (Phase 4): `CUSTOM` — SPI pro 3. strany (jiné pluginy zaregistrují vlastní `ObjectiveHandler`).

Pro RPG server je důležité, že `target` u `KILL_ENTITY` a `COLLECT_ITEM` **umí rozlišit vanilla vs. custom**:
- `KILL_ENTITY` → pokud `target` odpovídá registrovanému MythicMobs internal name, tracker naslouchá `MythicMobDeathEvent` místo `EntityDeathEvent`; jinak vanilla `EntityType`.
- `COLLECT_ITEM`/`CRAFT_ITEM` → pokud `target` je ve formátu `itemsadder:namespace:id` nebo `oraxen:id`, porovnává se přes příslušné API; jinak vanilla `Material`.

Toto se řeší v jedné třídě `ItemMatcher` / `EntityMatcher`, ne rozházené po listenerech — jediné místo pravdy pro "co je co".

## 5. Storage vrstva

```
storage/
 ├─ QuestDefinitionRepository (interface)
 │   └─ YamlQuestDefinitionRepository   — čte/píše quests/*.yml, hot-reload přes /questadmin reload
 ├─ PlayerDataRepository (interface)
 │   ├─ SqlitePlayerDataRepository       — výchozí, HikariCP + jeden soubor .db
 │   └─ MySqlPlayerDataRepository        — Phase 4, stejné SQL schéma (dialektové rozdíly minimální)
 └─ migrations/  (jednoduché verzované SQL skripty, `schema_version` tabulka)
```

- Progres se **cachuje v paměti** (`Map<UUID, PlayerQuestData>`) po dobu online session, async load při `PlayerJoinEvent` (pre-login/async), async save při `PlayerQuitEvent` a periodicky (dirty-flag + flush každých ~5 min), plus synchronní flush při `onDisable`.
- I na jednom serveru dává SQLite+HikariCP smysl (thread pool, žádné blokování hlavního vlákna) — bez HikariCP by SQLite snadno blokoval tick při vyšším I/O.
- Repository rozhraní zajišťuje, že přechod na MySQL (Phase 4, pokud časem přibude síť serverů) je jen o nové implementaci + config přepínači, žádný zásah do `QuestService`.

## 6. NPC integrace (Citizens + FancyNPCs)

Abstrakce nad oběma pluginy, protože mají různé API a různé komunity je preferují:

```
npc/
 ├─ NpcProvider (interface)          — isAvailable(), resolveNpc(NpcRef): boolean je-li NPC platný
 ├─ CitizensNpcProvider              — naslouchá Citizens NPCRightClickEvent
 ├─ FancyNpcsNpcProvider             — naslouchá FancyNpcs NpcInteractEvent
 └─ NpcProviderRegistry              — detekuje za běhu dostupné pluginy, vybere aktivní provider(y);
                                        obě knihovny mohou běžet zároveň, klidně na různých NPC
```

- `QuestGiverRef` v definici questu obsahuje `provider` (`CITIZENS`/`FANCYNPCS`) + `npcId` (Citizens `int` id nebo FancyNPCs string id). Admin toto propojení nastaví buď v YAML, nebo pohodlněji v GUI editoru příkazem `/questadmin npc link <questId>` následovaným klikem na NPC.
- Interakce s NPC otevírá GUI s nabídkou dostupných questů (přijmout / rozpracováno / odevzdat), ne rovnou start — NPC je "quest giver", ne trigger na jeden konkrétní quest, i když jednoduchá 1:1 vazba je taky podporovaná (menší servery to tak chtějí).
- **Bez Citizens/FancyNPCs plugin funguje beze změny** — questy jde přijímat přes `/quest` GUI a příkazy. NPC je čistě volitelná prezentační vrstva nad stejným `QuestService`.

## 7. Architektura / struktura balíčků

```
eu.purrtech.purrtechQuest
 ├─ PurrtechQuest.java                 — jen bootstrap: načtení configu, inicializace DI kontejneru (ruční, žádný framework), registrace listenerů/příkazů
 ├─ api/                               — VEŘEJNÉ API pro jiné pluginy
 │   ├─ PurrtechQuestAPI (service locator: PurrtechQuestAPI.get())
 │   ├─ event/  (QuestAcceptEvent, QuestObjectiveProgressEvent, QuestCompleteEvent, QuestTurnInEvent — Bukkit Events, cancellable kde dává smysl)
 │   └─ objective/ObjectiveHandler (SPI pro CUSTOM objektivy registrované 3. stranou)
 ├─ config/                            — PluginConfig, MessagesConfig, konfigurační DTO
 ├─ model/                             — Quest, QuestObjective (+ podtypy), QuestReward (+ podtypy), enumy
 ├─ player/                            — PlayerQuestData, PlayerQuestDataCache
 ├─ storage/                           — repository rozhraní + Yaml/Sqlite/MySql implementace, HikariCP setup, migrace
 ├─ service/
 │   ├─ QuestService                   — jádro: acceptQuest, abandonQuest, updateProgress, completeQuest, turnIn, checkPrerequisites
 │   └─ RewardService                  — vykonání odměn (deleguje na RewardExecutor per typ)
 ├─ tracking/                          — per-objective listenery + ItemMatcher/EntityMatcher (kap. 4)
 ├─ npc/                               — NpcProvider abstrakce (kap. 6)
 ├─ integration/                       — VaultHook, PlaceholderAPIHook, ItemsAdderHook, OraxenHook, MythicMobsHook — vše guard `isPluginPresent()`
 ├─ gui/                               — vlastní lehký inventory-GUI framework (žádná externí lib, ať nešahujeme na shading zbytečně) — QuestLogGui, QuestDetailGui, QuestEditorGui
 ├─ command/                           — QuestCommand, QuestAdminCommand (Brigadier)
 └─ util/                              — Scheduler wrapper (region-aware, viz kap. 10), text/MiniMessage helpery
```

Princip: **`service/` nezná Bukkit eventy ani GUI**, `tracking/`/`gui/`/`command/` jsou tenké adaptéry nad `QuestService`. Díky tomu jde `QuestService` testovat čistě v JUnit bez serveru (MockBukkit jen tam, kde je nevyhnutelné).

## 8. Tvorba questů

Dvě rovnocenné cesty nad stejným repository:

1. **YAML soubory** (`plugins/PurrtechQuest/quests/*.yml`) — pro power-usery, verzování v gitu, hromadné úpravy, případně budoucí sdílení quest-packů mezi servery.
2. **In-game GUI editor** (`/questadmin edit`) — postupný wizard (název → objektivy → odměny → prerekvizity → NPC link), ukládá do stejného YAML formátu. Nutné pro adminy, kteří nechtějí editovat soubory ručně.

`/questadmin reload` provede hot-reload definic bez restartu serveru; probíhající `QuestProgress` hráčů zůstává (progres je vázaný na `questId` + index objektivu, ne na objekt v paměti).

## 9. Plnění questů — tok událostí

1. Hráč přijme quest (GUI/příkaz/NPC) → `QuestService.acceptQuest()` → vytvoří `QuestProgress`, ověří prerekvizity a cooldown, vyhodí `QuestAcceptEvent`.
2. `tracking/` listenery (registrované **jen pro typy objektivů, které aktuálně existují v aktivních questech** — výkonová optimalizace, viz kap. 10) zachytí relevantní Bukkit event, zjistí zda se týká nějakého `IN_PROGRESS` questu hráče, zavolají `QuestService.updateProgress()`.
3. Po splnění všech objektivů → status `COMPLETED`, vyhodí se `QuestCompleteEvent`, hráč dostane notifikaci (title/actionbar/sound — konfigurovatelné).
4. Odevzdání (`turnIn`) — buď automatické (pokud quest nemá NPC a `autoTurnIn: true`), nebo ručně u NPC / v GUI → `RewardService` vyplatí odměny, vyhodí `QuestTurnInEvent`.

## 10. Výkon a škálovatelnost

- **Podmíněná registrace listenerů** — plugin při startu/reloadu spočítá, jaké `ObjectiveType` se reálně používají v aktivních questech, a zaregistruje jen odpovídající listenery. Server se 20 questy typu KILL_ENTITY nepotřebuje poslouchat `BlockPlaceEvent`.
- **Rychlý zamítací filtr** — v každém listeneru nejdřív O(1) lookup "má tenhle hráč vůbec nějaký IN_PROGRESS quest s tímhle typem objektivu", až pak dražší matching (custom item/entity lookup).
- **REACH_LOCATION** — neřešit přes `PlayerMoveEvent` na každý pohyb; throttlovat (kontrola jen při změně bloku, ne při pohybu hlavou) nebo periodickým async schedulerem co pár tiků kontrolovat vzdálenost jen u hráčů s aktivním location-objektivem.
- **DB I/O vždy async** (HikariCP thread pool), zápis do herního stavu hráče zpět na hlavní vlákno přes `Scheduler`.
- **Folia-friendly do budoucna** — i když cílíme na Paper, `util/Scheduler` je od začátku napsaný jako wrapper (global/region/entity scheduler), aby případný přechod na Folia nevyžadoval přepsání service vrstvy.

## 11. GUI a UX

- `/quest` → Quest Log GUI: seznam dostupných / rozpracovaných / dokončených questů, filtrování dle kategorie.
- Detail questu → popis, objektivy s progress barem (např. `Zabij zombíky: 7/15`), odměny, tlačítko Accept/Abandon/Track.
- "Track" quest → trvalý indikátor progresu v action baru nebo BossBar (konfigurovatelné), aktualizace při každé změně progresu.
- Notifikace: Title při dokončení objektivu/questu, zvuk, volitelně chat zpráva — vše přes `MessagesConfig` (CZ/EN).

## 12. Příkazy a oprávnění

| Příkaz | Oprávnění | Popis |
|---|---|---|
| `/quest`, `/quest log` | `purrtechquest.use` (default true) | otevře Quest Log GUI |
| `/quest accept <id>`, `/quest abandon <id>`, `/quest track <id>` | `purrtechquest.use` | |
| `/questadmin create/edit/delete` | `purrtechquest.admin` | GUI editor |
| `/questadmin give <hráč> <id>` | `purrtechquest.admin` | vynucené přiřazení questu |
| `/questadmin reset <hráč> <id>` | `purrtechquest.admin` | reset progresu (repeatable/testing) |
| `/questadmin reload` | `purrtechquest.admin` | hot-reload YAML definic |
| `/questadmin npc link <id>` | `purrtechquest.admin` | propojení questu s NPC |

## 13. Veřejné API pro 3. strany

Protože ekosystém (my-shop) naznačuje, že mohou vznikat další addon pluginy, `api/` balíček od začátku vystavuje:
- `PurrtechQuestAPI.get().getQuestService()` — read-only dotazy na progres hráče.
- Bukkit eventy (`QuestAcceptEvent`, `QuestObjectiveProgressEvent`, `QuestCompleteEvent`, `QuestTurnInEvent`) — jiné pluginy mohou reagovat (např. dát titul hráči, zapsat do statistik).
- `ObjectiveHandler` SPI — registrace vlastního typu objektivu (`CUSTOM`) jiným pluginem, např. "navštiv 5 měst" z RPG pluginu.

## 14. Testování

- **Unit testy** (JUnit 5): `QuestService` (accept/progress/complete/prerekvizity/cooldown), `ItemMatcher`/`EntityMatcher` matching logika, storage repository (in-memory SQLite pro testy).
- **MockBukkit**: tam, kde je nevyhnutelné simulovat Bukkit API (např. listener → service integrace).
- **Manuální QA checklist** přes `./gradlew runServer` (task už v projektu) — scénáře: accept → progress → complete → turn-in, reload za běhu, repeatable cooldown, NPC interakce s oběma providery.

## 15. Fázovaný plán (roadmap)

| Fáze | Obsah |
|---|---|
| **0 — Základ** | Config loader, `model/`, `storage/` rozhraní + SQLite/YAML implementace, `PurrtechQuestAPI` kostra |
| **1 — MVP** | 5 základních objektivů, `QuestService`, chat/příkazové ovládání (bez GUI), money+command odměny (Vault soft-depend), lokalizace CZ/EN |
| **2 — GUI** | Quest Log GUI, detail GUI, action bar/BossBar tracking, notifikace |
| **3 — Admin nástroje** | In-game quest editor GUI, quest chains/prerekvizity, repeatable + cooldown |
| **4 — NPC + RPG integrace** | Citizens + FancyNPCs providery, ItemsAdder/Oraxen matching, MythicMobs kill objektiv |
| **5 — Veřejné API** | Eventy, `ObjectiveHandler` SPI, dokumentace pro 3. strany |
| **6 — Polish** | PlaceholderAPI placeholdery, bStats, výkonové ladění, MySQL implementace (pro případ budoucí sítě serverů) |

## 16. Otevřená rizika / rozhodnutí k dořešení

- **GUI knihovna** — vlastní lehký framework vs. externí (např. triumph-gui). Doporučení: vlastní minimální framework (pár set řádků), abychom se vyhnuli shadingu další závislosti a měli plnou kontrolu nad UX; revidovat pokud editor GUI (Fáze 3) naroste do komplexity, kde se to nevyplatí.
- **Formát quest-packů** — pokud časem přibude marketplace přes my-shop (prodej hotových quest-packů), stojí za zvážení `.zip` s YAML + assety a import příkaz — zatím mimo scope.
- **MythicMobs/ItemsAdder/Oraxen verze API** — nutno ověřit aktuální API těchto pluginů při implementaci Fáze 4 (často mění balíčky mezi verzemi).

---

**Návrh dalšího kroku:** Pokud analýza sedí, navrhuji začít **Fází 0** — vytvořit balíčkovou strukturu, `model/` třídy, config loader a storage rozhraní se SQLite implementací, aby bylo na čem stavět zbytek. Dej vědět, jestli mám rovnou pokračovat implementací, nebo chceš k analýze něco upravit.
