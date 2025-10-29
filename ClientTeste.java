import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientTeste {
  static String comandos[];

  public static void main(String[] args) throws Exception {
    int tmpTempoComandos = 200;

    Socket clientSocket = new Socket("localhost", 9876);
    DataOutputStream outToServer = new DataOutputStream(clientSocket.getOutputStream());
    BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    // Lê comandos de um arquivo texto no mesmo diretório do projeto.
    // Primeira linha: tempo em milissegundos entre comandos.
    // Demais linhas: comandos a serem enviados.
    String fileName = args != null && args.length > 0 ? args[0] : "Teste.txt";
    try {
      List<String> linhas = Files.readAllLines(Paths.get(fileName), StandardCharsets.UTF_8);
      if (linhas.isEmpty()) {
        System.err.println("Arquivo de comandos vazio: " + fileName);
        clientSocket.close();
        return;
      }
      // Primeira linha: tempoComandos
      String tempoStr = linhas.get(0).trim();
      try {
        tmpTempoComandos = Integer.parseInt(tempoStr);
      } catch (NumberFormatException nfe) {
        System.err.println("Primeira linha deve ser o tempo em ms. Valor lido: '" + tempoStr + "'. Usando 200ms.");
        tmpTempoComandos = 200;
      }

      // Demais linhas: comandos (ignora vazias e comentários iniciados com '#')
      List<String> listaComandos = new ArrayList<>();
      for (int i = 1; i < linhas.size(); i++) {
        String line = linhas.get(i);
        if (line == null)
          continue;
        String cmd = line.trim();
        if (cmd.isEmpty() || cmd.startsWith("#"))
          continue;
        listaComandos.add(cmd);
      }
      if (listaComandos.isEmpty()) {
        System.err.println("Nenhum comando encontrado no arquivo: " + fileName);
        clientSocket.close();
        return;
      }
      comandos = listaComandos.toArray(new String[0]);
      System.out.println(
          "Carregado " + comandos.length + " comandos de '" + fileName + "' com intervalo de " + tmpTempoComandos
              + "ms.");
    } catch (IOException ioex) {
      System.err.println("Falha ao ler arquivo de comandos '" + fileName + "': " + ioex.getMessage());
      clientSocket.close();
      return;
    }
    final int tempoComandos = tmpTempoComandos;
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

    // Este cliente de teste apenas envia comandos programados a partir do arquivo

    // Thread que envia cada comando do array em intervalo fixo, sem aguardar
    // respostas
    Thread sender = new Thread(() -> {
      try {
        if (comandos == null) {
          System.err.println("Nenhum comando carregado. Encerrando.");
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
