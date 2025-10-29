package server;

import java.io.*;
import java.net.*;
import java.util.*;
import classes.*;

public class Server {
  // Até então eu havia feito toda a lógica dentro do ClientHandler sem se
  // preocupar com condições de corrida ou uma melhor separação de
  // responsabilidades essa classe tem o intuito de tentar melhorar isso
  static GameManager gameManager = new GameManager();

  // Declarando thread para lidar com cada cliente conectado
  // Como lidar com threads visto em:
  // https://www.geeksforgeeks.org/java/java-multithreading-tutorial
  static class ClientHandler extends Thread {
    private Socket connectionSocket;
    private GameManager gameManager;

    // Salvando o socket de conexão do cliente para podermos utilizar no run()
    public ClientHandler(Socket socket, GameManager gameManager) {
      this.connectionSocket = socket;
      this.gameManager = gameManager;
    }

    // Método que recebe um indice para verificar se foi informado algo para o campo
    // daquele indice
    // Se não foi informado, envia uma mensagem dizendo que nomeCampo não foi
    // informado ao cliente
    static Boolean verificarCampo(String nomeCampo, int indice, String[] splitedSentence, DataOutputStream outToClient)
        throws IOException {
      if (splitedSentence.length < indice + 1 || splitedSentence[indice].isEmpty()) {
        outToClient.writeBytes("0|" + nomeCampo + " nao informado|\n");
        return false;
      }
      return true;
    }

    // Método para validar se o cliente existe e se o token está correto, se estiver
    // correto, ele vai setar o socket de conexão do cliente novamente
    static Boolean validarCliente(Cliente cliente, String tokenCliente, DataOutputStream outToClient,
        Socket connectionSocket) throws IOException {
      if (cliente == null) {
        outToClient.writeBytes("0|Cliente nao encontrado|\n");
        return false;
      }
      if (!cliente.validarToken(tokenCliente)) {
        outToClient.writeBytes("0|Token invalido|\n");
        return false;
      }

      // O token está correto, vamos setar o socket de conexão, para caso ele tiver se
      // desconectado e reconectado
      cliente.setConnectionSocket(connectionSocket);
      return true;
    }

