
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
	private static final String AUDIT_LOG_FILE = "resources/audit.log";
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	public enum Level {
		PRIVATE, AUDIT,
	}

	public static void Log(Level level, Object... message) {
		StringBuilder builder = new StringBuilder();
		for (Object obj : message) {
			builder.append(obj.toString()).append(" ");
		}
		String msg = builder.toString();
		switch (level) {
			case PRIVATE:
				System.out.println(msg);
				break;
			case AUDIT:
				String auditMsg = "[" + LocalDateTime.now().format(TIMESTAMP_FORMAT) + "] " + msg;
				try (PrintWriter out = new PrintWriter(new FileOutputStream(AUDIT_LOG_FILE, true))) {
					out.println(auditMsg);
				} catch (IOException e) {
					System.err.println("Failed to write to audit log: " + e.getMessage());
				}
		}
	}

	public static void LogError(Exception e, Object... message) {
		StringBuilder builder = new StringBuilder();
		for (Object obj : message) {
			builder.append(obj.toString()).append(" ");
		}
		builder.append(": ").append(e.getMessage());
		String msg = builder.toString();
		System.out.println(msg);
		// e.printStackTrace();
	}
}
