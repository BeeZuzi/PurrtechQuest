# PurrtechQuest

A Paper (1.21.11+) quest plugin for Minecraft servers. Admins build quests entirely in-game through a GUI
editor — kill/gather/craft/fish/location/NPC-talk/boss-fight/placeholder/money objectives, item/money/
command/experience/permission rewards, rank-tiered reward buckets, choice-group alternatives, prerequisites,
cooldowns, auto-start, auto-turn-in, and a permission gate — with progress tracked per player and persisted
to SQLite (default) or MySQL/MariaDB.

Every optional integration below is genuinely optional: PurrtechQuest works standalone and only hooks into a
plugin once it detects that plugin is actually installed.

## Installation

1. Drop the jar into `plugins/` and start the server once so it generates its data folder.
2. Edit `plugins/PurrtechQuest/config.yml` if the defaults don't fit (storage backend, default locale,
   whether uncategorized quests get their own "Others" category, etc. — see
   [Configuration](#configuration) below).
3. `/questadmin create <id>` opens the in-game quest editor to build your first quest, or hand-edit the
   sample quest under `plugins/PurrtechQuest/quests/` and `/questadmin reload`.

No other setup is required. Everything below is optional.

## Player commands (`/quest`, permission `purrtechquest.use`, default: everyone)

| Command | What it does |
| --- | --- |
| `/quest` or `/quest gui` | Opens the GUI: one button per quest category, plus (unless `categories.others-as-category` is on) an icon for each uncategorized quest. With no categories at all, this is just a flat quest list. |
| `/quest list` | Chat-based list of every quest and its status. |
| `/quest info <quest>` | Chat-based detail view of one quest. |
| `/quest accept <quest>` | Accepts a quest. |
| `/quest abandon <quest>` | Abandons an in-progress quest. |
| `/quest turnin <quest>` | Turns in a completed quest. |
| `/quest track <quest>` / `/quest untrack` | Shows live objective progress in the action bar or boss bar (configurable). |

## Admin commands (`/questadmin`, permission `purrtechquest.admin`, default: op)

| Command | What it does |
| --- | --- |
| `/questadmin create <id>` | Opens the GUI editor to build a new quest. |
| `/questadmin edit <quest>` | Opens the GUI editor on an existing quest. |
| `/questadmin delete <quest>` | Deletes a quest definition. |
| `/questadmin reload` | Reloads quest definitions from disk. |
| `/questadmin give <player> <quest>` | Force-accepts a quest for a player, bypassing prerequisites/cooldown. |
| `/questadmin reset <player> <quest>` | Wipes a player's progress on a quest. |
| `/questadmin npclink <quest>` / `npcunlink <quest>` | Links/unlinks a quest to a Citizens or FancyNpcs NPC (right-click the NPC in-world to confirm). |
| `/questadmin category <name>` | Opens the editor for a quest category: description, which parts of its display are shown, which slot its button sits on, and which item represents it. A category exists the moment any quest uses its name — this just configures how it looks. |

## Configuration

`config.yml` covers:

- **`storage`** — `SQLITE` (default, single server) or `MYSQL` (for a network of servers sharing player
  progress) plus connection settings.
- **`default-locale`** — which `lang/<code>.yml` a player with no matching locale falls back to. Defaults to
  `en`. This is *not* a hard limit on what languages you can offer — see [Localization](#localization).
- **`quests-directory`** — where quest YAML files live, relative to the plugin's data folder.
- **`tracking.display`** — `ACTION_BAR` (default) or `BOSS_BAR` for `/quest track`.
- **`categories.others-as-category`** — quests with no category normally show as individual icons directly
  in `/quest gui`, alongside your named categories' buttons; set this to `true` to instead group them behind
  their own "Others" category button, configured the same way as any category you create:
  `/questadmin category default`.
- **`debug`** — extra logging.

## Localization

PurrtechQuest ships `lang/cs.yml` and `lang/en.yml`. Every player sees messages in their own client language
automatically if a matching file exists, `default-locale` otherwise — this needs no server restart or admin
action, it just works per player.

To add another language, drop a `lang/<code>.yml` into the plugin's data folder (e.g. `lang/de.yml`) using
one of the bundled files as a template — it's picked up automatically on the next start, no plugin update or
code change required. The two bundled locales additionally self-heal: if a plugin update adds a new message
key or changes what an existing one expects, your on-disk `cs.yml`/`en.yml` gets the missing keys filled in
automatically (any key you've already customized is left untouched). A locale you add yourself doesn't have
a bundled file to self-heal against, so keep an eye on the changelog for new keys when you update.

## Menu layout

PurrtechQuest ships `menus.yml`, controlling the size and button placement of every GUI screen (the quest
log, the quest detail view, the admin editor and all of its sub-screens). Each menu is listed by id with a
`size` (a multiple of 9, up to 54) and a `slots` map from button id to slot index, e.g.:

```yaml
quest-category:
  size: 27
  slots:
    close: 22
```

An invalid or missing `size`/slot (not a multiple of 9, out of range, or an id `menus.yml` doesn't know
about) silently falls back to the plugin's built-in default rather than breaking the menu — nothing you can
put in this file will crash the GUI. Two buttons landing on the same slot isn't rejected the same way (there
are legitimate reasons to want that), but is logged as a warning on startup so it's not a silent surprise.
Menu *titles* aren't set here — like everything else player-facing, they live in `lang/<locale>.yml`
(the `*-title` keys), so a technician who wants to rename a menu edits that file instead.

The category buttons in `/quest gui` aren't fixed slots in this file, since which categories exist depends
on your quests — each one's placement and icon are configured per category instead, via
`/questadmin category <name>` (see [Quest editor basics](#quest-editor-basics)).

`menus.yml` self-heals the same way `lang/*.yml` does: a plugin update that adds a new menu or button never
overwrites a value you've already customized, only fills in what's missing.

## Quest editor basics

Everything about a quest — name, description, category, sort order, objectives, rewards, prerequisites,
repeatability/cooldown, auto-start, auto-turn-in, an optional NPC link, an optional required permission — is
editable through the `/questadmin create`/`edit` GUI. Objectives and rewards are added through their own
sub-screens reachable from the main editor; nothing is written to disk until you hit Save.

A quest's **category** is just whatever text you type in the editor's Category field — typing a name nobody
has used before creates it, no separate "create category" step needed. Once at least one quest uses a
category, it gets its own button in `/quest gui`; `/questadmin category <name>` then lets you configure that
button's description, which of the three lore lines it shows, which slot it sits on (if another category
already occupies that slot, the two swap rather than colliding), and its icon (a vanilla material, an
ItemsAdder/Oraxen custom item id, or whatever's currently in your hand). A quest left with no category falls
into `default`, which behaves like any other category — configurable the same way via
`/questadmin category default` — except it's off by default (see `categories.others-as-category` under
[Configuration](#configuration)); until turned on, those quests just show as their own icons directly in the
menu instead of behind a button.

Quest files under `plugins/PurrtechQuest/quests/*.yml` are plain, human-editable YAML in the same format the
editor writes, so hand-editing (or scripting quest generation) works too if you prefer that to the GUI.

## Optional integrations

None of these need to be installed. PurrtechQuest detects each at startup and only uses it if present.

| Plugin | What it enables |
| --- | --- |
| [Vault](https://www.spigotmc.org/resources/vault.34315/) + any economy plugin | Money rewards, `SPEND_MONEY`/`EARN_MONEY` objectives. |
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | `PLACEHOLDER_CHECK` objectives (compare any placeholder against a value) and `%purrtechquest_...%` placeholders for other plugins to consume. |
| [Citizens](https://www.spigotmc.org/resources/citizens.13811/) or [FancyNpcs](https://www.spigotmc.org/resources/fancynpcs.108219/) | NPC quest givers and `TALK_TO_NPC` objectives. |
| [MythicMobs](https://www.spigotmc.org/resources/mythicmobs.5702/) | `DEFEAT_BOSS` objectives against custom mobs. |
| [ExcellentShop](https://www.spigotmc.org/resources/excellentshop.109123/) | `SPEND_MONEY` progress reported from real NPC-shop purchases. |
| [ItemsAdder](https://www.spigotmc.org/resources/itemsadder.73355/) / [Oraxen](https://www.spigotmc.org/resources/oraxen.72448/) | Custom items as objective targets/rewards. |

## Permissions

| Node | Default | Meaning |
| --- | --- | --- |
| `purrtechquest.use` | everyone | Use `/quest`. |
| `purrtechquest.admin` | op | Use `/questadmin`. |
| `purrtechquest.reward` | everyone | Receive a quest's *base* rewards on turn-in. Revoke to cut a player off from rewards entirely without touching quest progress. |

Two more kinds of permission are created dynamically as you configure quests — they aren't in the list above
because they don't exist until you define them:

- **Reward tiers**: naming a tier `king` in the editor creates `purrtechquest.reward.king`, checked on top of
  the base `purrtechquest.reward` node, so different ranks can get different bonus rewards.
- **Quest gating**: setting a quest's "required permission" field to any node you choose restricts who can
  even accept that quest.

Both are registered as real, discoverable Bukkit permissions on save/reload (so they show up in a permission
plugin's editor/autocomplete), not just ad-hoc strings.

## Building from source

```bash
./gradlew build
```

Requires Java 21. Tests run against real SQLite (no live Bukkit server needed for the parts that don't touch
Bukkit-only APIs like live registries — those are covered by manual in-game testing instead).
