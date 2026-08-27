# PurrtechQuest API

A guide for other plugins that want to react to quests, or add their own objective types. Everything here
lives under `eu.purrtech.purrtechQuest.api`.

## Depending on PurrtechQuest

Add it as a soft-depend in your `paper-plugin.yml` (or `plugin.yml`):

```yaml
softdepend:
  - PurrtechQuest
```

Then in your `onEnable`, only touch the API after confirming PurrtechQuest is actually loaded:

```java
if (getServer().getPluginManager().isPluginEnabled("PurrtechQuest")) {
    PurrtechQuestAPI api = PurrtechQuestProvider.get();
    // ...
}
```

`PurrtechQuestProvider.get()` throws `IllegalStateException` if called before PurrtechQuest has enabled —
the guard above (or listening for your own plugin's `onEnable`, which always runs after softdepends)
avoids that.

You'll need PurrtechQuest's jar on your compile classpath. It isn't published anywhere yet, so for now:
`compileOnly(files("path/to/PurrtechQuest.jar"))`, or build against a local Maven install.

## Threading

Everything below — `QuestService` methods, event handlers, `ObjectiveHandler.isValidTarget` — runs on the
main server thread and is expected to be called from it. None of it is thread-safe to call from an async
context.

## Getting the API

```java
PurrtechQuestAPI api = PurrtechQuestProvider.get();
QuestService quests = api.questService();
```

`QuestService` is the main surface:

| Method | What it does |
|---|---|
| `allQuests()` | Every loaded quest definition. |
| `quest(String id)` | Look up one quest by id. |
| `acceptQuest(Player, String questId)` | Start a quest for a player (subject to prerequisites/cooldown, fires `QuestAcceptEvent`). |
| `abandonQuest(Player, String questId)` | Cancel an in-progress quest. |
| `turnIn(Player, String questId)` | Turn in a completed quest and grant rewards (fires `QuestTurnInEvent`). |
| `updateProgress(Player, ObjectiveType, String target, int amount)` | Report progress toward any objective matching that type+target — this is how you drive `ObjectiveType.CUSTOM` objectives (see below). |
| `adminForceAccept` / `adminResetProgress` | Bypass prerequisites/cooldown entirely — not vetoable by `QuestAcceptEvent`, since it's meant to be an authoritative override. |

For read-only inspection of a specific player's progress, go through the same `QuestService` methods a
command would: `quests.acceptQuest(...)` etc. all return a result enum (`AcceptResult`, `AbandonResult`,
`TurnInResult`) you can switch on.

`api.questDefinitions()` and `api.playerData()` expose the lower-level YAML/SQLite repositories directly,
in case you're building something like a quest-pack import/export tool rather than reacting to gameplay.

## Events

All in `eu.purrtech.purrtechQuest.api.event`, all plain Bukkit `Event`s — register a `Listener` the normal
way. Fired synchronously on the main thread, in this order for a typical `autoTurnIn` quest:

```
QuestAcceptEvent → QuestObjectiveProgressEvent (× as many objectives progress) → QuestCompleteEvent → QuestTurnInEvent
```

| Event | Cancellable | Fired |
|---|---|---|
| `QuestAcceptEvent` | Yes | Right before a player's progress on a quest is created. Cancelling makes `acceptQuest` return `AcceptResult.CANCELLED` — nothing is written. **Not fired** for `adminForceAccept`. |
| `QuestObjectiveProgressEvent` | No | After a single objective's stored counter increases (already clamped to its target amount). Carries the objective index and the previous/new amount. |
| `QuestCompleteEvent` | No | The moment all of a quest's objectives are met. The objectives are already done — nothing left to veto. |
| `QuestTurnInEvent` | Yes | Right before rewards are granted (quest is `COMPLETED`, not yet `TURNED_IN`). Cancelling blocks the whole turn-in — `turnIn` returns `TurnInResult.CANCELLED`, no rewards granted, quest stays `COMPLETED` for a later attempt. |
| `QuestAbandonEvent` | No | After a player abandons an in-progress quest (progress already reset). |

Example — block turn-in unless the player is standing at a specific NPC:

```java
@EventHandler
public void onTurnIn(QuestTurnInEvent event) {
    if (!isNearRewardNpc(event.getPlayer())) {
        event.setCancelled(true);
        event.getPlayer().sendMessage("Go find the quest giver to turn this in!");
    }
}
```

## Custom objectives (`ObjectiveHandler` SPI)

`ObjectiveType.CUSTOM` exists for objectives your plugin defines and tracks itself — PurrtechQuest doesn't
know how to detect "visited 5 cities" or "reached prestige 3" on its own. Two pieces:

1. **Register a handler** so the in-game quest editor can offer your objective type and validate what an
   admin types for it:

   ```java
   public class VisitCityObjectiveHandler implements ObjectiveHandler {
       @Override public String id() { return "visit_city"; }
       @Override public String displayName() { return "Visit a City"; }
       @Override public boolean isValidTarget(String subTarget) {
           return CityRegistry.exists(subTarget); // e.g. "paris"
       }
   }
   ```

   ```java
   api.objectiveHandlers().register(new VisitCityObjectiveHandler());
   ```

   Once registered, an admin editing a quest sees "Visit a City" as a selectable objective type and can
   type e.g. `paris` as the target — validated live against your `isValidTarget`.

2. **Report progress yourself**, whenever your plugin's own logic detects the condition. The target string
   PurrtechQuest stores is always `"<handlerId>:<subTarget>"` — build it the same way when reporting:

   ```java
   // player just visited Paris
   PurrtechQuestProvider.get().questService()
       .updateProgress(player, ObjectiveType.CUSTOM, "visit_city:paris", 1);
   ```

   `updateProgress` finds every `IN_PROGRESS` quest of that player with a matching `CUSTOM` objective
   (case-insensitive target match) and applies the progress — same completion/turn-in flow as any built-in
   objective, including firing `QuestObjectiveProgressEvent`/`QuestCompleteEvent`.

Unregister with `api.objectiveHandlers().unregister(id)` in your `onDisable` — quests referencing an id
that's no longer registered still work for tracking (nothing PurrtechQuest-side depends on the handler
staying registered), they just won't be offered/re-editable in the GUI until it's back.

## Compatibility

There's no versioned/stable API contract yet (no releases have shipped). Treat this as an early integration
point that may still shift — pin against a specific PurrtechQuest build if you need stability.
