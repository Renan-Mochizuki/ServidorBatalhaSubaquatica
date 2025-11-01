package server;

import java.io.*;
import java.net.*;
import java.util.*;
import classes.*;

public class Server {
  // Até então eu havia feito toda a lógica dentro do ClientHandler sem se
  // preocupar com condições de corrida ou uma melhor separação de
  // responsabilidades essa classe tem o intuito de tentar melhorar isso
  private final GameManager gameManager = new GameManager();
  private final Tradutor tradutor = new Tradutor();

  // Declarando thread para lidar com cada cliente conectado
  // Como lidar com threads visto em:
  // https://www.geeksforgeeks.org/java/java-multithreading-tutorial
  class ClientHandler extends Thread {
    private Socket connectionSocket;

    // Salvando o socket de conexão do cliente para podermos utilizar no run()
    public ClientHandler(Socket socket) {
      this.connectionSocket = socket;
    }

    // Método que recebe um indice para verificar se foi informado algo para o campo
    // daquele indice
    // Se não foi informado, envia uma mensagem dizendo que nomeCampo não foi
    // informado ao cliente
    private boolean verificarCampo(String nomeCampo, int indice, String[] splitedSentence, String tipo,
        DataOutputStream outToClient)
        throws IOException {
      if (splitedSentence.length < indice + 1 || splitedSentence[indice].isEmpty()) {
        enviarLinha(outToClient, tipo, "400", nomeCampo + " nao informado", "campo:" + nomeCampo);
        return false;
      }
      return true;
    }

    // Método para validar se o cliente existe e se o token está correto, se estiver
    // correto, ele vai setar o socket de conexão do cliente novamente
    private boolean validarCliente(Cliente cliente, String tokenCliente, String tipo, Socket connectionSocket)
        throws IOException {
      DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
      if (cliente == null) {
        enviarLinha(outToClient, tipo, "404", "Cliente nao encontrado", "campo:nomeCliente");
        return false;
      }
      if (!cliente.validarToken(tokenCliente)) {
        enviarLinha(outToClient, tipo, "401", "Token invalido", "campo:tokenCliente");
        return false;
      }

      // O token está correto, vamos setar o socket de conexão, para caso ele tiver se
      // desconectado e reconectado
      cliente.setConnectionSocket(connectionSocket);

      // Se o jogador estiver em uma partida vamos atualizar o socket do seu objeto
      // jogador também
      JogoPartida jogoPartida = gameManager.encontrarPartidaAndamento(cliente.getIdPartida());
      if (jogoPartida != null) {
        Jogador jogador = jogoPartida.buscarJogadorPorNome(cliente.getNome());
        if (jogador != null) {
          jogador.setConnectionSocket(connectionSocket);
        }
      }

      // Renova o keepalive para qualquer ação validada
      gameManager.keepAliveCliente(cliente, Constants.TIPOKEEPALIVE);
      return true;
    }

    // Método para enviar uma linha para o cliente evitando erros de conexão
    private void enviarLinha(DataOutputStream outToClient, String tipo, String codigo, String texto, String valor) {
      if (outToClient == null)
        return;
      try {
        String spr = Constants.SEPARADOR;
        String linha = tipo + spr + codigo + spr + texto + spr + valor;
        outToClient.writeBytes(linha + "\n");
        outToClient.flush();
      } catch (IOException e) {
        // Iremos ignorar, cliente pode ter fechado a conexão
      }
    }

