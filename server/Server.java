package server;

import java.io.*;
import java.net.*;
import java.util.*;
import classes.*;

public class Server {

  // Até então havia feito toda a lógica dentro do ClientHandler sem se preocupar
  // com condições de corrida essa classe tem o intuito de lidar com isso
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
              // CADASTRO <nomeCliente>
              case "CADASTRO": {
                if (!verificarCampo("nome", 1, splitedSentence, outToClient))
                  break;

                String nomeCliente = splitedSentence[1];

                gameManager.inserirCliente(listaCliente, nomeCliente, connectionSocket);
                break;
              }
              // LISTARPARTIDAS
              case "LISTARPARTIDAS": {
                gameManager.listarPartidas(outToClient);
                break;
              }
              // LISTARJOGADORES
              case "LISTARJOGADORES": {
                gameManager.listarJogadores(outToClient);
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

                // Percorre todas partidas e verifica se o id
                Partida partidaEscolhida = gameManager.encontrarPartida(idPartida);

                // Partida não encontrada
                if (partidaEscolhida == null) {
                  outToClient.writeBytes("0|Partida inexistente|\n");
                  break;
                }

                // Se a partida estiver lotada, não pode entrar
                if (partidaEscolhida.partidaLotada() == true) {
                  outToClient.writeBytes("0|Partida lotada|\n");
                  break;
                }

                // Pega o id da partida armazenada em cliente
                int idPartidaCliente = cliente.getIdPartida();

                // Se o cliente já está na partida escolhida, não faz nada
                if (idPartidaCliente == idPartida) {
                  outToClient.writeBytes("1|Cliente ja esta nessa partida|\n");
                  break;
                }

                // Se o cliente já estava em alguma partida
                if (idPartidaCliente != -1) {
                  JogoPartida partidaAndamento = gameManager.encontrarPartidaAndamento(idPartidaCliente);
                  if (partidaAndamento != null) {
                    outToClient.writeBytes("0|Nao e possivel trocar de partida durante um jogo|\n");
                    break;
                  }
                  Partida partidaAnterior = gameManager.encontrarPartida(idPartidaCliente);
                  if (partidaAnterior != null) {
                    // Remove o cliente da partida anterior
                    partidaAnterior.removerCliente(cliente);
                    System.out.println("Cliente " + nomeCliente + " saiu da partida " + idPartidaCliente);
                  }
                }

                // Define a partida do cliente
                cliente.setIdPartida(idPartida);
                partidaEscolhida.adicionarCliente(cliente);

                System.out.println("Cliente " + nomeCliente + " entrou na partida " + idPartida);

                outToClient.writeBytes("1|Entrou na partida com sucesso|\n");

                // Tenta iniciar a partida
                gameManager.tentarIniciarPartida(partidaEscolhida);
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

                // Verifica se o desafiante está em uma partida em andamento
                int idPartidaDesafiante = clienteDesafiante.getIdPartida();
                JogoPartida partidaAndamento = gameManager.encontrarPartidaAndamento(idPartidaDesafiante);
                if (partidaAndamento != null) {
                  outToClient.writeBytes("0|Nao e possivel desafiar durante uma partida em andamento|\n");
                  break;
                }

                Cliente clienteDesafiado = listaCliente.get(nomeDesafiado);
                if (clienteDesafiado == null) {
                  outToClient.writeBytes("0|Cliente desafiado nao encontrado|\n");
                  break;
                }

                // Se o destinatario não desafiou o remetente, então vamos apenas enviar o nosso
                // desafio para ele
                if (!nomeDesafiante.equals(clienteDesafiado.getJogadorDesafiado())) {
                  gameManager.notificarJogadorPartida(clienteDesafiado, "Desafio recebido", nomeDesafiante);

                  clienteDesafiante.setJogadorDesafiado(nomeDesafiado);

                  outToClient.writeBytes("1|Desafio enviado com sucesso|\n");
                  break;
                }

                // Os dois jogadores se desafiaram, vamos iniciar a partida diretamente

                // Criando a lista de clientes para a nova partida
                List<Cliente> clientes = new ArrayList<>();
                clientes.add(clienteDesafiante);
                clientes.add(clienteDesafiado);

                int idPartida = gameManager.proximoIdAutoIncrement();

                clienteDesafiante.setIdPartida(idPartida);
                clienteDesafiado.setIdPartida(idPartida);

                JogoPartida novaPartida = new JogoPartida(idPartida, clientes, null);

                gameManager.iniciarJogoPartida(novaPartida);
                break;
              }
              // MOVER <nome> <token> <posicaoX> <posicaoY>
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

                Cliente cliente = listaCliente.get(nomeCliente);

                if (!validarCliente(cliente, tokenCliente, outToClient, connectionSocket))
                  break;

