package eu.purrtech.purrtechQuest.gui;

import eu.purrtech.purrtechQuest.config.MessagesConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/**
 * Lets a GUI screen ask the admin to type something in chat instead of building yet another inventory
 * widget for text/number entry — GUIs are bad at that, chat is good at it. Only one prompt can be pending
 * per player; the next chat message they send is captured (and never broadcast — see {@link #onChat}, this
 * is deliberately belt-and-braces) instead of being sent as normal chat. Typing {@code cancel} aborts
 * whatever prompted it.
 * <p>
 * Deliberately uses the legacy {@link AsyncPlayerChatEvent} rather than Paper's newer
 * {@code AsyncChatEvent} — cancelling it suppresses the message for the typing player too (not just other
 * viewers), which is what this needs. It's deprecated but Paper (a Spigot/Bukkit fork) still fires it
 * for compatibility, so it isn't at risk of quietly disappearing.
 * <p>
 * The inventory is closed for the duration of the prompt — trying to type in chat with a GUI open reads
 * the input as a chat message but leaves the (now stale) GUI sitting on screen, which is confusing. Both
 * {@code onInput} and {@code onCancel} are expected to reopen whatever screen makes sense next (usually by
 * constructing a fresh instance of that screen and calling {@code open(player)} on it).
 */
public final class ChatInputService implements Listener {

    private record PendingPrompt(Consumer<String> onInput, Runnable onCancel) {
    }

    private final JavaPlugin plugin;
    private final MessagesConfig messages;
    private final Map<UUID, PendingPrompt> pending = new ConcurrentHashMap<>();

    public ChatInputService(JavaPlugin plugin, MessagesConfig messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public void prompt(Player player, String promptMessageKey, Consumer<String> onInput, Runnable onCancel) {
        player.closeInventory();
        player.sendMessage(messages.render(promptMessageKey, player, Map.of()));
        player.sendMessage(messages.render("quest.editor-cancel-hint", player, Map.of()));
        pending.put(player.getUniqueId(), new PendingPrompt(onInput, onCancel));
    }

    public void promptInt(Player player, String promptMessageKey, IntConsumer onValid, Runnable onCancel) {
        prompt(player, promptMessageKey, raw -> {
            try {
                onValid.accept(Integer.parseInt(raw.trim()));
            } catch (NumberFormatException e) {
                player.sendMessage(messages.render("quest.editor-invalid-number", player, Map.of()));
                promptInt(player, promptMessageKey, onValid, onCancel);
            }
        }, onCancel);
    }

    public void promptDouble(Player player, String promptMessageKey, DoubleConsumer onValid, Runnable onCancel) {
        prompt(player, promptMessageKey, raw -> {
            try {
                onValid.accept(Double.parseDouble(raw.trim()));
            } catch (NumberFormatException e) {
                player.sendMessage(messages.render("quest.editor-invalid-number", player, Map.of()));
                promptDouble(player, promptMessageKey, onValid, onCancel);
            }
        }, onCancel);
    }

    /** Drops a pending prompt without running either callback — for flows that abandon it some other way. */
    public void cancel(Player player) {
        pending.remove(player.getUniqueId());
    }

    /**
     * {@code LOWEST} so this runs before any other plugin's chat listener gets a look at the message.
     * {@code setCancelled(true)} alone is enough to stop {@link AsyncPlayerChatEvent} from reaching anyone,
     * including the sender — unlike Paper's newer chat event, nothing here is rendered client-side ahead of
     * the server's broadcast, so a cancelled event means nobody ever sees the message.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        PendingPrompt prompt = pending.remove(playerId);
        if (prompt == null) {
            return;
        }
        event.setCancelled(true);
        event.getRecipients().clear();
        String text = event.getMessage().trim();
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (text.equalsIgnoreCase("cancel")) {
                player.sendMessage(messages.render("quest.editor-cancelled", player, Map.of()));
                prompt.onCancel().run();
                return;
            }
            prompt.onInput().accept(text);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
