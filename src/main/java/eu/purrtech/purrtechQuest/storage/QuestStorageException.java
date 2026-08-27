package eu.purrtech.purrtechQuest.storage;

public class QuestStorageException extends RuntimeException {

    public QuestStorageException(String message) {
        super(message);
    }

    public QuestStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
