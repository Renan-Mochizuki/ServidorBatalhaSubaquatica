import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientTeste2 {
  static String comandos1[] = {
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

  static String comandos2[] = {
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

  static String comandos3[] = {
      "cadastrar a",
      "cadastrar b",
      "desafiar a a b",
      "desafiar b b a",
      "mover a a 1 1 1",
      "mover b b 1 1 1",
      "atacar a a 2 0 1",
      "cadastrar c",
      "desafiar c c a",
      "mover c c 1 1 1",
      "mover a a 1 0 1",
      "mover a a 0 1 1",
      "mover b b 1 0 1",
      "mover b b 0 1 1",
      "atacar b b 3 0 1",
      "atacar a a 2 0 1",
      "cadastrar d",
      "desafiar d d c",
      "mover d d 1 1 1",
      "desafiar c c d",
      "mover c c 1 0 1",
      "mover c c 0 1 1",
      "mover d d 1 0 1",
      "cadastrar e",
      "cadastrar f",
      "mover a a 1 0 1",
      "mover b b 1 0 1",
      "mover c c 1 0 1",
      "mover d d 1 0 1",
      "mover a a 1 0 1",
      "mover b b 1 0 1",
      "mover c c 1 0 1",
      "mover d d 1 0 1",
      "mover a a 1 0 1",
      "mover b b 1 0 1",
      "mover c c 1 0 1",
      "mover d d 1 0 1",
      "mover a a 1 0 1",
      "mover b b 1 0 1",
      "mover c c 1 0 1",
      "mover d d 1 0 1",
      "cadastrar g",
      "desafiar g g e",
      "desafiar e e f",
      "desafiar f f e",
      "mover e e 1 1 1",
      "mover f f 1 1 1",
      "mover g g 1 1 1",
      "mover a a 1 1 1",
      "mover b b 1 1 1",
      "atacar a a 5 0",
      "atacar b b 5 0",
      "mover c c 1 1 1",
      "mover d d 1 1 1",
      "mover e e 1 1 1",
      "mover e e 1 1 1",
      "mover f f 1 1 1",
      "mover g g 1 1 1",
      "mover a a 1 1 1",
      "mover b b 1 1 1",
      "mover c c 1 1 1",
      "mover d d 1 1 1",
      "mover e e 1 1 1",
  };

  static String comandos[];

  public static void main(String[] args) throws Exception {
    int tempoComandos = 200;

    BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));

    Socket clientSocket = new Socket("localhost", 9876);
    DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
    BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    String escolha = inFromUser.readLine();

    switch (escolha) {
      case "a":
        comandos = comandos1;
        break;

      case "b":
        comandos = comandos2;
        break;
      case "c":
        comandos = comandos3;
        break;

      default:
        break;
    }
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

    // Removido o envio manual: este cliente de teste apenas envia comandos
    // programados

    // Thread que envia cada comando do array em intervalo fixo, sem aguardar
    // respostas
    Thread sender = new Thread(() -> {
      try {
        if (comandos == null) {
          System.err.println("Nenhum conjunto de comandos selecionado (digite a, b ou c). Encerrando.");
          return;
        }
        for (String cmd : comandos) {
          System.out.println("Enviando comando: " + cmd);
          synchronized (outToServer) {
            outToServer.writeBytes(cmd + "\n");
            outToServer.flush();
          }
          // Aguarda o intervalo antes do próximo comando
          try {
            Thread.sleep(tempoComandos);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      } catch (IOException e) {
        System.err.println("Erro ao enviar comando: " + e.getMessage());
      } finally {
        // Fecha o socket ao concluir/ocorrer erro
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
