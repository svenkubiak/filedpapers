package utils;

public class Result {
    public sealed interface Of permits Success, Failure {}

    public record Success(Object value) implements Of {
        public <T> T get(Class<T> type) {
            return type.cast(value);
        }

        public static Success of(Object value) {
            return new Success(value);
        }
        public static Success empty() {
            return new Success(null);
        }
    }

    public record Failure(Error error) implements Of {
        public static Failure user(String message) {
            return new Failure(new UserError(message));
        }
        public static Failure server(String message) {
            return new Failure(new ServerError(message));
        }
    }

    public sealed interface Error permits UserError, ServerError {
        String message();
    }

    public record UserError(String message) implements Error {}
    public record ServerError(String message) implements Error {}
}

