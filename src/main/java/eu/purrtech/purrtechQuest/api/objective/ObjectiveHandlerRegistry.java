package eu.purrtech.purrtechQuest.api.objective;

import java.util.Collection;
import java.util.Optional;

/**
 * Where 3rd-party {@link ObjectiveHandler}s live. Get one via
 * {@code PurrtechQuestAPI.get().objectiveHandlers()}.
 */
public interface ObjectiveHandlerRegistry {

    void register(ObjectiveHandler handler);

    void unregister(String id);

    Optional<ObjectiveHandler> get(String id);

    Collection<ObjectiveHandler> all();
}
