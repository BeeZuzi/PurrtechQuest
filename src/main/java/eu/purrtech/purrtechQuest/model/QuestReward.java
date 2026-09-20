package eu.purrtech.purrtechQuest.model;

/**
 * Something a player receives on turn-in. Kept as plain data here — actually granting it (Vault call,
 * building an {@code ItemStack}, dispatching a command) is {@code RewardService}'s job in a later phase.
 * <p>
 * Every reward carries an admin-chosen {@link #name()} — what {@code QuestDetailGui} shows the player for it
 * instead of a raw "500 coins"/"3x DIAMOND" line. The in-game editor requires one for every reward it
 * creates and lets it be renamed afterwards; it's {@code null} only for rewards saved before names existed
 * (or hand-written in a quest file without one), which the GUI falls back to describing automatically.
 */
public sealed interface QuestReward {

    /** The admin-chosen display name, or {@code null} if none was ever set. */
    String name();

    /** A copy of this reward with a different display name (blank clears it back to {@code null}). */
    QuestReward withName(String name);

    private static String normalizeName(String name) {
        return name == null || name.isBlank() ? null : name.trim();
    }

    record Money(double amount, String name) implements QuestReward {
        public Money {
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be positive, got " + amount);
            }
            name = normalizeName(name);
        }

        public Money(double amount) {
            this(amount, null);
        }

        @Override
        public QuestReward withName(String name) {
            return new Money(amount, name);
        }
    }

    /**
     * {@code customId}, when set, points at an ItemsAdder/Oraxen custom item instead of a vanilla material.
     */
    record Item(String material, int amount, String customId, String name) implements QuestReward {
        public Item {
            if (material == null || material.isBlank()) {
                throw new IllegalArgumentException("material must not be blank");
            }
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be positive, got " + amount);
            }
            name = normalizeName(name);
        }

        public Item(String material, int amount, String customId) {
            this(material, amount, customId, null);
        }

        @Override
        public QuestReward withName(String name) {
            return new Item(material, amount, customId, name);
        }
    }

    record Command(String command, String name) implements QuestReward {
        public Command {
            if (command == null || command.isBlank()) {
                throw new IllegalArgumentException("command must not be blank");
            }
            name = normalizeName(name);
        }

        public Command(String command) {
            this(command, null);
        }

        @Override
        public QuestReward withName(String name) {
            return new Command(command, name);
        }
    }

    record Experience(int amount, String name) implements QuestReward {
        public Experience {
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be positive, got " + amount);
            }
            name = normalizeName(name);
        }

        public Experience(int amount) {
            this(amount, null);
        }

        @Override
        public QuestReward withName(String name) {
            return new Experience(amount, name);
        }
    }

    record Permission(String node, Long durationSeconds, String name) implements QuestReward {
        public Permission {
            if (node == null || node.isBlank()) {
                throw new IllegalArgumentException("node must not be blank");
            }
            name = normalizeName(name);
        }

        public Permission(String node, Long durationSeconds) {
            this(node, durationSeconds, null);
        }

        @Override
        public QuestReward withName(String name) {
            return new Permission(node, durationSeconds, name);
        }
    }
}
