package eu.purrtech.purrtechQuest.api;

import eu.purrtech.purrtechQuest.api.objective.ObjectiveHandlerRegistry;
import eu.purrtech.purrtechQuest.service.QuestService;
import eu.purrtech.purrtechQuest.storage.PlayerDataRepository;
import eu.purrtech.purrtechQuest.storage.QuestDefinitionRepository;

public final class PurrtechQuestApiImpl implements PurrtechQuestAPI {

    private final QuestService questService;
    private final ObjectiveHandlerRegistry objectiveHandlers;
    private final QuestDefinitionRepository questDefinitions;
    private final PlayerDataRepository playerData;

    public PurrtechQuestApiImpl(QuestService questService, ObjectiveHandlerRegistry objectiveHandlers,
                                 QuestDefinitionRepository questDefinitions, PlayerDataRepository playerData) {
        this.questService = questService;
        this.objectiveHandlers = objectiveHandlers;
        this.questDefinitions = questDefinitions;
        this.playerData = playerData;
    }

    @Override
    public QuestService questService() {
        return questService;
    }

    @Override
    public ObjectiveHandlerRegistry objectiveHandlers() {
        return objectiveHandlers;
    }

    @Override
    public QuestDefinitionRepository questDefinitions() {
        return questDefinitions;
    }

    @Override
    public PlayerDataRepository playerData() {
        return playerData;
    }
}
