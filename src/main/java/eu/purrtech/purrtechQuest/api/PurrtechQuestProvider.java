package eu.purrtech.purrtechQuest.api;

public final class PurrtechQuestProvider {

    private static volatile PurrtechQuestAPI instance;

    private PurrtechQuestProvider() {
    }

    public static PurrtechQuestAPI get() {
        PurrtechQuestAPI api = instance;
        if (api == null) {
            throw new IllegalStateException("PurrtechQuest API is not initialized yet");
        }
        return api;
    }

    public static void register(PurrtechQuestAPI api) {
        instance = api;
    }

    public static void unregister() {
        instance = null;
    }
}
