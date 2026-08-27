package eu.purrtech.purrtechQuest.storage;

import eu.purrtech.purrtechQuest.model.Quest;

import java.util.List;

/**
 * Where {@link Quest} definitions live. Today: YAML files under the plugin's data folder. The interface
 * exists so that a future quest-pack import/export format doesn't need to touch anything above this layer.
 */
public interface QuestDefinitionRepository {

    List<Quest> loadAll();

    void save(Quest quest);

    void delete(String questId);
}
