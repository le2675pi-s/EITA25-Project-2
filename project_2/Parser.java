public class Parser {
	public record Result<T>(boolean success, T value) {
		@Override
		public String toString() {
			return value.toString();
		}
	}

	public static Result<String> parse(String[] args, int index) {
		if (index < 0 || index >= args.length) {
			return new Result<>(false, null);
		}
		return new Result<>(true, args[index]);
	}
}
