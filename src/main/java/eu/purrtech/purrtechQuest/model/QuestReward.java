package eu.purrtech.purrtechQuest.model;

/**
 * Something a player receives on turn-in. Kept as plain data here — actually granting it (Vault call,
 * building an {@code ItemStack}, dispatching a command) is {@code RewardService}'s job in a later phase.
 */
public sealed interface QuestReward {

    record Money(double amount) implements QuestReward {
        public Money {
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be positive, got " + amount);
            }
        }
    }

    /**
     * {@code customId}, when set, points at an ItemsAdder/Oraxen custom item instead of a vanilla material.
     */
    record Item(String material, int amount, String customId) implements QuestReward {
        public Item {
            if (material == null || material.isBlank()) {
                throw new IllegalArgumentException("material must not be blank");
            }
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be positive, got " + amount);
            }
        }
    }

    record Command(String command) implements QuestReward {
        public Command {
            if (command == null || command.isBlank()) {
                throw new IllegalArgumentException("command must not be blank");
            }
        }
    }

    record Experience(int amount) implements QuestReward {
        public Experience {
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be positive, got " + amount);
            }
        }
    }

    record Permission(String node, Long durationSeconds) implements QuestReward {
        public Permission {
            if (node == null || node.isBlank()) {
                throw new IllegalArgumentException("node must not be blank");
            }
        }
    }
}
