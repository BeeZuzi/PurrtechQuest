package eu.purrtech.purrtechQuest.config;

/** Only read/meaningful when {@code storage.type} is {@code MYSQL}. */
public record MySqlSettings(String host, int port, String database, String username, String password,
                             int poolSize, boolean useSsl) {
}