    public void run() {
      try {
        // Código tirado dos slides
        BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
        DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
        String sentence;
        // Variável para controlar o loop, manter o socket aberto até o cliente pedir
        // para sair
        boolean sair = false;
        Map<String, Cliente> listaCliente = gameManager.getListaCliente();
        while ((sentence = inFromClient.readLine()) != null && !sair) {
          // Separando a sentença nas palavras
          sentence = sentence.trim();
          String splitedSentence[] = sentence.split(Constants.SEPARADORCLIENTE);
          String comandoEnviado = splitedSentence[0].toUpperCase();
          if (tradutor.containsKey(comandoEnviado)) {
            comandoEnviado = tradutor.get(comandoEnviado);
          }
          // Outro try catch para que um erro em um comando não feche a conexão
          try {
            // Switch para os comandos enviados pelo cliente
            switch (comandoEnviado) {
              // CADASTRAR <nomeCliente>
              case "CADASTRAR": {
                String tipo = Constants.TIPOCADASTRAR;
                if (!verificarCampo("nome", 1, splitedSentence, tipo, outToClient))
                  break;

                String nomeCliente = splitedSentence[1];

                gameManager.cadastrarCliente(listaCliente, nomeCliente, connectionSocket, tipo);
                break;
              }
              // LISTARPARTIDAS
              case "LISTARPARTIDAS": {
                String tipo = Constants.TIPOLISTARPARTIDAS;
                gameManager.listarPartidasCliente(outToClient, tipo);
                break;
              }
              // LISTARJOGADORES
              case "LISTARJOGADORES": {
                String tipo = Constants.TIPOLISTARJOGADORES;
                gameManager.listarJogadoresCliente(outToClient, tipo);
                break;
              }
              // ENTRARPARTIDA <nome> <token> <idPartida>
              case "ENTRARPARTIDA": {
                String tipo = Constants.TIPOENTRARPARTIDA;
                if (!verificarCampo("nome", 1, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("token", 2, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("idPartida", 3, splitedSentence, tipo, outToClient))
                  break;

                String nomeCliente = splitedSentence[1];
                String tokenCliente = splitedSentence[2];
                String idPartidaStr = splitedSentence[3];

                Cliente cliente = listaCliente.get(nomeCliente);

                if (!validarCliente(cliente, tokenCliente, tipo, connectionSocket))
                  break;

                // Convertendo idPartida para inteiro
                int idPartida;
                try {
                  idPartida = Integer.parseInt(idPartidaStr);
                } catch (NumberFormatException e) {
                  cliente.enviarLinha("LISTARPARTIDAS", "400", "id da partida invalido", "campo:idPartida");
                  break;
                }

                gameManager.entrarPartidaCliente(cliente, idPartida, tipo);
                break;
              }
              // DESAFIAR <nomeDesafiante> <token> <nomeDesafiado>
              case "DESAFIAR": {
                String tipo = Constants.TIPODESAFIAR;
                if (!verificarCampo("nomeDesafiante", 1, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("token", 2, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("nomeDesafiado", 3, splitedSentence, tipo, outToClient))
                  break;
                String nomeDesafiante = splitedSentence[1];
                String tokenDesafiante = splitedSentence[2];
                String nomeDesafiado = splitedSentence[3];

                Cliente clienteDesafiante = listaCliente.get(nomeDesafiante);

                if (!validarCliente(clienteDesafiante, tokenDesafiante, tipo, connectionSocket))
                  break;

                Cliente clienteDesafiado = listaCliente.get(nomeDesafiado);
                if (clienteDesafiado == null) {
                  clienteDesafiante.enviarLinha("DESAFIAR", "404", "Cliente desafiado nao encontrado",
                      "campo:nomeDesafiado");
                  break;
                }

                gameManager.desafiarCliente(clienteDesafiante, clienteDesafiado, tipo);
                break;
              }
              // ACEITARDESAFIO <nomeDesafiado> <token> <nomeDesafiante>
              case "ACEITARDESAFIO": {
                String tipo = Constants.TIPOACEITARDESAFIO;
                if (!verificarCampo("nomeDesafiado", 1, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("token", 2, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("nomeDesafiante", 3, splitedSentence, tipo, outToClient))
                  break;
                String nomeDesafiado = splitedSentence[1];
                String tokenDesafiado = splitedSentence[2];
                String nomeDesafiante = splitedSentence[3];

                Cliente clienteDesafiado = listaCliente.get(nomeDesafiado);

                if (!validarCliente(clienteDesafiado, tokenDesafiado, tipo, connectionSocket))
                  break;

                Cliente clienteDesafiante = listaCliente.get(nomeDesafiante);
                if (clienteDesafiante == null) {
                  clienteDesafiado.enviarLinha("ACEITARDESAFIO", "404", "Cliente desafiante nao encontrado",
                      "campo:nomeDesafiante");
                  break;
                }

                gameManager.aceitarDesafioCliente(clienteDesafiado, clienteDesafiante, tipo);
                break;
              }
              // RECUSARDESAFIO <nomeDesafiado> <token> <nomeDesafiante>
              case "RECUSARDESAFIO": {
                String tipo = Constants.TIPORECUSARDESAFIO;
                if (!verificarCampo("nomeDesafiado", 1, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("token", 2, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("nomeDesafiante", 3, splitedSentence, tipo, outToClient))
                  break;
                String nomeDesafiado = splitedSentence[1];
                String tokenDesafiado = splitedSentence[2];
                String nomeDesafiante = splitedSentence[3];

                Cliente clienteDesafiado = listaCliente.get(nomeDesafiado);

                if (!validarCliente(clienteDesafiado, tokenDesafiado, tipo, connectionSocket))
                  break;

                Cliente clienteDesafiante = listaCliente.get(nomeDesafiante);
                if (clienteDesafiante == null) {
                  clienteDesafiado.enviarLinha("RECUSARDESAFIO", "404", "Cliente desafiante nao encontrado",
                      "campo:nomeDesafiante");
                  break;
                }

                gameManager.recusarDesafioCliente(clienteDesafiado, clienteDesafiante, tipo);
                break;
              }
              // CHATGLOBAL <nome> <token> <mensagem>
              case "CHATGLOBAL": {
                String tipo = Constants.TIPOCHATGLOBAL;
                if (!verificarCampo("nome", 1, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("token", 2, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("mensagem", 3, splitedSentence, tipo, outToClient))
                  break;

                String nomeCliente = splitedSentence[1];
                String tokenCliente = splitedSentence[2];
                // Juntando a mensagem novamente
                String mensagem = String.join(" ", Arrays.copyOfRange(splitedSentence, 3, splitedSentence.length));

                Cliente cliente = listaCliente.get(nomeCliente);

                if (!validarCliente(cliente, tokenCliente, tipo, connectionSocket))
                  break;

                gameManager.chatGlobalCliente(cliente, mensagem, tipo);
                break;
              }
              // CHATPARTIDA <nome> <token> <mensagem>
              case "CHATPARTIDA": {
                String tipo = Constants.TIPOCHATPARTIDA;
                if (!verificarCampo("nome", 1, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("token", 2, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("mensagem", 3, splitedSentence, tipo, outToClient))
                  break;

                String nomeCliente = splitedSentence[1];
                String tokenCliente = splitedSentence[2];
                // Juntando a mensagem novamente
                String mensagem = String.join(" ", Arrays.copyOfRange(splitedSentence, 3, splitedSentence.length));

                Cliente cliente = listaCliente.get(nomeCliente);

                if (!validarCliente(cliente, tokenCliente, tipo, connectionSocket))
                  break;

                gameManager.chatPartidaCliente(cliente, mensagem, tipo);
                break;
              }
              // CHATJOGADOR <nome> <token> <nomeDestinatario> <mensagem>
              case "CHATJOGADOR": {
                String tipo = Constants.TIPOCHATJOGADOR;
                if (!verificarCampo("nome", 1, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("token", 2, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("nomeDestinatario", 3, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("mensagem", 4, splitedSentence, tipo, outToClient))
                  break;

                String nomeCliente = splitedSentence[1];
                String tokenCliente = splitedSentence[2];
                String nomeDestinatario = splitedSentence[3];
                // Juntando a mensagem novamente
                String mensagem = String.join(" ", Arrays.copyOfRange(splitedSentence, 4, splitedSentence.length));

                Cliente cliente = listaCliente.get(nomeCliente);

                if (!validarCliente(cliente, tokenCliente, tipo, connectionSocket))
                  break;

                gameManager.chatJogadorCliente(cliente, nomeDestinatario, mensagem, tipo);
                break;
              }
              // PRONTOPARTIDA <nome> <token>
              case "PRONTOPARTIDA": {
                String tipo = Constants.TIPOPRONTOPARTIDA;
                if (!verificarCampo("nome", 1, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("token", 2, splitedSentence, tipo, outToClient))
                  break;

                String nomeCliente = splitedSentence[1];
                String tokenCliente = splitedSentence[2];

                Cliente cliente = listaCliente.get(nomeCliente);

                if (!validarCliente(cliente, tokenCliente, tipo, connectionSocket))
                  break;

                gameManager.prontoPartidaCliente(cliente, tipo);
                break;
              }
              // MOVER <nome> <token> <posicaoX> <posicaoY> <modoDeslocamento>
              case "MOVER": {
                String tipo = Constants.TIPOMOVER;
                if (!verificarCampo("nome", 1, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("token", 2, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("posicaoX", 3, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("posicaoY", 4, splitedSentence, tipo, outToClient))
                  break;

                String nomeCliente = splitedSentence[1];
                String tokenCliente = splitedSentence[2];
                int posicaoX = Integer.parseInt(splitedSentence[3]);
                int posicaoY = Integer.parseInt(splitedSentence[4]);

                // Opção adicional, se for true, então as posições passadas não serão tratadas
                // como posições absolutas, mas como deslocamentos relativos
                boolean deslocamento = false;
                if (splitedSentence.length > 5) {
                  String deslocamentoStr = splitedSentence[5];
                  deslocamento = deslocamentoStr.equals("true") || deslocamentoStr.equals("1");
                }

                Cliente cliente = listaCliente.get(nomeCliente);

                if (!validarCliente(cliente, tokenCliente, tipo, connectionSocket))
                  break;

                gameManager.moverCliente(cliente, posicaoX, posicaoY, deslocamento, tipo);
                break;
              }
              // ATACAR <nome> <token> <posicaoX> <posicaoY> <modoDeslocamento>
              case "ATACAR": {
                String tipo = Constants.TIPOATACAR;
                if (!verificarCampo("nome", 1, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("token", 2, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("posicaoX", 3, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("posicaoY", 4, splitedSentence, tipo, outToClient))
                  break;

                String nomeCliente = splitedSentence[1];
                String tokenCliente = splitedSentence[2];
                int posicaoX = Integer.parseInt(splitedSentence[3]);
                int posicaoY = Integer.parseInt(splitedSentence[4]);

                // Opção adicional, se for true, então as posições passadas não serão tratadas
                // como posições absolutas, mas como deslocamentos relativos
                boolean deslocamento = false;
                if (splitedSentence.length > 5) {
                  String deslocamentoStr = splitedSentence[5];
                  deslocamento = deslocamentoStr.equals("true") || deslocamentoStr.equals("1");
                }

                Cliente cliente = listaCliente.get(nomeCliente);

                if (!validarCliente(cliente, tokenCliente, tipo, connectionSocket))
                  break;

                gameManager.atacarCliente(cliente, posicaoX, posicaoY, deslocamento, tipo);
                break;
              }
              // SONAR <nome> <token> <posicaoX> <posicaoY> <modoDeslocamento>
              case "SONAR": {
                String tipo = Constants.TIPOSONAR;
                if (!verificarCampo("nome", 1, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("token", 2, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("posicaoX", 3, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("posicaoY", 4, splitedSentence, tipo, outToClient))
                  break;

                String nomeCliente = splitedSentence[1];
                String tokenCliente = splitedSentence[2];
                int posicaoX = Integer.parseInt(splitedSentence[3]);
                int posicaoY = Integer.parseInt(splitedSentence[4]);

                // Opção adicional, se for true, então as posições passadas não serão tratadas
                // como posições absolutas, mas como deslocamentos relativos
                boolean deslocamento = false;
                if (splitedSentence.length > 5) {
                  String deslocamentoStr = splitedSentence[5];
                  deslocamento = deslocamentoStr.equals("true") || deslocamentoStr.equals("1");
                }

                Cliente cliente = listaCliente.get(nomeCliente);

                if (!validarCliente(cliente, tokenCliente, tipo, connectionSocket))
                  break;

                gameManager.sonarCliente(cliente, posicaoX, posicaoY, deslocamento, tipo);
                break;
              }
              // PASSAR <nome> <token>
              case "PASSAR": {
                String tipo = Constants.TIPOPASSAR;
                if (!verificarCampo("nome", 1, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("token", 2, splitedSentence, tipo, outToClient))
                  break;

                String nomeCliente = splitedSentence[1];
                String tokenCliente = splitedSentence[2];

                Cliente cliente = listaCliente.get(nomeCliente);

                if (!validarCliente(cliente, tokenCliente, tipo, connectionSocket))
                  break;

                gameManager.passarCliente(cliente, tipo);
                break;
              }
              // SAIRPARTIDA <nome> <token>
              case "SAIRPARTIDA": {
                String tipo = Constants.TIPOSAIRPARTIDA;
                if (!verificarCampo("nome", 1, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("token", 2, splitedSentence, tipo, outToClient))
                  break;

                String nomeCliente = splitedSentence[1];
                String tokenCliente = splitedSentence[2];

                Cliente cliente = listaCliente.get(nomeCliente);

                if (!validarCliente(cliente, tokenCliente, tipo, connectionSocket))
                  break;

                gameManager.sairPartidaCliente(cliente, tipo);
                break;
              }
              // SAIR <nome> <token>
              case "SAIR": {
                String tipo = Constants.TIPOSAIR;
                if (!verificarCampo("nome", 1, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("token", 2, splitedSentence, tipo, outToClient))
                  break;

                String nomeCliente = splitedSentence[1];
                String tokenCliente = splitedSentence[2];

                Cliente cliente = listaCliente.get(nomeCliente);

                if (!validarCliente(cliente, tokenCliente, tipo, connectionSocket))
                  break;

                System.out.println("Removendo cliente: " + nomeCliente);

                gameManager.sairCliente(cliente, tipo);

                // Marca para encerrar o loop de leitura e fechar o socket
                sair = true;
                break;
              }
              // KEEPALIVE <nome> <token>
              case "KEEPALIVE": {
                String tipo = Constants.TIPOKEEPALIVE;
                if (!verificarCampo("nome", 1, splitedSentence, tipo, outToClient) ||
                    !verificarCampo("token", 2, splitedSentence, tipo, outToClient))
                  break;

                String nomeCliente = splitedSentence[1];
                String tokenCliente = splitedSentence[2];

                Cliente cliente = listaCliente.get(nomeCliente);

                if (!validarCliente(cliente, tokenCliente, tipo, connectionSocket))
                  break;

                gameManager.keepAliveCliente(cliente, tipo);
                break;
              }
              default: {
                enviarLinha(outToClient, "DESCONHECIDO", "405", "Comando desconhecido", "");
                break;
              }
            }
          } catch (Exception e) {
            // Erro inesperado no processamento do comando
            enviarLinha(outToClient, Constants.TIPOERRO, "500", "Erro interno do servidor",
                "excecao:" + e.getClass().getSimpleName());
            e.printStackTrace();
          }
        }
      } catch (IOException e) {
        // Ignora
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        try {
          connectionSocket.close();
        } catch (IOException e) {
          // Ignora
        }
      }
    }
  }

  public static void main(String[] args) throws Exception {
    ServerSocket welcomeSocket = null;
    // Código para testar várias portas se a porta padrão já estiver em uso
    int basePort = Constants.PORTA_SERVIDOR;
    int tryPort = basePort;
    for (int i = 0; i < 100; i++) {
      try {
        welcomeSocket = new ServerSocket(tryPort);
        System.out.println("Servidor iniciado na porta " + tryPort);
        break;
      } catch (java.net.BindException be) {
        tryPort++;
      }
    }

    try {
      Server server = new Server();
      server.gameManager.criarPartidas();

      while (true) {
        // Aceite todas as conexões de entrada
        Socket connectionSocket = welcomeSocket.accept();

        System.out.println("Cliente conectado: " + connectionSocket.getInetAddress().getHostAddress() + ":"
            + connectionSocket.getPort());

        // Declarando um ClientHandler (thread) para cada conexão
        ClientHandler clientHandler = server.new ClientHandler(connectionSocket);
        clientHandler.start();
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        welcomeSocket.close();
      } catch (IOException ioe) {
        // Ignora
      }
    }
  }
}