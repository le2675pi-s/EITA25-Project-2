import java.io.*;
import java.net.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import javax.net.*;
import javax.net.ssl.*;

public class Server implements Runnable {
	private static final String JOURNAL_FILE = "resources/server/journals.txt";

	private static final String SERVERKEYSTORE_FILE = "resources/server/serverkeystore";
	private static final String SERVERTRUSTSTORE_FILE = "resources/server/servertruststore";

	private static final String SERVERKEYSTORE_PASSWORD = "password";
	private static final String SERVERTRUSTSTORE_PASSWORD = "password";

	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private ServerSocket serverSocket = null;
	private SSLSocket socket = null;
	private static int numConnectedClients = 0;

	public Server(ServerSocket ss) throws IOException {
		serverSocket = ss;
		newListener();
	}

	private void newListener() { (new Thread(this)).start(); } // calls run()

	@Override
	public void run() {
		try {
			Data data = connect();
			listen(data);
			disconnect();
		} catch (IOException e) {
			Logger.Log(Logger.Level.PRIVATE, "Client died: ", e.getMessage());
		}
	}

	private Data connect() throws IOException {
		socket = (SSLSocket)serverSocket.accept();
		newListener();

		String subject = TLSConfig.getSubject(socket);

		numConnectedClients++;
		Logger.Log( Logger.Level.PRIVATE, "Client connected: ", subject);
		Logger.Log( Logger.Level.PRIVATE, "Concurrent connection(s): ", numConnectedClients, "\n");

		return new Data(subject);
	}

	private void disconnect() throws IOException {
		socket.close();
		numConnectedClients--;
		Logger.Log( Logger.Level.PRIVATE, "Client disconnected");
		Logger.Log( Logger.Level.PRIVATE, "Concurrent connection(s): ", numConnectedClients, "\n");
	}

	private void listen(Data data) throws IOException {
		PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
		BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

		String msg = null;
		while ((msg = in.readLine()) != null) {
			String[] parse = msg.split(" ", 2);
			var cmd = Parser.parse(parse, 0);
			if (!cmd.success()) {
				Logger.Log(Logger.Level.PRIVATE, "Invalid command format: ", msg);
				out.println("Invalid command format [use <COMMAND> ...]");
				continue;
			}

			Command command;
			try {
				command = Command.valueOf(cmd.value());
			} catch (IllegalArgumentException e) {
				Logger.LogError(e, "Invalid command: ", cmd.value());
				out.println("Invalid command [use CREATE, READ, WRITE, DELETE, or QUIT]");
				continue;
			}

			if (command == Command.LIST) {
				try (BufferedReader reader = new BufferedReader(new FileReader(JOURNAL_FILE))) {
					StringBuilder builder = new StringBuilder();
					builder.append("Authorized read access journals:\t");
					reader.lines().forEach(line -> {
						Journal entry = new Journal(line);
						if (isAuthorized(entry, data, Command.READ)) {
							builder.append(entry.toString()).append("\t");
						}
					});
					out.println(builder.toString());
				} catch (IOException e) {
					Logger.LogError(e, "Error reading journal: ");
					out.println("LIST failed");
					continue;
				}
				continue;
			}
			
			var argsResult = Parser.parse(parse, 1);
			if (!argsResult.success()) {
				Logger.Log(Logger.Level.PRIVATE, "Invalid command format: ", msg);
				out.println("Invalid command format [use <COMMAND> <id>:...]");
				continue;
			}
			
			String[] args = argsResult.value().split(":");
			
			if (command == Command.CREATE) {
				var patient = Parser.parse(args, 0);
				var division = Parser.parse(args, 1);
				var nurse = Parser.parse(args, 2);
				var info = Parser.parse(args, 3);
				if (!patient.success() || !division.success() || !nurse.success() || !info.success()) {
					Logger.Log(Logger.Level.PRIVATE, "Invalid CREATE command format: ", msg);
					out.println("Invalid CREATE command format [use CREATE <patient>:<division>:<nurse>:<info>]");
					continue;
				}
				
				String id = String.valueOf(System.currentTimeMillis());
				Journal journal = new Journal(id, patient.value(), data.CN(), nurse.value(), division.value(), info.value(), LocalDateTime.now().format(TIMESTAMP_FORMAT));
				try {
					try (PrintWriter writer = new PrintWriter(new FileWriter(JOURNAL_FILE, true))) {
						writer.println(journal.id() + ":" + journal.patient() + ":" + journal.doctor() + ":" + journal.nurse() + ":" + journal.division() + ":" + journal.info() + ":" + journal.created());
					}
					Logger.Log(Logger.Level.AUDIT, "Journal created: ", journal, " by doctor: ", data.CN());
					out.println("Journal created with ID: " + journal.id() + " successfully");
				} catch (IOException e) {
					Logger.LogError(e, "Error writing journal: ");
					out.println("CREATE failed");
				}
				continue;
			}

			var id = Parser.parse(args, 0);
			if (!id.success()) {
				Logger.Log(Logger.Level.PRIVATE, "Invalid command format: ", msg);
				out.println("Invalid command format [use <COMMAND> <id>:...]");
				continue;
			}

			try {
				ArrayList<String> newLines = new ArrayList<>();
				try (BufferedReader reader = new BufferedReader(new FileReader(JOURNAL_FILE))) {
					String[] lines = reader.lines().toArray(String[]::new);
					boolean found = false;
					for (String line : lines) {
						Journal journal = new Journal(line);
	
						if (journal.id().equals(id.value())) {
							found = true;
							Logger.Log(Logger.Level.PRIVATE, "Matching journal entry found: ", journal);
	
							if (!isAuthorized(journal, data, command)) {
								out.println("UNAUTHORIZED");
								newLines.add(line);
								continue;
							}
	
							String newLine = null;
							switch (command) {
								case READ:
									Logger.Log(Logger.Level.AUDIT, "Journal read: ", journal, " by user: ", data.CN());
									out.println(journal.toString());
									newLine = line;
									break;
								case WRITE:
									var info = Parser.parse(args, 1);
									if (!info.success()) {
										Logger.Log(Logger.Level.PRIVATE, "Invalid WRITE command format: ", msg);
										out.println("Invalid WRITE command format [use WRITE <id>:<info>]");
										newLines.add(line);
										continue;
									}
									journal = new Journal(journal.id(), journal.patient(), journal.doctor(), journal.nurse(), journal.division(), journal.info() + info.value(), journal.created());
									newLine = journal.toString();
									Logger.Log(Logger.Level.AUDIT, "Journal updated: ", journal, " by user: ", data.CN());
									out.println("Journal updated successfully");
									break;
								case DELETE:
									Logger.Log(Logger.Level.AUDIT, "Journal deleted: ", journal, " by user: ", data.CN());
									out.println("Journal deleted successfully");
									break;
							}
							newLines.add(newLine);
						}
					}
					if (!found) {
						Logger.Log(Logger.Level.PRIVATE, "No matching journal entry found with id: ", id.value());
						out.println("No matching journal entry found");
						continue;
					}
				}
				try (PrintWriter writer = new PrintWriter(new FileWriter(JOURNAL_FILE))) {
					for (String newLine : newLines) {
						writer.println(newLine);
					}
				}
			} catch (IOException e) {
				Logger.LogError(e, "Error reading journal");
			}
			out.flush();
			Logger.Log(Logger.Level.PRIVATE, "Command processed: ", command, " with id: ", id.value());
		}
		in.close();
		out.close();
	}

