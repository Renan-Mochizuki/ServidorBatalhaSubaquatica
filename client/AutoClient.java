package client;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;

// Código feito pelo Agente Copilot
/**
 * Simple automated client that:
 * - connects to the server
 * - sends: CADASTRAR <name>
 * - waits for server response containing token:... and stores the token
 * - reads commands from the provided file (skipping the first two lines)
 * - for each command, sends: <command> <name> <token> <rest-of-command>
 * - sleeps the configured interval between commands
 *
 * Usage: java -cp classes client.AutoClient <name> <tempoMs> <commandsFile>
 */
public class AutoClient {
  public static void main(String[] args) throws Exception {
    if (args.length < 3) {
      System.err.println("Usage: client.AutoClient <name> <tempoMs> <commandsFile>");
      System.exit(1);
    }

    final String name = args[0];
    int tempo;
    try {
      tempo = Integer.parseInt(args[1]);
    } catch (NumberFormatException nfe) {
      System.err.println("Aviso: tempo invalido '" + args[1] + "', usando 200ms por padrao.");
      tempo = 200;
    }
    final Path commandsFile = Paths.get(args[2]);

    if (!Files.exists(commandsFile)) {
      System.err.println("Commands file not found: " + commandsFile);
      System.exit(1);
    }

    List<String> allLines = Files.readAllLines(commandsFile);
    if (allLines.size() < 2) {
      System.err.println("Commands file must contain at least two lines (names + tempo)");
      System.exit(1);
    }

    List<String> commands = new ArrayList<>();
    for (int i = 2; i < allLines.size(); i++) {
      String l = allLines.get(i);
      if (l == null)
        continue;
      l = l.trim();
      if (l.isEmpty() || l.startsWith("#"))
        continue;
      commands.add(l);
    }

    Socket socket = new Socket("localhost", 9876);
    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

    AtomicReference<String> tokenRef = new AtomicReference<>(null);
    AtomicBoolean running = new AtomicBoolean(true);

    Thread reader = new Thread(() -> {
      try {
        String line;
        while (running.get() && (line = in.readLine()) != null) {
          System.out.println("[" + name + "] FROM SERVER: " + line);
          // look for CADASTRAR success with token
          if (line.startsWith("CADASTRAR|201|") && line.contains("token:")) {
            int idx = line.indexOf("token:");
            if (idx >= 0) {
              String tk = line.substring(idx + "token:".length()).trim();
              if (!tk.isEmpty()) {
                tokenRef.compareAndSet(null, tk);
                System.out.println("[" + name + "] token received: " + tk);
              }
            }
          }
          // if server tells us we're disconnected, exit loop and shut down
          if (line.startsWith("DESCONECTADO")) {
            System.out.println("[" + name + "] received DESCONECTADO from server, closing client.");
            // signal main thread to exit
            running.set(false);
            break;
          }
        }
      } catch (IOException e) {
        if (running.get())
          System.err.println("[" + name + "] read error: " + e.getMessage());
      } finally {
        running.set(false);
      }
    }, "Reader-" + name);
    reader.setDaemon(true);
    reader.start();

    // send CADASTRAR
    synchronized (out) {
      out.writeBytes("CADASTRAR " + name + "\n");
      out.flush();
    }

    // wait up to 15 seconds for token
    long waitUntil = System.currentTimeMillis() + 15000L;
    while (tokenRef.get() == null && System.currentTimeMillis() < waitUntil && running.get()) {
      Thread.sleep(100);
    }

    String token = tokenRef.get();
    if (token == null) {
      System.err.println("[" + name + "] did not receive token within timeout, exiting.");
      running.set(false);
      socket.close();
      System.exit(2);
    }

    // send commands
    for (String cmd : commands) {
      if (!running.get())
        break;
      String[] parts = cmd.split("\\s+", 2);
      String first = parts.length > 0 ? parts[0] : cmd;
      String rest = parts.length > 1 ? parts[1] : "";
      String toSend = first + " " + name + " " + token + (rest.isEmpty() ? "" : " " + rest);
      synchronized (out) {
        out.writeBytes(toSend + "\n");
        out.flush();
      }
      System.out.println("[" + name + "] SENT: " + toSend);
      Thread.sleep(tempo);
    }

    // after sending all commands keep the client running so it can receive server
    // messages
    System.out.println("[" + name
        + "] finished sending commands - awaiting server messages. Will exit when server sends 'DESCONECTADO'.");
    // wait until reader sets running=false (on DESCONECTADO or error)
    while (running.get()) {
      try {
        Thread.sleep(200);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        break;
      }
    }

    // cleanup
    try {
      socket.close();
    } catch (IOException ignored) {
    }
    reader.join(2000);
  }
}