                JogoPartida partidaAndamento = gameManager.encontrarPartidaAndamento(cliente.getIdPartida());
                if (partidaAndamento == null) {
                  outToClient.writeBytes("0|Cliente nao esta em uma partida em andamento|\n");
                  break;
                }

                if (!nomeCliente.equals(partidaAndamento.getJogadorTurno())) {
                  outToClient.writeBytes("0|Nao e o turno do jogador|\n");
                  break;
                }

                if (!partidaAndamento.movimento(nomeCliente, posicaoX, posicaoY)) {
                  outToClient.writeBytes("0|Movimento invalido|\n");
                } else {
                  outToClient.writeBytes("1|Movimento realizado com sucesso|\n");

                  gameManager.proximoTurnoPartida(partidaAndamento);
                }
                break;
              }
              // ATACAR <nome> <token> <posicaoX> <posicaoY>
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

                Cliente cliente = listaCliente.get(nomeCliente);

                if (!validarCliente(cliente, tokenCliente, outToClient, connectionSocket))
                  break;

                JogoPartida partidaAndamento = gameManager.encontrarPartidaAndamento(cliente.getIdPartida());
                if (partidaAndamento == null) {
                  outToClient.writeBytes("0|Cliente nao esta em uma partida em andamento|\n");
                  break;
                }

                if (!nomeCliente.equals(partidaAndamento.getJogadorTurno())) {
                  outToClient.writeBytes("0|Nao e o turno do jogador|\n");
                  break;
                }

                // Realiza o ataque
                if (!partidaAndamento.ataque(nomeCliente, posicaoX, posicaoY)) {
                  outToClient.writeBytes("0|Ataque invalido|\n");
                } else {
                  outToClient.writeBytes("1|Ataque realizado com sucesso|\n");

                  // Avança para o próximo turno
                  gameManager.proximoTurnoPartida(partidaAndamento);
                }
                break;
              }
              // SONAR <nome> <token> <posicaoX> <posicaoY>
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

                Cliente cliente = listaCliente.get(nomeCliente);

                if (!validarCliente(cliente, tokenCliente, outToClient, connectionSocket))
                  break;

                JogoPartida partidaAndamento = gameManager.encontrarPartidaAndamento(cliente.getIdPartida());
                if (partidaAndamento == null) {
                  outToClient.writeBytes("0|Cliente nao esta em uma partida em andamento|\n");
                  break;
                }

                if (!nomeCliente.equals(partidaAndamento.getJogadorTurno())) {
                  outToClient.writeBytes("0|Nao e o turno do jogador|\n");
                  break;
                }

                // Realiza a ação do dispositivo de proximidade
                if (!partidaAndamento.dispositivoProximidade(nomeCliente, posicaoX, posicaoY)) {
                  outToClient.writeBytes("0|Sonar invalido|\n");
                } else {
                  outToClient.writeBytes("1|Sonar utilizado com sucesso|\n");

                  // Avança para o próximo turno
                  gameManager.proximoTurnoPartida(partidaAndamento);
                }
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

                JogoPartida partidaAndamento = gameManager.encontrarPartidaAndamento(cliente.getIdPartida());
                if (partidaAndamento == null) {
                  outToClient.writeBytes("0|Cliente nao esta em uma partida em andamento|\n");
                  break;
                }

                if (!nomeCliente.equals(partidaAndamento.getJogadorTurno())) {
                  outToClient.writeBytes("0|Nao e o turno do jogador|\n");
                  break;
                }

                outToClient.writeBytes("1|Turno passado com sucesso|\n");

                // Avança para o próximo turno
                gameManager.proximoTurnoPartida(partidaAndamento);
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

                cliente.setJogadorDesafiado(null);

                // Caso o cliente esteja em uma partida em andamento
                JogoPartida partidaAndamento = gameManager.encontrarPartidaAndamento(cliente.getIdPartida());
                if (partidaAndamento != null) {
                  String turno = partidaAndamento.getJogadorTurno();
                  partidaAndamento.removerJogador(partidaAndamento.buscarJogadorPorNome(nomeCliente));
                  if (turno.equals(nomeCliente)) {
                    gameManager.proximoTurnoPartida(partidaAndamento);
                  }
                  cliente.setIdPartida(-1);
                  outToClient.writeBytes("1|Saiu da partida|\n");
                  break;
                }

                // Não está em uma partida em andamento, pega a partida do cliente
                Partida partida = gameManager.encontrarPartida(cliente.getIdPartida());

                if (partida != null) {
                  partida.removerCliente(cliente);
                  cliente.setIdPartida(-1);
                  outToClient.writeBytes("1|Saiu da partida|\n");
                } else {
                  outToClient.writeBytes("0|Partida inexistente|\n");
                }

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