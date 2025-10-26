package server;

import java.io.*;
import java.net.*;
import java.util.*;
import classes.*;
import java.util.concurrent.*;

public class Server {
  // Lista para armazenar os clientes conectados
  // Usando um HashMap para facilitar o acesso por nome ou ID
  // Só permite um cliente por nome
  // ChatGPT sugeriu esse HashMap
  static Map<String, Cliente> listaCliente = new ConcurrentHashMap<>();
  // Declarando lista de partidas
  static List<Partida> partidas = new ArrayList<>();
  static List<JogoPartida> jogoPartidas = new ArrayList<>();

  static int idAutoIncrement = Constants.NUMERO_PARTIDAS + 1;

  // Declarando thread para lidar com cada cliente conectado
  // Como lidar com threads visto em:
  // https://www.geeksforgeeks.org/java/java-multithreading-tutorial
  static class ClientHandler extends Thread {
    private Socket connectionSocket;

    // Salvando o socket de conexão do cliente para podermos utilizar no run()
    public ClientHandler(Socket socket) {
      this.connectionSocket = socket;
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
        Socket connectionSocket)
        throws IOException {
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

    // Método para encontrar uma partida pelo id, percorrendo a lista de partidas e
    // comparando o id
    static Partida encontrarPartida(int idPartida) {
      Partida partidaEscolhida = null;
      Iterator<Partida> it = partidas.iterator();
      while (it.hasNext()) {
        Partida partida = it.next();
        if (partida.getId() == idPartida) {
          partidaEscolhida = partida;
          break;
        }
      }
      return partidaEscolhida;
    }

    // Método para encontrar uma partida pelo id, percorrendo a lista de partidas e
    // comparando o id
    static JogoPartida encontrarPartidaAndamento(int idPartida) {
      JogoPartida partidaEscolhida = null;
      Iterator<JogoPartida> it = jogoPartidas.iterator();
      while (it.hasNext()) {
        JogoPartida jogoPartida = it.next();
        if (jogoPartida.getId() == idPartida) {
          partidaEscolhida = jogoPartida;
          break;
        }
      }
      return partidaEscolhida;
    }

    // Método para tentar iniciar a partida, toda vez que um cliente se conecta a
    // partida ele tentará iniciar a partida, se chama o método iniciarPartida da
    // Partida que apenas muda o estado de andamento, se conseguir, devemos
    // instanciar agora o JogoPartida que instancia os jogadores e inicia o jogo
    static void tentarIniciarPartida(Partida partida) throws IOException {
      if (partida.iniciarPartida()) {
        JogoPartida novaPartida = new JogoPartida(partida.getId(), partida.getClientes());
        iniciarJogoPartida(novaPartida);
      }
    }

    // Método para iniciar o jogo da partida, adicionando na lista de partidas e
    // notificando os jogadores sobre o inicio da partida e o turno
    static void iniciarJogoPartida(JogoPartida novaPartida) throws IOException {
      jogoPartidas.add(novaPartida);
      System.out.println("Partida iniciada: " + novaPartida.getId());

      novaPartida.imprimirPartida();
      // Devemos notificar os clientes dessa partida que a partida iniciou
      notificarJogadoresPartida(novaPartida, "Partida iniciada", novaPartida.getId() + "");

      String jogadorTurno = novaPartida.proximoTurno();
      System.out.println("Primeiro turno do jogador: " + jogadorTurno);
      notificarJogadoresPartida(novaPartida, "Turno do jogador", jogadorTurno);
    }

    // Método que passa para o próximo turno da partida e notifica os jogadores
    static void proximoTurnoPartida(JogoPartida jogoPartida) throws IOException {// Avança para o próximo turno
      // Avança para o próximo turno
      jogoPartida.proximoTurno();

      notificarJogadoresPartida(jogoPartida, "Turno do jogador", jogoPartida.getJogadorTurno());

      jogoPartida.imprimirPartida();
    }

    // Método que notifica todos os jogadores de uma partida com uma mensagem e um
    // valor passado
    static void notificarJogadoresPartida(JogoPartida jogoPartida, String mensagem, String valor) throws IOException {
      // Percorre todos os clientes da partida e envia a mensagem
      Iterator<Jogador> it = jogoPartida.getJogadores().iterator();
      while (it.hasNext()) {
        Jogador jogador = it.next();
        DataOutputStream outToClient = new DataOutputStream(jogador.getConnectionSocket().getOutputStream());
        outToClient.writeBytes("1|" + mensagem + "|" + valor + "\n");
      }
    }

    // Método para notificar o cliente desafiado sobre o desafio recebido
    static void notificarDesafio(Cliente clienteDesafiante, Cliente clienteDesafiado) throws IOException {
      DataOutputStream outToClient = new DataOutputStream(clienteDesafiado.getConnectionSocket().getOutputStream());
      outToClient.writeBytes("1|Desafio recebido|" + clienteDesafiante.getNome() + "\n");
    }

    static void finalizarPartida(JogoPartida jogoPartida, Jogador vencedor) throws IOException {
      // TODO
      notificarJogadoresPartida(jogoPartida, "Partida finalizada", vencedor.getNome());
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

                // Criando um token para o cliente, o cliente receberá esse token e deve
                // utilizá-lo para garantir sua identidade, poderíamos gerar um hash a partir do
                // nome e um salt mas um randomUUID já garante a unicidade necessária
                // String tokenCliente = UUID.randomUUID().toString();
                String tokenCliente = nomeCliente.toLowerCase();
                System.out.println("Cadastrando cliente: " + nomeCliente + " com token: " + tokenCliente);

                Cliente novoCliente = new Cliente(nomeCliente, tokenCliente, connectionSocket);

                // Tenta adicionar o cliente na lista, esse método retorna o item anterior
                // daquela chave (nomecliente), ou seja, se não existir item com aquela chave,
                // retorna null
                // ChatGPT sugeriu o HashMap do listaCliente e esse método putIfAbsent
                if (listaCliente.putIfAbsent(nomeCliente, novoCliente) != null) {
                  outToClient.writeBytes("0|Um cliente com esse nome ja existe|\n");
                } else {
                  outToClient.writeBytes("1|Cadastrado com sucesso|" + tokenCliente + "\n");
                }
                break;
              }
              // LISTARPARTIDAS
              case "LISTARPARTIDAS": {
                String partidasServidor = "";
                Iterator<Partida> it = Server.partidas.iterator();
                while (it.hasNext()) {
                  Partida partida = it.next();
                  partidasServidor += partida.getInfo();
                  partidasServidor += ";";
                }
                outToClient.writeBytes("1|Partidas públicas|" + partidasServidor + "\n");
                break;
              }
              // LISTARJOGADORES
              case "LISTARJOGADORES": {
                String clientesServidor = "";
                Iterator<Cliente> it = listaCliente.values().iterator();
                while (it.hasNext()) {
                  Cliente cliente = it.next();
                  clientesServidor += cliente.getNome();
                  clientesServidor += ";";
                }
                outToClient.writeBytes("1|Jogadores conectados|" + clientesServidor + "\n");
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
                Partida partidaEscolhida = encontrarPartida(idPartida);

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
                  JogoPartida partidaAndamento = encontrarPartidaAndamento(idPartidaCliente);
                  if (partidaAndamento != null) {
                    outToClient.writeBytes("0|Nao e possivel trocar de partida durante um jogo|\n");
                    break;
                  }
                  Partida partidaAnterior = encontrarPartida(idPartidaCliente);
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
                tentarIniciarPartida(partidaEscolhida);
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
                JogoPartida partidaAndamento = encontrarPartidaAndamento(idPartidaDesafiante);
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
                  notificarDesafio(clienteDesafiante, clienteDesafiado);

                  clienteDesafiante.setJogadorDesafiado(nomeDesafiado);

                  outToClient.writeBytes("1|Desafio enviado com sucesso|\n");
                  break;
                }

                // Os dois jogadores se desafiaram, vamos iniciar a partida diretamente

                // Criando a lista de clientes para a nova partida
                List<Cliente> clientes = new ArrayList<>();
                clientes.add(clienteDesafiante);
                clientes.add(clienteDesafiado);

                int idPartida = idAutoIncrement;
                idAutoIncrement++;

                clienteDesafiante.setIdPartida(idPartida);
                clienteDesafiado.setIdPartida(idPartida);

                JogoPartida novaPartida = new JogoPartida(idPartida, clientes);

                iniciarJogoPartida(novaPartida);
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

                // TODO
                JogoPartida partidaAndamento = encontrarPartidaAndamento(cliente.getIdPartida());
                if (partidaAndamento != null) {
                  // partidaAndamento.removerJogador(cliente);
                  cliente.setIdPartida(-1);
                  outToClient.writeBytes("1|Saiu da partida|\n");
                  break;
                }

                // Pega a partida do cliente
                Partida partida = encontrarPartida(cliente.getIdPartida());

                if (partida != null) {
                  partida.removerCliente(cliente);
                  cliente.setIdPartida(-1);
                  outToClient.writeBytes("1|Saiu da partida|\n");
                } else {
                  outToClient.writeBytes("0|Partida inexistente|\n");
                }

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

                JogoPartida partidaAndamento = encontrarPartidaAndamento(cliente.getIdPartida());
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

                  proximoTurnoPartida(partidaAndamento);
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

                JogoPartida partidaAndamento = encontrarPartidaAndamento(cliente.getIdPartida());
                if (partidaAndamento == null) {
                  outToClient.writeBytes("0|Cliente nao esta em uma partida em andamento|\n");
                  break;
                }

                if (!nomeCliente.equals(partidaAndamento.getJogadorTurno())) {
                  outToClient.writeBytes("0|Nao e o turno do jogador|\n");
                  break;
                }

                // Realiza o ataque
                if (!partidaAndamento.atacar(nomeCliente, posicaoX, posicaoY)) {
                  outToClient.writeBytes("0|Ataque invalido|\n");
                } else {
                  outToClient.writeBytes("1|Ataque realizado com sucesso|\n");

                  // Avança para o próximo turno
                  proximoTurnoPartida(partidaAndamento);
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

                JogoPartida partidaAndamento = encontrarPartidaAndamento(cliente.getIdPartida());
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
                proximoTurnoPartida(partidaAndamento);
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
      // Loop para criar partidas
      for (int i = 0; i < Constants.NUMERO_PARTIDAS; i++) {
        partidas.add(new Partida(i + 1));
      }

      while (true) {
        // Aceite todas as conexões de entrada
        Socket connectionSocket = welcomeSocket.accept();

        System.out.println("Cliente conectado: " + connectionSocket.getInetAddress().getHostAddress() + ":"
            + connectionSocket.getPort());

        // Declarando um ClientHandler (thread) para cada conexão
        ClientHandler clientHandler = new ClientHandler(connectionSocket);
        clientHandler.start();
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      welcomeSocket.close();
    }
  }
}