	private boolean isAuthorized(Journal journal, Data data, Command command) {
		String CN = data.CN();
		String OU = data.OU();
		String O = data.O();
		return switch (command) {
			case READ -> journal.patient().equals(CN) ||
				O.equals("Nurse") && (journal.nurse().equals(CN) || journal.division().equals(OU)) ||
				O.equals("Doctor") && (journal.doctor().equals(CN) || journal.division().equals(OU)) ||
				O.equals("Gov");
			case WRITE -> O.equals("Nurse") && journal.nurse().equals(CN) ||
				O.equals("Doctor") && journal.doctor().equals(CN);
			case DELETE -> O.equals("Gov");
			default -> false;
		};
	}

	public static void main(String args[]) {
		Logger.Log(Logger.Level.PRIVATE, "Starting Server...");

		var portResult = Parser.parse(args, 0);
		int port;
		try {
			port = Integer.parseInt(portResult.value());
			if (port < 0 || port > 65535) {
				Logger.Log(Logger.Level.PRIVATE, "Invalid port number");
				System.exit(-1);
			}
		} catch (NumberFormatException e) {
			Logger.Log(Logger.Level.PRIVATE, "Invalid port number");
			System.exit(-1);
			return;
		}

		try {
			SSLContext ctx = TLSConfig.create(SERVERKEYSTORE_FILE, SERVERKEYSTORE_PASSWORD, SERVERTRUSTSTORE_FILE, SERVERTRUSTSTORE_PASSWORD);
			SSLServerSocketFactory ssf = ctx.getServerSocketFactory();
			ServerSocket ss = ssf.createServerSocket(port, 0, InetAddress.getByName(null));
			((SSLServerSocket)ss).setNeedClientAuth(true); // enables client authentication
			new Server(ss);
		} catch (Exception e) {
			Logger.LogError(e, "Unable to start Server");
		}
	}
}
