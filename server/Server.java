package server;

import java.io.*;
import java.net.*;
import java.util.*;
import classes.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {
  // Lista para armazenar os clientes conectados
  // Usando um HashMap para facilitar o acesso por nome ou ID
  // Só permite um cliente por nome
  // ChatGPT sugeriu esse HashMap
  static Map<String, Cliente> listaCliente = new ConcurrentHashMap<>();
  // Declarando lista de partidas
  static List<Partida> partidas = new ArrayList<>();
  // Lista de partidas em andamento deve ser segura para threads
  static List<JogoPartida> jogoPartidas = Collections.synchronizedList(new ArrayList<>());

  // Gerador de IDs thread-safe
  static AtomicInteger idAutoIncrement = new AtomicInteger(Constants.NUMERO_PARTIDAS + 1);

  // Executor single-thread: thread principal do servidor que processa a lógica
  static ExecutorService serverExecutor = Executors.newSingleThreadExecutor();

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
      synchronized (jogoPartidas) {
        Iterator<JogoPartida> it = jogoPartidas.iterator();
        while (it.hasNext()) {
          JogoPartida jogoPartida = it.next();
          if (jogoPartida.getId() == idPartida) {
            partidaEscolhida = jogoPartida;
            break;
          }
        }
      }
      return partidaEscolhida;
    }

    // Método para tentar iniciar a partida, toda vez que um cliente se conecta a
    // partida ele tentará iniciar a partida, se chama o método iniciarPartida da
    // Partida que apenas muda o estado de andamento, se conseguir, devemos
    // instanciar agora o JogoPartida que instancia os jogadores e inicia o jogo
    static void tentarIniciarPartida(Partida partida) throws IOException {
      synchronized (partida) {
        if (partida.iniciarPartida()) {
          JogoPartida novaPartida = new JogoPartida(partida.getId(), partida.getClientes(), partida);
          iniciarJogoPartida(novaPartida);
        }
      }
    }

    // Método para iniciar o jogo da partida, adicionando na lista de partidas e
    // notificando os jogadores sobre o inicio da partida e o turno
    static void iniciarJogoPartida(JogoPartida novaPartida) throws IOException {
      jogoPartidas.add(novaPartida);
      // Processa inicialização e notificações na thread do servidor
      serverExecutor.execute(() -> {
        try {
          synchronized (novaPartida) {
            System.out.println("Partida iniciada: " + novaPartida.getId());
            novaPartida.imprimirPartida();
            // Devemos notificar os clientes dessa partida que a partida iniciou
            notificarJogadoresPartida(novaPartida, "Partida iniciada", novaPartida.getId() + "");

            String jogadorTurno = novaPartida.proximoTurno();
            notificarJogadoresPartida(novaPartida, "Turno do jogador", jogadorTurno);
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      });
    }

    // Método que passa para o próximo turno da partida e determina os
    // acontecimentos da partida para notificar os jogadores
    static void proximoTurnoPartida(JogoPartida jogoPartida) throws IOException {
      synchronized (jogoPartida) {
        lidarJogadoresAtaques(jogoPartida);

        if (verificarFimJogoPartida(jogoPartida)) {
          return;
        }

        notificarJogadoresDetectados(jogoPartida);

        // Avança para o próximo turno
        jogoPartida.proximoTurno();

        notificarJogadoresPartida(jogoPartida, "Turno do jogador", jogoPartida.getJogadorTurno());

        jogoPartida.imprimirPartida();
      }
    }

    // Método que notifica todos os jogadores de uma partida com uma mensagem e um
    // valor passado
    static void notificarJogadoresPartida(JogoPartida jogoPartida, String mensagem, String valor) throws IOException {
      // Faz um snapshot da lista para evitar problemas de concorrência durante
      // iteração
      List<Jogador> jogadoresSnapshot = new ArrayList<>(jogoPartida.getTodosJogadores());
      for (Jogador jogador : jogadoresSnapshot) {
        notificarJogadorPartida(jogador, mensagem, valor);
      }
    }

    // Método que notifica um único jogador de uma partida com uma mensagem e um
    // valor passado
    static void notificarJogadorPartida(Cliente cliente, String mensagem, String valor) throws IOException {
      DataOutputStream outToClient = new DataOutputStream(cliente.getConnectionSocket().getOutputStream());
      synchronized (cliente) {
        outToClient.writeBytes("1|" + mensagem + "|" + valor + "\n");
      }
    }

    // Utilitário para localizar um cliente pelo socket atual (para sincronizar
    // escritas)
    static Cliente encontrarClientePorSocket(Socket socket) {
      for (Cliente c : listaCliente.values()) {
        if (c.getConnectionSocket() == socket) {
          return c;
        }
      }
      return null;
    }

    // Utilitário para escrever resposta com sincronização por cliente quando
    // possível
    static void safeWrite(DataOutputStream out, Socket socket, Cliente cliente, String message) throws IOException {
      if (cliente == null) {
        cliente = encontrarClientePorSocket(socket);
      }
      if (cliente != null) {
        synchronized (cliente) {
          out.writeBytes(message);
        }
      } else {
        out.writeBytes(message);
      }
    }

    // Método que notifica o dono do dispositivo sobre os jogadores detectados por
    // seus dispositivos de proximidade
    static void notificarJogadoresDetectados(JogoPartida jogoPartida) throws IOException {
      // Loop de todos os dispositivos
      Iterator<DispositivoProximidade> itDispositivos = jogoPartida.getDispositivos().iterator();
      while (itDispositivos.hasNext()) {
        String todosJogadoresDetectados = "";
        DispositivoProximidade dispositivo = itDispositivos.next();

        // Pega a lista de todos jogadores próximos do dispositivo
        List<Jogador> jogadoresDetectados = jogoPartida.detectarJogadores(dispositivo);
        Iterator<Jogador> itJogadoresDetectados = jogadoresDetectados.iterator();
        while (itJogadoresDetectados.hasNext()) {
          Jogador jogadorDetectado = itJogadoresDetectados.next();
          // Notifica o jogador dono do dispositivo sobre o jogador detectado
          todosJogadoresDetectados += jogadorDetectado.getNome() + ",";
        }
        if (!todosJogadoresDetectados.isEmpty()) {
          notificarJogadorPartida(dispositivo.getJogadorDono(),
              "Jogador detectado pelo dispositivo " + dispositivo.getNum(), todosJogadoresDetectados);
        }
      }
    }

    // Método que notifica o dono do dispositivo sobre os jogadores acertados por
    // seus misseis
    static void lidarJogadoresAtaques(JogoPartida jogoPartida) throws IOException {
      // Loop de todos os misseis
      Iterator<Missil> itMisseis = jogoPartida.getMisseis().iterator();
      while (itMisseis.hasNext()) {
        String todosJogadoresAcertados = "";
        Missil missil = itMisseis.next();

        // Pega a lista de todos jogadores próximos do missil
        List<Jogador> jogadoresDetectados = jogoPartida.detectarJogadores(missil);
        Iterator<Jogador> itJogadoresDetectados = jogadoresDetectados.iterator();
        while (itJogadoresDetectados.hasNext()) {
          Jogador jogadorDetectado = itJogadoresDetectados.next();
          // Notifica o jogador dono do missil sobre o jogador detectado
          todosJogadoresAcertados += jogadorDetectado.getNome() + ",";
        }
        if (!todosJogadoresAcertados.isEmpty()) {
          notificarJogadorPartida(missil.getJogadorDono(),
              "Jogador acertados pelo missil", todosJogadoresAcertados);
          // Iterando sobre os jogadores acertados para matá-los
          Iterator<Jogador> itJogadoresAcertados = jogadoresDetectados.iterator();
          while (itJogadoresAcertados.hasNext()) {
            Jogador jogadorAcertado = itJogadoresAcertados.next();
            jogoPartida.matarJogador(jogadorAcertado);
            notificarJogadorPartida(jogadorAcertado,
                "Você foi acertado por um missil", missil.getJogadorDono().getNome());
          }
        }
      }
    }

    // Método que roda cada turno, se a partida terminou, notifica os jogadores e
    // chama outra função para finalizar a partida e remover da lista
    static Boolean verificarFimJogoPartida(JogoPartida jogoPartida) throws IOException {
      Jogador vencedor = jogoPartida.verificarFimPartida();
      if (vencedor != null) {
        notificarJogadorPartida(vencedor, "Voce e o vencedor!", "");
        notificarJogadoresPartida(jogoPartida, "Partida finalizada", vencedor.getNome());
        finalizarJogoPartida(jogoPartida);
        return true;
      }
      return false;
    }

    // Método que seta o estado da partida como finalizada e remove da lista de
    // partidas em andamento
    static void finalizarJogoPartida(JogoPartida jogoPartida) {
      System.out.println("Partida finalizada: " + jogoPartida.getId());
      // Vamos pegar todos os jogadores para definir eles com nenhuma partida
      List<Jogador> todosJogadores = jogoPartida.getTodosJogadores();
      Iterator<Jogador> it = todosJogadores.iterator();
      while (it.hasNext()) {
        Jogador jogador = it.next();
        Cliente cliente = listaCliente.get(jogador.getNome());
        cliente.setJogadorDesafiado(null);
        cliente.setIdPartida(-1);
      }
      jogoPartida.finalizarPartida();
      jogoPartidas.remove(jogoPartida);
    }

    public void run() {
      try {
        // Código tirado dos slides
        BufferedReader inFromClient = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
        DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
        String sentence;
        while ((sentence = inFromClient.readLine()) != null) {
          final String sentenceCopy = sentence;
          // Processa o comando na thread principal do servidor
          serverExecutor.execute(() -> {
            try {
              // Separando a sentença nas palavras
              String splitedSentence[] = sentenceCopy.split(" ");
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
                    safeWrite(outToClient, connectionSocket, null, "0|Um cliente com esse nome ja existe|\n");
                  } else {
                    safeWrite(outToClient, connectionSocket, null, "1|Cadastrado com sucesso|" + tokenCliente + "\n");
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
                  safeWrite(outToClient, connectionSocket, null, "1|Partidas públicas|" + partidasServidor + "\n");
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
                  safeWrite(outToClient, connectionSocket, null, "1|Jogadores conectados|" + clientesServidor + "\n");
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
                    safeWrite(outToClient, connectionSocket, cliente, "0|ID da partida invalido|\n");
                    break;
                  }

                  // Percorre todas partidas e verifica se o id
                  Partida partidaEscolhida = encontrarPartida(idPartida);

                  // Partida não encontrada
                  if (partidaEscolhida == null) {
                    safeWrite(outToClient, connectionSocket, cliente, "0|Partida inexistente|\n");
                    break;
                  }

                  // Se a partida estiver lotada, não pode entrar
                  if (partidaEscolhida.partidaLotada() == true) {
                    safeWrite(outToClient, connectionSocket, cliente, "0|Partida lotada|\n");
                    break;
                  }

                  // Pega o id da partida armazenada em cliente
                  int idPartidaCliente = cliente.getIdPartida();

                  // Se o cliente já está na partida escolhida, não faz nada
                  if (idPartidaCliente == idPartida) {
                    safeWrite(outToClient, connectionSocket, cliente, "1|Cliente ja esta nessa partida|\n");
                    break;
                  }

                  // Se o cliente já estava em alguma partida
                  if (idPartidaCliente != -1) {
                    JogoPartida partidaAndamento = encontrarPartidaAndamento(idPartidaCliente);
                    if (partidaAndamento != null) {
                      safeWrite(outToClient, connectionSocket, cliente,
                          "0|Nao e possivel trocar de partida durante um jogo|\n");
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

                  safeWrite(outToClient, connectionSocket, cliente, "1|Entrou na partida com sucesso|\n");

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
                    safeWrite(outToClient, connectionSocket, clienteDesafiante,
                        "0|Nao e possivel desafiar durante uma partida em andamento|\n");
                    break;
                  }

                  Cliente clienteDesafiado = listaCliente.get(nomeDesafiado);
                  if (clienteDesafiado == null) {
                    safeWrite(outToClient, connectionSocket, clienteDesafiante,
                        "0|Cliente desafiado nao encontrado|\n");
                    break;
                  }

                  // Se o destinatario não desafiou o remetente, então vamos apenas enviar o nosso
                  // desafio para ele
                  if (!nomeDesafiante.equals(clienteDesafiado.getJogadorDesafiado())) {
                    notificarJogadorPartida(clienteDesafiado, "Desafio recebido", nomeDesafiante);

                    clienteDesafiante.setJogadorDesafiado(nomeDesafiado);

                    safeWrite(outToClient, connectionSocket, clienteDesafiante, "1|Desafio enviado com sucesso|\n");
                    break;
                  }

                  // Os dois jogadores se desafiaram, vamos iniciar a partida diretamente

                  // Criando a lista de clientes para a nova partida
                  List<Cliente> clientes = new ArrayList<>();
                  clientes.add(clienteDesafiante);
                  clientes.add(clienteDesafiado);

                  int idPartida = idAutoIncrement.getAndIncrement();

                  clienteDesafiante.setIdPartida(idPartida);
                  clienteDesafiado.setIdPartida(idPartida);

                  JogoPartida novaPartida = new JogoPartida(idPartida, clientes, null);

                  iniciarJogoPartida(novaPartida);
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
                    safeWrite(outToClient, connectionSocket, cliente,
                        "0|Cliente nao esta em uma partida em andamento|\n");
                    break;
                  }

                  if (!nomeCliente.equals(partidaAndamento.getJogadorTurno())) {
                    safeWrite(outToClient, connectionSocket, cliente, "0|Nao e o turno do jogador|\n");
                    break;
                  }

                  if (!partidaAndamento.movimento(nomeCliente, posicaoX, posicaoY)) {
                    safeWrite(outToClient, connectionSocket, cliente, "0|Movimento invalido|\n");
                  } else {
                    safeWrite(outToClient, connectionSocket, cliente, "1|Movimento realizado com sucesso|\n");

                    // Já estamos na thread do servidor; pode avançar o turno diretamente
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
                    safeWrite(outToClient, connectionSocket, cliente,
                        "0|Cliente nao esta em uma partida em andamento|\n");
                    break;
                  }

                  if (!nomeCliente.equals(partidaAndamento.getJogadorTurno())) {
                    safeWrite(outToClient, connectionSocket, cliente, "0|Nao e o turno do jogador|\n");
                    break;
                  }

                  // Realiza o ataque
                  if (!partidaAndamento.ataque(nomeCliente, posicaoX, posicaoY)) {
                    safeWrite(outToClient, connectionSocket, cliente, "0|Ataque invalido|\n");
                  } else {
                    safeWrite(outToClient, connectionSocket, cliente, "1|Ataque realizado com sucesso|\n");

                    // Já estamos na thread do servidor; pode avançar o turno diretamente
                    proximoTurnoPartida(partidaAndamento);
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

                  JogoPartida partidaAndamento = encontrarPartidaAndamento(cliente.getIdPartida());
                  if (partidaAndamento == null) {
                    safeWrite(outToClient, connectionSocket, cliente,
                        "0|Cliente nao esta em uma partida em andamento|\n");
                    break;
                  }

                  if (!nomeCliente.equals(partidaAndamento.getJogadorTurno())) {
                    safeWrite(outToClient, connectionSocket, cliente, "0|Nao e o turno do jogador|\n");
                    break;
                  }

                  // Realiza a ação do dispositivo de proximidade
                  if (!partidaAndamento.dispositivoProximidade(nomeCliente, posicaoX, posicaoY)) {
                    safeWrite(outToClient, connectionSocket, cliente, "0|Sonar invalido|\n");
                  } else {
                    safeWrite(outToClient, connectionSocket, cliente, "1|Sonar utilizado com sucesso|\n");

                    // Já estamos na thread do servidor; pode avançar o turno diretamente
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
                    safeWrite(outToClient, connectionSocket, cliente,
                        "0|Cliente nao esta em uma partida em andamento|\n");
                    break;
                  }

                  if (!nomeCliente.equals(partidaAndamento.getJogadorTurno())) {
                    safeWrite(outToClient, connectionSocket, cliente, "0|Nao e o turno do jogador|\n");
                    break;
                  }

                  safeWrite(outToClient, connectionSocket, cliente, "1|Turno passado com sucesso|\n");

                  // Já estamos na thread do servidor; pode avançar o turno diretamente
                  proximoTurnoPartida(partidaAndamento);
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
                  JogoPartida partidaAndamento = encontrarPartidaAndamento(cliente.getIdPartida());
                  if (partidaAndamento != null) {
                    synchronized (partidaAndamento) {
                      partidaAndamento.removerJogador(partidaAndamento.buscarJogadorPorNome(nomeCliente));
                    }
                    if (nomeCliente.equals(partidaAndamento.getJogadorTurno())) {
                      proximoTurnoPartida(partidaAndamento);
                    }
                    cliente.setIdPartida(-1);
                    safeWrite(outToClient, connectionSocket, cliente, "1|Saiu da partida|\n");
                    break;
                  }

                  // Não está em uma partida em andamento, pega a partida do cliente
                  Partida partida = encontrarPartida(cliente.getIdPartida());

                  if (partida != null) {
                    partida.removerCliente(cliente);
                    cliente.setIdPartida(-1);
                    safeWrite(outToClient, connectionSocket, cliente, "1|Saiu da partida|\n");
                  } else {
                    safeWrite(outToClient, connectionSocket, cliente, "0|Partida inexistente|\n");
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
                  safeWrite(outToClient, connectionSocket, cliente, "1|Cliente desconectado com sucesso|\n");

                  // Encerra a conexão: o loop de leitura do cliente terminará quando o socket for
                  // fechado
                  try {
                    connectionSocket.close();
                  } catch (IOException ioe) {
                    ioe.printStackTrace();
                  }
                  break;
                }
                default:
                  safeWrite(outToClient, connectionSocket, null, "0|Comando desconhecido|\n");
                  break;
              }
            } catch (Exception ex) {
              ex.printStackTrace();
            }
          });
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