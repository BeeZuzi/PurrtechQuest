package eu.purrtech.purrtechQuest.gui;

import eu.purrtech.purrtechQuest.api.objective.ObjectiveHandlerRegistry;
import eu.purrtech.purrtechQuest.config.MenuLayoutConfig;
import eu.purrtech.purrtechQuest.config.MessagesConfig;
import eu.purrtech.purrtechQuest.npc.NpcLinkService;
import eu.purrtech.purrtechQuest.permission.QuestPermissionRegistrar;
import eu.purrtech.purrtechQuest.service.QuestService;
import eu.purrtech.purrtechQuest.storage.CategoryConfigRepository;
import eu.purrtech.purrtechQuest.storage.QuestDefinitionRepository;
import eu.purrtech.purrtechQuest.tracking.TrackerRegistrationManager;

/**
 * Dependencies shared by every screen of the quest editor wizard, bundled so navigating between screens
 * doesn't mean threading seven constructor parameters through each one just to pass them along unchanged.
 */
public record QuestEditorContext(MessagesConfig messages, ChatInputService chatInput,
                                  QuestService questService, QuestDefinitionRepository questRepository,
                                  ObjectiveHandlerRegistry objectiveHandlers,
                                  TrackerRegistrationManager trackerRegistrationManager,
                                  NpcLinkService npcLinkService,
                                  QuestPermissionRegistrar questPermissionRegistrar,
                                  CategoryConfigRepository categoryConfigRepository,
                                  MenuLayoutConfig menuLayouts) {
}
