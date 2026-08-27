package eu.purrtech.purrtechQuest.api.objective;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ObjectiveHandlerRegistryImpl implements ObjectiveHandlerRegistry {

    private final Map<String, ObjectiveHandler> handlers = new ConcurrentHashMap<>();

    @Override
    public void register(ObjectiveHandler handler) {
        handlers.put(handler.id(), handler);
    }

    @Override
    public void unregister(String id) {
        handlers.remove(id);
    }

    @Override
    public Optional<ObjectiveHandler> get(String id) {
        return Optional.ofNullable(handlers.get(id));
    }

    @Override
    public Collection<ObjectiveHandler> all() {
        return List.copyOf(handlers.values());
    }
}
