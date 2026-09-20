package eu.purrtech.purrtechQuest.storage;

import eu.purrtech.purrtechQuest.model.NpcProviderType;
import eu.purrtech.purrtechQuest.model.ObjectiveType;
import eu.purrtech.purrtechQuest.model.Quest;
import eu.purrtech.purrtechQuest.model.QuestGiverRef;
import eu.purrtech.purrtechQuest.model.QuestObjective;
import eu.purrtech.purrtechQuest.model.QuestReward;
import eu.purrtech.purrtechQuest.model.QuestRewardTier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts between {@link Quest} and its on-disk YAML shape. Kept separate from
 * {@link YamlQuestDefinitionRepository} so the file-I/O and the (de)serialization logic can change independently.
 */
final class QuestYamlMapper {

    private QuestYamlMapper() {
    }

    static Quest fromYaml(String id, YamlConfiguration config) {
        String displayName = config.getString("display-name", id);
        String description = config.getString("description", "");
        String category = config.getString("category", "default");
        boolean repeatable = config.getBoolean("repeatable", false);
        long cooldownSeconds = config.getLong("cooldown-seconds", 0L);
        boolean autoStart = config.getBoolean("auto-start", false);
        boolean autoTurnIn = config.getBoolean("auto-turn-in", false);
        String requiredPermission = config.getString("required-permission");
        Integer sortOrder = config.isSet("sort-order") ? config.getInt("sort-order") : null;
        List<String> requiredQuests = config.getStringList("required-quests");

        List<QuestObjective> objectives = new ArrayList<>();
        for (Map<?, ?> raw : config.getMapList("objectives")) {
            objectives.add(objectiveFromMap(raw));
        }

        List<QuestReward> rewards = new ArrayList<>();
        for (Map<?, ?> raw : config.getMapList("rewards")) {
            rewards.add(rewardFromMap(raw));
        }

        List<QuestRewardTier> rewardTiers = new ArrayList<>();
        for (Map<?, ?> raw : config.getMapList("reward-tiers")) {
            rewardTiers.add(rewardTierFromMap(raw));
        }

        QuestGiverRef questGiver = null;
        ConfigurationSection giverSection = config.getConfigurationSection("quest-giver");
        if (giverSection != null) {
            String npcId = giverSection.getString("npc-id");
            if (npcId != null && !npcId.isBlank()) {
                NpcProviderType provider = NpcProviderType.valueOf(
                        giverSection.getString("provider", "CITIZENS").toUpperCase());
                questGiver = new QuestGiverRef(provider, npcId);
            }
        }

        return new Quest(id, displayName, description, category, objectives, rewards, rewardTiers,
                requiredQuests, repeatable, cooldownSeconds, autoStart, autoTurnIn, questGiver, requiredPermission, sortOrder);
    }

    private static QuestObjective objectiveFromMap(Map<?, ?> raw) {
        Object rawType = raw.get("type");
        Object rawTarget = raw.get("target");
        if (rawType == null || rawTarget == null) {
            throw new QuestStorageException("Objective entry is missing 'type' or 'target': " + raw);
        }
        ObjectiveType type = ObjectiveType.valueOf(String.valueOf(rawType).toUpperCase());
        String target = String.valueOf(rawTarget);
        int amount = raw.get("amount") instanceof Number n ? n.intValue() : 1;

        Map<String, String> meta = new LinkedHashMap<>();
        if (raw.get("meta") instanceof Map<?, ?> metaMap) {
            metaMap.forEach((k, v) -> meta.put(String.valueOf(k), String.valueOf(v)));
        }
        String choiceGroup = raw.get("choice-group") == null ? null : String.valueOf(raw.get("choice-group"));
        String label = raw.get("label") == null ? null : String.valueOf(raw.get("label"));
        return new QuestObjective(type, target, amount, meta, choiceGroup, label);
    }

    private static QuestReward rewardFromMap(Map<?, ?> raw) {
        Object rawType = raw.get("type");
        if (rawType == null) {
            throw new QuestStorageException("Reward entry is missing 'type': " + raw);
        }
        String rewardType = String.valueOf(rawType).toUpperCase();
        String name = raw.get("name") == null ? null : String.valueOf(raw.get("name"));
        return switch (rewardType) {
            case "MONEY" -> new QuestReward.Money(((Number) raw.get("amount")).doubleValue(), name);
            case "ITEM" -> new QuestReward.Item(
                    String.valueOf(raw.get("material")),
                    raw.get("amount") instanceof Number n ? n.intValue() : 1,
                    raw.get("custom-id") == null ? null : String.valueOf(raw.get("custom-id")),
                    name);
            case "COMMAND" -> new QuestReward.Command(String.valueOf(raw.get("command")), name);
            case "EXPERIENCE" -> new QuestReward.Experience(((Number) raw.get("amount")).intValue(), name);
            case "PERMISSION" -> new QuestReward.Permission(
                    String.valueOf(raw.get("node")),
                    raw.get("duration-seconds") instanceof Number n ? n.longValue() : null,
                    name);
            default -> throw new QuestStorageException("Unknown reward type: " + rewardType);
        };
    }

