import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientTeste {
  String comandos1[] = {
      "cadastrar a",
      "desafiar a a b",
      "mover a a 1 1 1",
      "mover a a 1 1 1",
      "mover a a 1 1 1",
      "mover a a 1 1 1",
      "mover a a 1 1 1",
      "mover a a 1 1 1",
      "mover a a 1 0 1",
      "mover a a 0 1 1",
      "mover a a 1 0 1",
      "mover a a 0 1 1",
      "mover a a 1 0 1",
      "mover a a 0 1 1",
      "mover a a 1 0 1",
      "mover a a 0 1 1",
      "mover a a 1 0 1",
      "mover a a 0 1 1",
      "mover a a 1 0 1",
      "mover a a 0 1 1",
      "mover a a 1 0 1",
      "mover a a 0 1 1",
      "mover a a 1 0 1",
      "mover a a 0 1 1",
      "mover a a 1 0 1",
      "mover a a 0 1 1",
      "atacar a a 15 0",
      "atacar a a 15 0",
      "atacar a a 15 0",
      "atacar a a 15 0",
      "atacar a a 15 0",
  };

  String comandos2[] = {
      "cadastrar b",
      "desafiar b b a",
      "mover b b 1 1 1",
      "mover b b 1 1 1",
      "mover b b 1 1 1",
      "mover b b 1 1 1",
      "mover b b 1 1 1",
      "mover b b 1 1 1",
      "mover b b 1 0 1",
      "mover b b 0 1 1",
      "mover b b 1 0 1",
      "mover b b 0 1 1",
      "mover b b 1 0 1",
      "mover b b 0 1 1",
      "mover b b 1 0 1",
      "mover b b 0 1 1",
      "mover b b 1 0 1",
      "mover b b 0 1 1",
      "mover b b 1 0 1",
      "mover b b 0 1 1",
      "mover b b 1 0 1",
      "mover b b 0 1 1",
      "mover b b 1 0 1",
      "mover b b 0 1 1",
      "mover b b 1 0 1",
      "mover b b 0 1 1",
  };

  public static void main(String[] args) throws Exception {
    int tempoComandos = 1000;

    // Instância para acessar o array 'comandos' não-estático
    ClientTeste self = new ClientTeste();

    // Leitura do console para enviar comandos manuais
    BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));

    Socket clientSocket = new Socket("localhost", 9876);
    DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
    BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    String escolha = inFromUser.readLine();
    String comandos[] = escolha.equals("a") ? self.comandos1 : self.comandos2;
    // Flag para coordenar encerramento entre threads
    AtomicBoolean running = new AtomicBoolean(true);

    // Thread que fica escutando mensagens do servidor a qualquer momento
    Thread serverReader = new Thread(() -> {
      try {
        String serverLine;
        while (running.get() && (serverLine = inFromServer.readLine()) != null) {
          System.out.println("\nFROM SERVER: " + serverLine);
          // Reexibe o prompt para a digitação manual
          System.out.print("> ");
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

    // Thread para leitura do console (entrada manual do usuário)
    Thread consoleSender = new Thread(() -> {
      try {
        System.out.print("> ");
        String sentence;
        while (running.get() && (sentence = inFromUser.readLine()) != null) {
          if (sentence.equalsIgnoreCase("fim")) {
            running.set(false);
            break;
          }
          synchronized (outToServer) {
            outToServer.writeBytes(sentence + "\n");
            outToServer.flush();
          }
          System.out.print("> ");
        }
      } catch (IOException e) {
        if (running.get()) {
          System.err.println("Erro na entrada do usuário: " + e.getMessage());
        }
      } finally {
        running.set(false);
        try {
          clientSocket.close();
        } catch (IOException e) {
          // Ignorar
        }
      }
    }, "Console-Sender");
    // Deixa como daemon para não travar encerramento se usuário não digitar mais
    consoleSender.setDaemon(true);
    consoleSender.start();

    // Thread que envia cada comando do array a cada 5 segundos
    Thread sender = new Thread(() -> {
      try {
        for (String cmd : comandos) {
          if (!running.get())
            break;
          System.out.println("Enviando comando: " + cmd);
          synchronized (outToServer) {
            outToServer.writeBytes(cmd + "\n");
            outToServer.flush();
          }
          // Aguarda 5 segundos antes do próximo comando
          try {
            Thread.sleep(tempoComandos);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      } catch (IOException e) {
        if (running.get()) {
          System.err.println("Erro ao enviar comando: " + e.getMessage());
        }
      } finally {
        // Finaliza execução após enviar todos os comandos
        running.set(false);
        try {
          clientSocket.close();
        } catch (IOException e) {
          // Ignorar
        }
      }
    }, "Command-Sender");
    sender.start();

    // Aguarda o término do envio dos comandos
    try {
      sender.join();
      serverReader.join(2000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