    public void run() {
      try {
        // Código tirado dos slides
        BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
        DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
        String sentence;
        // Variável para controlar o loop, manter o socket aberto até o cliente pedir
        // para sair
        Boolean sair = false;

        while (!sair) {
          Map<String, Cliente> listaCliente = gameManager.getListaCliente();
          while ((sentence = inFromClient.readLine()) != null) {
            // Separando a sentença nas palavras
            String splitedSentence[] = sentence.split(" ");
            // Switch para os comandos enviados pelo cliente
            switch (splitedSentence[0].toUpperCase()) {
              // CADASTRAR <nomeCliente>
              case "CADASTRAR": {
                if (!verificarCampo("nome", 1, splitedSentence, outToClient))
                  break;

                String nomeCliente = splitedSentence[1];

                gameManager.cadastrarCliente(listaCliente, nomeCliente, connectionSocket);
                break;
              }
              // LISTARPARTIDAS
              case "LISTARPARTIDAS": {
                gameManager.listarPartidasCliente(outToClient);
                break;
              }
              // LISTARJOGADORES
              case "LISTARJOGADORES": {
                gameManager.listarJogadoresCliente(outToClient);
                break;
              }
              // ENTRARPARTIDA <nome> <token> <idPartida>
              case "ENTRARPARTIDA": {
                if (!verificarCampo("nome", 1, splitedSentence, outToClient) ||
                    !verificarCampo("token", 2, splitedSentence, outToClient) ||
                    !verificarCampo("idPartida", 3, splitedSentence, outToClient))
                  break;

                String nomeCliente = splitedSentence[1];
                String tokenCliente = splitedSentence[2];
                String idPartidaStr = splitedSentence[3];

                Cliente cliente = listaCliente.get(nomeCliente);

                if (!validarCliente(cliente, tokenCliente, outToClient, connectionSocket))
                  break;

                // Convertendo idPartida para inteiro
                int idPartida;
                try {
                  idPartida = Integer.parseInt(idPartidaStr);
                } catch (NumberFormatException e) {
                  outToClient.writeBytes("0|ID da partida invalido|\n");
                  break;
                }

                gameManager.entrarPartidaCliente(cliente, idPartida);
                break;
              }
              // DESAFIAR <nomeDesafiante> <token> <nomeDesafiado>
              case "DESAFIAR": {
                if (!verificarCampo("nomeDesafiante", 1, splitedSentence, outToClient) ||
                    !verificarCampo("token", 2, splitedSentence, outToClient) ||
                    !verificarCampo("nomeDesafiado", 3, splitedSentence, outToClient))
                  break;
                String nomeDesafiante = splitedSentence[1];
                String tokenDesafiante = splitedSentence[2];
                String nomeDesafiado = splitedSentence[3];

                Cliente clienteDesafiante = listaCliente.get(nomeDesafiante);

                if (!validarCliente(clienteDesafiante, tokenDesafiante, outToClient, connectionSocket))
                  break;

                Cliente clienteDesafiado = listaCliente.get(nomeDesafiado);
                if (clienteDesafiado == null) {
                  outToClient.writeBytes("0|Cliente desafiado nao encontrado|\n");
                  break;
                }

                gameManager.desafiarCliente(clienteDesafiante, clienteDesafiado);
                break;
              }
              // MOVER <nome> <token> <posicaoX> <posicaoY> <modoDeslocamento>
              case "MOVER": {
                if (!verificarCampo("nome", 1, splitedSentence, outToClient) ||
                    !verificarCampo("token", 2, splitedSentence, outToClient) ||
                    !verificarCampo("posicaoX", 3, splitedSentence, outToClient) ||
                    !verificarCampo("posicaoY", 4, splitedSentence, outToClient))
                  break;

                String nomeCliente = splitedSentence[1];
                String tokenCliente = splitedSentence[2];
                int posicaoX = Integer.parseInt(splitedSentence[3]);
                int posicaoY = Integer.parseInt(splitedSentence[4]);

                // Opção adicional, se for true, então as posições passadas não serão tratadas
                // como posições absolutas, mas como deslocamentos relativos
                Boolean deslocamento = false;
                if (splitedSentence.length > 5) {
                  String deslocamentoStr = splitedSentence[5];
                  deslocamento = deslocamentoStr.equals("true") || deslocamentoStr.equals("1");
                }

                Cliente cliente = listaCliente.get(nomeCliente);

                if (!validarCliente(cliente, tokenCliente, outToClient, connectionSocket))
                  break;

                gameManager.moverCliente(cliente, posicaoX, posicaoY, deslocamento);
                break;
              }
              // ATACAR <nome> <token> <posicaoX> <posicaoY> <modoDeslocamento>
              case "ATACAR": {
                if (!verificarCampo("nome", 1, splitedSentence, outToClient) ||
                    !verificarCampo("token", 2, splitedSentence, outToClient) ||
                    !verificarCampo("posicaoX", 3, splitedSentence, outToClient) ||
                    !verificarCampo("posicaoY", 4, splitedSentence, outToClient))
                  break;

                String nomeCliente = splitedSentence[1];
                String tokenCliente = splitedSentence[2];
                int posicaoX = Integer.parseInt(splitedSentence[3]);
                int posicaoY = Integer.parseInt(splitedSentence[4]);

                // Opção adicional, se for true, então as posições passadas não serão tratadas
                // como posições absolutas, mas como deslocamentos relativos
                Boolean deslocamento = false;
                if (splitedSentence.length > 5) {
                  String deslocamentoStr = splitedSentence[5];
                  deslocamento = deslocamentoStr.equals("true") || deslocamentoStr.equals("1");
                }

                Cliente cliente = listaCliente.get(nomeCliente);

                if (!validarCliente(cliente, tokenCliente, outToClient, connectionSocket))
                  break;

                gameManager.atacarCliente(cliente, posicaoX, posicaoY, deslocamento);
                break;
              }
              // SONAR <nome> <token> <posicaoX> <posicaoY> <modoDeslocamento>
              case "SONAR": {
                if (!verificarCampo("nome", 1, splitedSentence, outToClient) ||
                    !verificarCampo("token", 2, splitedSentence, outToClient) ||
                    !verificarCampo("posicaoX", 3, splitedSentence, outToClient) ||
                    !verificarCampo("posicaoY", 4, splitedSentence, outToClient))
                  break;

                String nomeCliente = splitedSentence[1];
                String tokenCliente = splitedSentence[2];
                int posicaoX = Integer.parseInt(splitedSentence[3]);
                int posicaoY = Integer.parseInt(splitedSentence[4]);

                // Opção adicional, se for true, então as posições passadas não serão tratadas
                // como posições absolutas, mas como deslocamentos relativos
                Boolean deslocamento = false;
                if (splitedSentence.length > 5) {
                  String deslocamentoStr = splitedSentence[5];
                  deslocamento = deslocamentoStr.equals("true") || deslocamentoStr.equals("1");
                }

                Cliente cliente = listaCliente.get(nomeCliente);

                if (!validarCliente(cliente, tokenCliente, outToClient, connectionSocket))
                  break;

                gameManager.sonarCliente(cliente, posicaoX, posicaoY, deslocamento);
                break;
              }
              // PASSAR <nome> <token>
              case "PASSAR": {
                if (!verificarCampo("nome", 1, splitedSentence, outToClient) ||
                    !verificarCampo("token", 2, splitedSentence, outToClient))
                  break;

                String nomeCliente = splitedSentence[1];
                String tokenCliente = splitedSentence[2];

                Cliente cliente = listaCliente.get(nomeCliente);

                if (!validarCliente(cliente, tokenCliente, outToClient, connectionSocket))
                  break;

                gameManager.passarCliente(cliente);
                break;
              }
              // SAIRPARTIDA <nome> <token>
              case "SAIRPARTIDA": {
                if (!verificarCampo("nome", 1, splitedSentence, outToClient) ||
                    !verificarCampo("token", 2, splitedSentence, outToClient))
                  break;

                String nomeCliente = splitedSentence[1];
                String tokenCliente = splitedSentence[2];

                Cliente cliente = listaCliente.get(nomeCliente);

                if (!validarCliente(cliente, tokenCliente, outToClient, connectionSocket))
                  break;

                gameManager.sairPartidaCliente(cliente);
                break;
              }
              // SAIR <nome> <token>
              case "SAIR": {
                if (!verificarCampo("nome", 1, splitedSentence, outToClient) ||
                    !verificarCampo("token", 2, splitedSentence, outToClient))
                  break;

                String nomeCliente = splitedSentence[1];
                String tokenCliente = splitedSentence[2];

                Cliente cliente = listaCliente.get(nomeCliente);

                if (!validarCliente(cliente, tokenCliente, outToClient, connectionSocket))
                  break;

                System.out.println("Removendo cliente: " + nomeCliente + " com token: " + tokenCliente);

                listaCliente.remove(nomeCliente);
                outToClient.writeBytes("1|Cliente desconectado com sucesso|\n");

                // Sai do loop para fechar o socket
                sair = true;
                break;
              }
              default:
                outToClient.writeBytes("0|Comando desconhecido|\n");
                break;
            }
          }
        }
      } catch (

      Exception e) {
        e.printStackTrace();
      } finally {
        try {
          connectionSocket.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }

  }

  public static void main(String[] args) throws Exception {
    // Declarando o socket do servidor
    // Código tirado dos slides
    ServerSocket welcomeSocket = new ServerSocket(Constants.PORTA_SERVIDOR);
    System.out.println("Servidor iniciado na porta " + Constants.PORTA_SERVIDOR);

    try {
      gameManager.criarPartidas();

      while (true) {
        // Aceite todas as conexões de entrada
        Socket connectionSocket = welcomeSocket.accept();

        System.out.println("Cliente conectado: " + connectionSocket.getInetAddress().getHostAddress() + ":"
            + connectionSocket.getPort());

        // Declarando um ClientHandler (thread) para cada conexão
        ClientHandler clientHandler = new ClientHandler(connectionSocket, gameManager);
        clientHandler.start();
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      welcomeSocket.close();
    }
  }
}