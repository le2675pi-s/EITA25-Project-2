import java.io.*;
import java.nio.file.Files;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.*;
import java.util.Scanner;
import javax.net.ssl.*;

/*
 * This example shows how to set up a key manager to perform client
 * authentication.
 *
 * This program assumes that the client is not inside a firewall.
 * The application can be modified to connect to a server outside
 * the firewall by following SSLSocketClientWithTunneling.java.
 */

public class Client {
	private static final String CLIENTTRUSTSTORE_FILE = "resources/client/clienttruststore";
	private static final String CLIENTTRUSTSTORE_PASSWORD = "password";

	private static final String HELP_MESSAGE = "USAGE: java Client [host] [port]";
	private static final String COMMANDS_MESSAGE = "COMMANDS: HELP, LIST, CREATE, READ, WRITE, DELETE, QUIT";

	public static void main(String[] args) throws Exception {
		var host = Parser.parse(args, 0);
		var portResult = Parser.parse(args, 1);

		if (!host.success() || !portResult.success()) {
			Logger.Log(Logger.Level.PRIVATE, HELP_MESSAGE);
			System.exit(-1);
		}

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

		BufferedReader read = new BufferedReader(new InputStreamReader(System.in));

		for (;;) {
			System.out.print("Enter keystore path: ");
			String keystore = read.readLine();
			System.out.print("Enter keystore password: ");
			String password = read.readLine();

			try {
				SSLSocketFactory factory;
				try {
					SSLContext ctx = TLSConfig.create(keystore, password, CLIENTTRUSTSTORE_FILE, CLIENTTRUSTSTORE_PASSWORD);
					factory = ctx.getSocketFactory();
				} catch (
						KeyStoreException | 
						NoSuchAlgorithmException |
						CertificateException |
						UnrecoverableKeyException |
						IOException e) {
					Logger.Log(Logger.Level.PRIVATE, "Invalid keystore path or password");
					continue;
				}

				SSLSocket socket = (SSLSocket)factory.createSocket(host.value(), port);
				socket.startHandshake();

				String subject = TLSConfig.getSubject(socket);
				Logger.Log(Logger.Level.PRIVATE, "Secure connection established with server: ", subject);

				Logger.Log(Logger.Level.PRIVATE, COMMANDS_MESSAGE, "\n");

				PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				for (;;) {
					System.out.print(">");
					String msg = read.readLine();

					String[] parse = msg.split(" ", 2);
					var cmd = Parser.parse(parse, 0);

					if (!cmd.success()) {
						Logger.Log(Logger.Level.PRIVATE, "Invalid command format: ", msg);
						continue;
					}

					Command command;
					try {
						command = Command.valueOf(cmd.value());
					} catch (IllegalArgumentException e) {
						Logger.LogError(e, "Invalid command", cmd.value());
						out.println("Invalid command [" + COMMANDS_MESSAGE + "]");
						continue;
					}

					boolean quit = false;
					switch (command) {
						case QUIT:
							quit = true;
							break;
						case HELP:
							Logger.Log(Logger.Level.PRIVATE, COMMANDS_MESSAGE);
							break;
						default:
							break;
					}
					if (quit) {
						out.flush();
						break;
					}

					Logger.Log(Logger.Level.PRIVATE, "Sending command: ", command);
					out.println(msg);
					out.flush();
					Logger.Log(Logger.Level.PRIVATE, "Waiting for server response...");
					Logger.Log(Logger.Level.PRIVATE, "Server response: ", in.readLine(), "\n");
				}
				in.close();
				out.close();
				read.close();
				socket.close();
			} catch (IOException e) {
				Logger.LogError(e, "Error during client-server communication");
			}

			break;
		}
	}
}
