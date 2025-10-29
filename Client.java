package client;

import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Client {
  public static void main(String[] args) throws Exception {
    BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
    Socket clientSocket = new Socket("localhost", 9876);
    DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
    BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

    // Flag para coordenar encerramento entre threads
    AtomicBoolean running = new AtomicBoolean(true);

    // Thread que fica escutando mensagens do servidor a qualquer momento
    Thread serverReader = new Thread(() -> {
      try {
        String serverLine;
        while (running.get() && (serverLine = inFromServer.readLine()) != null) {
          System.out.println("\nFROM SERVER: " + serverLine);
          // Re-print prompt para usuário digitar novamente
          System.out.print("Enter a sentence: ");
        }
      } catch (IOException e) {
        if (running.get()) {
          System.err.println("Erro ao ler do servidor: " + e.getMessage());
        }
      } finally {
        running.set(false);
      }
    }, "Server-Reader");
    serverReader.setDaemon(true);
    serverReader.start();

    // Thread principal lê do console e envia ao servidor a qualquer momento
    try {
      String sentence;
      while (running.get() && (sentence = inFromUser.readLine()) != null) {
        if (sentence.equalsIgnoreCase("fim")) {
          // Sinaliza encerramento e fecha socket
          running.set(false);
          break;
        }
        // Sincroniza escrita por segurança se houver múltiplas threads escrevendo
        synchronized (outToServer) {
          outToServer.writeBytes(sentence + "\n");
          outToServer.flush();
        }
      }
    } catch (IOException e) {
      System.err.println("Erro na entrada do usuário: " + e.getMessage());
    } finally {
      running.set(false);
      try {
        clientSocket.close();
      } catch (IOException e) {
        // Ignorar
      }
      // Aguarda a thread de leitura encerrar brevemente
      try {
        serverReader.join(2000);
      } catch (InterruptedException e) {
        // Ignorar
      }
    }
  }
}