    private static QuestRewardTier rewardTierFromMap(Map<?, ?> raw) {
        Object rawPermission = raw.get("permission");
        if (rawPermission == null) {
            throw new QuestStorageException("Reward tier entry is missing 'permission': " + raw);
        }
        String displayName = raw.get("display-name") == null ? null : String.valueOf(raw.get("display-name"));
        List<QuestReward> rewards = new ArrayList<>();
        if (raw.get("rewards") instanceof List<?> rawRewards) {
            for (Object rawReward : rawRewards) {
                if (rawReward instanceof Map<?, ?> rewardMap) {
                    rewards.add(rewardFromMap(rewardMap));
                }
            }
        }
        return new QuestRewardTier(String.valueOf(rawPermission), displayName, rewards);
    }

    static void toYaml(Quest quest, YamlConfiguration config) {
        config.set("display-name", quest.displayName());
        config.set("description", quest.description());
        config.set("category", quest.category());
        config.set("repeatable", quest.repeatable());
        config.set("cooldown-seconds", quest.cooldownSeconds());
        config.set("auto-start", quest.autoStart());
        config.set("auto-turn-in", quest.autoTurnIn());
        config.set("required-permission", quest.requiredPermission());
        config.set("sort-order", quest.sortOrder());
        config.set("required-quests", quest.requiredQuests());

        List<Map<String, Object>> objectives = new ArrayList<>();
        for (QuestObjective objective : quest.objectives()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("type", objective.type().name());
            map.put("target", objective.target());
            map.put("amount", objective.amount());
            map.put("label", objective.label());
            if (!objective.meta().isEmpty()) {
                map.put("meta", objective.meta());
            }
            if (objective.choiceGroup() != null) {
                map.put("choice-group", objective.choiceGroup());
            }
            objectives.add(map);
        }
        config.set("objectives", objectives);

        List<Map<String, Object>> rewards = new ArrayList<>();
        for (QuestReward reward : quest.rewards()) {
            rewards.add(rewardToMap(reward));
        }
        config.set("rewards", rewards);

        List<Map<String, Object>> rewardTiers = new ArrayList<>();
        for (QuestRewardTier tier : quest.rewardTiers()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("permission", tier.permission());
            map.put("display-name", tier.displayName());
            List<Map<String, Object>> tierRewards = new ArrayList<>();
            for (QuestReward reward : tier.rewards()) {
                tierRewards.add(rewardToMap(reward));
            }
            map.put("rewards", tierRewards);
            rewardTiers.add(map);
        }
        config.set("reward-tiers", rewardTiers);

        if (quest.questGiver() != null) {
            config.set("quest-giver.provider", quest.questGiver().provider().name());
            config.set("quest-giver.npc-id", quest.questGiver().npcId());
        } else {
            config.set("quest-giver", null);
        }
    }

    private static Map<String, Object> rewardToMap(QuestReward reward) {
        Map<String, Object> map = new LinkedHashMap<>();
        switch (reward) {
            case QuestReward.Money money -> {
                map.put("type", "MONEY");
                map.put("amount", money.amount());
            }
            case QuestReward.Item item -> {
                map.put("type", "ITEM");
                map.put("material", item.material());
                map.put("amount", item.amount());
                if (item.customId() != null) {
                    map.put("custom-id", item.customId());
                }
            }
            case QuestReward.Command command -> {
                map.put("type", "COMMAND");
                map.put("command", command.command());
            }
            case QuestReward.Experience experience -> {
                map.put("type", "EXPERIENCE");
                map.put("amount", experience.amount());
            }
            case QuestReward.Permission permission -> {
                map.put("type", "PERMISSION");
                map.put("node", permission.node());
                if (permission.durationSeconds() != null) {
                    map.put("duration-seconds", permission.durationSeconds());
                }
            }
        }
        if (reward.name() != null) {
            map.put("name", reward.name());
        }
        return map;
    }
}
