package eu.purrtech.purrtechQuest.api;

import eu.purrtech.purrtechQuest.api.objective.ObjectiveHandlerRegistry;
import eu.purrtech.purrtechQuest.service.QuestService;
import eu.purrtech.purrtechQuest.storage.PlayerDataRepository;
import eu.purrtech.purrtechQuest.storage.QuestDefinitionRepository;

/**
 * Public entry point for other plugins. Grab it via {@link PurrtechQuestProvider#get()} after
 * PurrtechQuest has enabled — soft-depend on PurrtechQuest and only call this after confirming it's loaded
 * (e.g. from your own {@code onEnable}, guarded by {@code getServer().getPluginManager().isPluginEnabled("PurrtechQuest")}).
 * <p>
 * {@link #questService()} is the main entry point (accept/abandon/turn-in a quest, check progress, force
 * progress via {@link #objectiveHandlers()}'s CUSTOM objectives). {@link #questDefinitions()} and
 * {@link #playerData()} are the lower-level repositories, useful for e.g. a quest-pack import/export tool.
 * See {@code docs/API.md} for the full guide, including the public events fired in
 * {@code eu.purrtech.purrtechQuest.api.event}.
 */
public interface PurrtechQuestAPI {

    QuestService questService();

    ObjectiveHandlerRegistry objectiveHandlers();

    QuestDefinitionRepository questDefinitions();

    PlayerDataRepository playerData();
}
