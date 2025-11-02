package server;

import java.util.*;
import classes.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class GameManager {
  private int idAutoIncrement;

  // Timer de turno por partida
  // Esses dois foram sugeridos pelo Agente Copilot do VSCode ao pedir como se
  // fazia um turno de 15 segundos
  private final ScheduledExecutorService turnScheduler = Executors.newScheduledThreadPool(2);
  private final ConcurrentMap<Integer, ScheduledFuture<?>> turnTimers = new ConcurrentHashMap<>();
  // Keepalive por cliente
  private final ScheduledExecutorService keepAliveScheduler = Executors.newScheduledThreadPool(4);
  private final ConcurrentMap<String, ScheduledFuture<?>> keepAliveTimers = new ConcurrentHashMap<>();

  // Lista para armazenar os clientes conectados
  // Usando um HashMap para facilitar o acesso por nome ou ID
  // Só permite um cliente por nome
  // ChatGPT sugeriu esse HashMap
  private Map<String, Cliente> listaCliente = Collections.synchronizedMap(new HashMap<>());
  // Declarando lista de partidas
  // ChatGPT recomendou usar Collections.synchronizedList para evitar condições de
  // corrida
  private List<Partida> partidas = Collections.synchronizedList(new ArrayList<>());
  private List<JogoPartida> jogoPartidas = Collections.synchronizedList(new ArrayList<>());

  public GameManager() {
    this.idAutoIncrement = Constants.NUMERO_PARTIDAS + 1;
  }

  public synchronized int proximoIdAutoIncrement() {
    return idAutoIncrement++;
  }

  public synchronized void resetarIdAutoIncrement() {
    idAutoIncrement = Constants.NUMERO_PARTIDAS + 1;
  }

  public Map<String, Cliente> getListaCliente() {
    return this.listaCliente;
  }

  public void criarPartidas() {
    // Loop para criar partidas
    for (int i = 0; i < Constants.NUMERO_PARTIDAS; i++) {
      partidas.add(new Partida(i + 1));
    }
  }

  // Método para encontrar uma partida pelo id, percorrendo a lista de partidas e
  // comparando o id
  public Partida encontrarPartida(int idPartida) {
    Partida partidaEscolhida = null;
    synchronized (partidas) {
      Iterator<Partida> it = partidas.iterator();
      while (it.hasNext()) {
        Partida partida = it.next();
        if (partida.getId() == idPartida) {
          partidaEscolhida = partida;
          break;
        }
      }
    }
    return partidaEscolhida;
  }

  // Método para encontrar uma partida pelo id, percorrendo a lista de partidas e
  // comparando o id
  public JogoPartida encontrarPartidaAndamento(int idPartida) {
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

  // Método que notifica um único jogador de uma partida com uma mensagem e um
  // valor passado
  public void notificarJogadorPartida(Cliente cliente, String tipo, String codigo, String mensagem, String valor) {
    // Usa método centralizado no Cliente para enviar com segurança
    cliente.enviarLinha(tipo, codigo, mensagem, valor);
  }

  // Método que notifica todos os jogadores de uma partida com uma mensagem e um
  // valor passado
  public void notificarJogadoresPartida(JogoPartida jogoPartida, String tipo, String codigo, String mensagem,
      String valor) {
    List<Jogador> jogadoresSnapshot;
    synchronized (jogoPartida) {
      jogadoresSnapshot = new ArrayList<>(jogoPartida.getTodosJogadores());
    }
    // Percorre todos os clientes da partida e envia a mensagem fora do lock da
    // partida
    Iterator<Jogador> it = jogadoresSnapshot.iterator();
    while (it.hasNext()) {
      Jogador jogador = it.next();
      notificarJogadorPartida(jogador, tipo, codigo, mensagem, valor);
    }
  }

  // Método que notifica todos os clientes conectados
  private void notificarTodos(String tipo, String codigo, String mensagem, String valor) {
    // Percorre todos os clientes da e envia a mensagem
    List<Cliente> clientesSnapshot;
    synchronized (listaCliente) {
      clientesSnapshot = new ArrayList<>(listaCliente.values());
    }
    Iterator<Cliente> it = clientesSnapshot.iterator();
    while (it.hasNext()) {
      Cliente clienteAtual = it.next();
      JogoPartida partidaAndamento = encontrarPartidaAndamento(clienteAtual.getIdPartida());
      if (partidaAndamento != null && Constants.CHAT_GLOBAL_SOMENTE_LOBBY) {
        continue;
      }
      clienteAtual.enviarLinha(tipo, codigo, mensagem, valor);
    }
  }

  // Método para tentar iniciar a partida, toda vez que um cliente se conecta a
  // partida ele tentará iniciar a partida, se chama o método iniciarPartida da
  // Partida que apenas muda o estado de andamento, se conseguir, devemos
  // instanciar agora o JogoPartida que instancia os jogadores e inicia o jogo
  public void tentarIniciarPartida(Partida partida) {
    // Variavel para podermos separar os blocos synchronized
    boolean partidaIniciou = false;
    synchronized (partida) {
      // garante que só uma thread pode tentar iniciar essa partida ao mesmo tempo
      partidaIniciou = partida.iniciarPartida();
    }
    if (partidaIniciou) {
      JogoPartida novaPartida = new JogoPartida(partida.getId(), partida.getClientes(), partida);
      iniciarJogoPartida(novaPartida);
    }
  }

  // Método para iniciar o jogo da partida, adicionando na lista de partidas e
  // notificando os jogadores sobre o inicio da partida e o turno
  public void iniciarJogoPartida(JogoPartida novaPartida) {
    synchronized (jogoPartidas) {
      jogoPartidas.add(novaPartida);
    }

    String todosJogadores = "";
    System.out.println("Partida reservada: " + novaPartida.getId());

    // Devemos notificar os clientes dessa partida que a partida foi reservada
    // Vamos informar também suas posições
    Iterator<Jogador> itJogadores = novaPartida.getJogadores().iterator();

    while (itJogadores.hasNext()) {
      Jogador jogador = itJogadores.next();
      if (!todosJogadores.isEmpty()) {
        todosJogadores += Constants.SEPARADORITEM;
      }
      todosJogadores += "nome:" + jogador.getNome();
    }
    String valorTodos = "jogadores:{" + todosJogadores + "}";
    itJogadores = novaPartida.getJogadores().iterator();
    while (itJogadores.hasNext()) {
      Jogador jogador = itJogadores.next();
      Posicao posicao = jogador.getPosicao();
      String valor = "x:" + posicao.getX() + Constants.SEPARADORATRIBUTO + "y:" + posicao.getY()
          + Constants.SEPARADORATRIBUTO + valorTodos;
      notificarJogadorPartida(jogador, Constants.TIPORESERVADOPARTIDA, "200", "Partida reservada", valor);
    }
  }

  // Método que passa para o próximo turno da partida e determina os
  // acontecimentos da partida para notificar os jogadores
  public void proximoTurnoPartida(JogoPartida jogoPartida) {
    // Cancela o timer do turno atual (para evitar disparo durante o processamento)
    cancelarTimerTurno(jogoPartida);
    lidarJogadoresAtaques(jogoPartida);

    if (verificarFimJogoPartida(jogoPartida)) {
      return;
    }

    lidarJogadoresDetectados(jogoPartida);

    proximoTurno(jogoPartida);

    jogoPartida.imprimirPartida();
  }

  // Método para avançar o turno e notificar os jogadores
  public void proximoTurno(JogoPartida jogoPartida) {
    // Avança para o próximo turno e captura o jogador do turno de forma atômica
    String jogadorTurno;
    synchronized (jogoPartida) {
      jogadorTurno = "turno:" + jogoPartida.proximoTurno();
    }

    notificarJogadoresPartida(jogoPartida, Constants.TIPOTURNO, "200", "Turno do jogador", jogadorTurno);
    // Agenda o timer para o novo turno
    agendarTimerTurno(jogoPartida);
  }

  // Método que notifica o dono do dispositivo sobre os jogadores acertados por
  // seus misseis
  public void lidarJogadoresAtaques(JogoPartida jogoPartida) {
    synchronized (jogoPartida) {
      List<Missil> misseis = jogoPartida.getMisseis();
      // Loop de todos os misseis
      ListIterator<Missil> itMisseis = misseis.listIterator(misseis.size());
      while (itMisseis.hasPrevious()) {
        String todosJogadoresAcertados = "";
        Missil missil = itMisseis.previous();

        int numTurnoAtual = jogoPartida.getNumTurno();
        int numTurnoMissil = missil.getNum();

        if (numTurnoMissil < numTurnoAtual) {
          break;
        }

        if (numTurnoMissil != numTurnoAtual) {
          continue;
        }

        // Pega a lista de todos jogadores próximos do missil
        List<Jogador> jogadoresDetectados = jogoPartida.detectarJogadores(missil);
        Iterator<Jogador> itJogadoresDetectados = jogadoresDetectados.iterator();
        while (itJogadoresDetectados.hasNext()) {
          Jogador jogadorDetectado = itJogadoresDetectados.next();
          // Notifica o jogador dono do missil sobre o jogador detectado
          Posicao posicao = jogadorDetectado.getPosicao();
          if (!todosJogadoresAcertados.isEmpty()) {
            todosJogadoresAcertados += Constants.SEPARADORITEM;
          }
          todosJogadoresAcertados += "nome:" + jogadorDetectado.getNome() + Constants.SEPARADORATRIBUTO + "x:"
              + posicao.getX() + Constants.SEPARADORATRIBUTO + "y:" + posicao.getY();
        }
        if (!todosJogadoresAcertados.isEmpty()) {
          String valor = "jogadores:{" + todosJogadoresAcertados + "}";
          notificarJogadorPartida(missil.getJogadorDono(), Constants.TIPOACERTO, "200",
              "Jogadores acertados pelo missil", valor);
          // Iterando sobre os jogadores acertados para matá-los
          Iterator<Jogador> itJogadoresAcertados = jogadoresDetectados.iterator();
          while (itJogadoresAcertados.hasNext()) {
            Jogador jogadorAcertado = itJogadoresAcertados.next();
            jogoPartida.matarJogador(jogadorAcertado);
            notificarJogadorPartida(jogadorAcertado, Constants.TIPOMORTE, "200",
                "Voce foi acertado por um missil", "dono:" + missil.getJogadorDono().getNome());
          }
        }
      }
    }
  }

  // Método que notifica o dono do dispositivo sobre os jogadores detectados por
  // seus dispositivos de proximidade
  public void lidarJogadoresDetectados(JogoPartida jogoPartida) {
    synchronized (jogoPartida) {
      // Loop de todos os dispositivos
      Iterator<DispositivoProximidade> itDispositivos = jogoPartida.getDispositivos().iterator();
      while (itDispositivos.hasNext()) {
        String todosJogadoresDetectados = "";
        DispositivoProximidade dispositivo = itDispositivos.next();
        int xDispositivo = dispositivo.getPosicao().getX();
        int yDispositivo = dispositivo.getPosicao().getY();

        // Pega a lista de todos jogadores próximos do dispositivo
        List<Jogador> jogadoresDetectados = jogoPartida.detectarJogadores(dispositivo);
        Iterator<Jogador> itJogadoresDetectados = jogadoresDetectados.iterator();
        while (itJogadoresDetectados.hasNext()) {
          Jogador jogadorDetectado = itJogadoresDetectados.next();
          if (!todosJogadoresDetectados.isEmpty()) {
            todosJogadoresDetectados += Constants.SEPARADORITEM;
          }
          // Notifica o jogador dono do dispositivo sobre o jogador detectado
          todosJogadoresDetectados += "nome:" + jogadorDetectado.getNome();
        }
        if (!todosJogadoresDetectados.isEmpty()) {
          String valor = "num:" + dispositivo.getNum() + Constants.SEPARADORATRIBUTO + "x:" + xDispositivo
              + Constants.SEPARADORATRIBUTO + "y:" + yDispositivo + Constants.SEPARADORATRIBUTO + "jogadores:{"
              + todosJogadoresDetectados + "}";
          notificarJogadorPartida(dispositivo.getJogadorDono(), Constants.TIPODETECTADO, "200",
              "Jogadores detectados pelo dispositivo " + dispositivo.getNum(), valor);
        }
      }
    }
  }

  // Método que roda cada turno, se a partida terminou, notifica os jogadores e
  // chama outra função para finalizar a partida e remover da lista
  public boolean verificarFimJogoPartida(JogoPartida jogoPartida) {
    Jogador vencedor = jogoPartida.verificarFimPartida();
    if (vencedor != null) {
      notificarJogadorPartida(vencedor, Constants.TIPOVITORIA, "200", "Voce e o vencedor!", "");
      notificarJogadoresPartida(jogoPartida, Constants.TIPOFIMPARTIDA, "200", "Partida finalizada",
          "vencedor:" + vencedor.getNome());
      finalizarJogoPartida(jogoPartida);
      return true;
    }
    return false;
  }

  // Método que seta o estado da partida como finalizada e remove da lista de
  // partidas em andamento
  public void finalizarJogoPartida(JogoPartida jogoPartida) {
    jogoPartida.imprimirPartida();
    System.out.println("Partida finalizada: " + jogoPartida.getId());
    // Atualiza estado dos jogadores e finaliza a partida protegendo o estado da
    // partida
    synchronized (jogoPartida) {
      List<Jogador> todosJogadores = jogoPartida.getTodosJogadores();
      Iterator<Jogador> it = todosJogadores.iterator();
      while (it.hasNext()) {
        Jogador jogador = it.next();
        Cliente cliente = listaCliente.get(jogador.getNome());
        if (cliente != null) {
          cliente.setJogadorDesafiado(null);
          cliente.setIdPartida(-1);
        }
      }
      jogoPartida.finalizarPartida();
    }
    // Cancela e remove o timer dessa partida
    cancelarTimerTurno(jogoPartida);
    jogoPartidas.remove(jogoPartida);

    // Se a partida que finalizou era uma pública notifica a todos
    if(jogoPartida.getId() <= Constants.NUMERO_PARTIDAS) {
      notificarTodos(Constants.TIPOLISTARPARTIDAS, "200", "Partidas publicas", gerarListaPartidas());
    }
  }

  // Método para enviar uma linha para o cliente evitando erros de conexão
  private void enviarLinha(DataOutputStream outToClient, String tipo, String codigo, String mensagem, String valor) {
    if (outToClient == null)
      return;
    try {
      String spr = Constants.SEPARADOR;
      String linha = tipo + spr + codigo + spr + mensagem + spr + valor;
      outToClient.writeBytes(linha + "\n");
      outToClient.flush();
    } catch (IOException e) {
      // Iremos ignorar, cliente pode ter fechado a conexão
    }
  }

  // Método para gerar a lista de jogadores conectados
  private String gerarListaJogadores() {
    StringBuilder jogadoresServidor = new StringBuilder();
    List<Cliente> clientesSnapshot;

    synchronized (listaCliente) {
      clientesSnapshot = new ArrayList<>(listaCliente.values());
    }

    Iterator<Cliente> it = clientesSnapshot.iterator();
    while (it.hasNext()) {
      Cliente cliente = it.next();
      if (jogadoresServidor.isEmpty()) {
        jogadoresServidor.append("nome:" + cliente.getNome());
        continue;
      }
      jogadoresServidor.append(Constants.SEPARADORITEM).append("nome:" + cliente.getNome());
    }
    String mensagem = "jogadores:{" + jogadoresServidor.toString() + "}";
    return mensagem;
  }

  // Método para gerar a lista de partidas públicas
  private String gerarListaPartidas() {
    StringBuilder partidasServidor = new StringBuilder();
    List<Partida> partidasSnapshot;

    synchronized (partidas) {
      partidasSnapshot = new ArrayList<>(partidas);
    }

    Iterator<Partida> it = partidasSnapshot.iterator();
    while (it.hasNext()) {
      Partida partida = it.next();
      if (partidasServidor.isEmpty()) {
        partidasServidor.append(partida.getInfo());
        continue;
      }
      partidasServidor.append(Constants.SEPARADORITEM).append(partida.getInfo());
    }
    String mensagem = "partidas:{" + partidasServidor.toString() + "}";
    return mensagem;
  }

  //
  // MÉTODOS PARA AÇÕES DOS CLIENTES
  //

  public void cadastrarCliente(Map<String, Cliente> listaCliente, String nomeCliente, Socket connectionSocket,
      String tipo) {
    try {
      if (nomeCliente.contains(Constants.SEPARADORCLIENTE) || nomeCliente.contains(Constants.SEPARADOR)
          || nomeCliente.contains(" ") || nomeCliente.contains(Constants.SEPARADORATRIBUTO)
          || nomeCliente.contains(Constants.SEPARADORITEM) || !nomeCliente.matches("\\A\\p{ASCII}*\\z")) {
        DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());
        enviarLinha(outToClient, tipo, "400", "Nome de cliente nao pode conter: " + Constants.SEPARADORCLIENTE
            + " " + Constants.SEPARADOR + " " + Constants.SEPARADORATRIBUTO + " " + Constants.SEPARADORITEM
            + " ou espacos em brancos ou caracteres especiais", "campo:nomeCliente");
        return;
      }
    } catch (IOException e) {
      // Iremos ignorar

    }

    // Criando um token para o cliente, o cliente receberá esse token e deve
    // utilizá-lo para garantir sua identidade, poderíamos gerar um hash a partir do
    // nome e um salt mas um randomUUID já garante a unicidade necessária
    String tokenCliente;

    if (nomeCliente.equalsIgnoreCase("teste") || nomeCliente.equalsIgnoreCase("teste2")) {
      tokenCliente = nomeCliente;
    } else {
      tokenCliente = UUID.randomUUID().toString();
    }

    Cliente novoCliente = new Cliente(nomeCliente, tokenCliente, connectionSocket);
    // Tenta adicionar o cliente na lista, esse método retorna o item anterior
    // daquela chave (nomecliente), ou seja, se não existir item com aquela chave,
    // retorna null
    if (listaCliente.putIfAbsent(nomeCliente, novoCliente) != null) {
      // Usa o cliente recém-criado (não persistido) apenas para responder nesta
      // conexão
      novoCliente.enviarLinha(tipo, "409", "Um cliente com esse nome ja existe", "");
    } else {
      System.out.println("Cadastrando cliente: " + nomeCliente + " com token: " + tokenCliente);
      novoCliente.enviarLinha(tipo, "201", "Cadastrado com sucesso", "token:" + tokenCliente);
      // Inicia keepalive do cliente
      keepAliveCliente(novoCliente, tipo);

      notificarTodos(Constants.TIPOLISTARJOGADORES, "200", "Jogadores conectados",
          gerarListaJogadores());
    }
  }

  public void listarPartidasCliente(DataOutputStream outToClient, String tipo) {
    enviarLinha(outToClient, tipo, "200", "Partidas publicas", gerarListaPartidas());
  }

  public void listarJogadoresCliente(DataOutputStream outToClient, String tipo) {
    enviarLinha(outToClient, tipo, "200", "Jogadores conectados", gerarListaJogadores());
  }

  public void entrarPartidaCliente(Cliente cliente, int idPartida, String tipo) {
    String nomeCliente = cliente.getNome();
    // Percorre todas partidas e verifica se o id
    Partida partidaEscolhida = encontrarPartida(idPartida);

    // Partida não encontrada
    if (partidaEscolhida == null) {
      cliente.enviarLinha(tipo, "404", "Partida inexistente", "campo:idPartida");
      return;
    }

    // Se a partida estiver lotada, não pode entrar
    if (partidaEscolhida.partidaLotada() == true) {
      cliente.enviarLinha(tipo, "403", "Partida lotada", "");
      return;
    }

    // Pega o id da partida armazenada em cliente
    int idPartidaCliente = cliente.getIdPartida();

    // Se o cliente já está na partida escolhida, não faz nada
    if (idPartidaCliente == idPartida) {
      cliente.enviarLinha(tipo, "204", "Ja esta nessa partida", "");
      return;
    }

    // Se o cliente já estava em alguma partida
    if (idPartidaCliente != -1) {
      JogoPartida partidaAndamento = encontrarPartidaAndamento(idPartidaCliente);
      if (partidaAndamento != null) {
        cliente.enviarLinha(tipo, "403", "Nao e possivel sair de uma partida em andamento", "");
        return;
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

    cliente.enviarLinha(tipo, "200", "Entrou na partida com sucesso", "");

    // Tenta iniciar a partida
    tentarIniciarPartida(partidaEscolhida);

    notificarTodos(Constants.TIPOLISTARPARTIDAS, "200", "Partidas publicas", gerarListaPartidas());
  }

  public void desafiarCliente(Cliente clienteDesafiante, Cliente clienteDesafiado, String tipo) {
    String nomeDesafiante = clienteDesafiante.getNome();
    String nomeDesafiado = clienteDesafiado.getNome();

    // Verifica se o desafiante está em uma partida em andamento
    int idPartidaDesafiante = clienteDesafiante.getIdPartida();
    JogoPartida partidaAndamento = encontrarPartidaAndamento(idPartidaDesafiante);
    if (partidaAndamento != null) {
      clienteDesafiante.enviarLinha(tipo, "403", "Nao e possivel desafiar durante uma partida em andamento", "");
      return;
    }

    // Verifica se o desafiado está em uma partida em andamento
    int idPartidaDesafiado = clienteDesafiado.getIdPartida();
    partidaAndamento = encontrarPartidaAndamento(idPartidaDesafiado);
    if (partidaAndamento != null) {
      clienteDesafiante.enviarLinha(tipo, "403", "O jogador desafiado esta em uma partida em andamento", "");
      return;
    }

    // Se o destinatario não desafiou o remetente, então vamos apenas enviar o nosso
    // desafio para ele
    if (!nomeDesafiante.equals(clienteDesafiado.getJogadorDesafiado())) {
      notificarJogadorPartida(clienteDesafiado, tipo, "200", "Desafio recebido", "desafiante:" + nomeDesafiante);

      clienteDesafiante.setJogadorDesafiado(nomeDesafiado);

      clienteDesafiante.enviarLinha(tipo, "201", "Desafio enviado com sucesso", "desafiado:" + nomeDesafiado);
      return;
    }

    // Os dois jogadores se desafiaram, vamos iniciar a partida diretamente
    aceitarDesafio(clienteDesafiante, clienteDesafiado, tipo);
  }

  public void aceitarDesafioCliente(Cliente clienteDesafiado, Cliente clienteDesafiante, String tipo) {
    String nomeDesafiante = clienteDesafiado.getNome();

    // Se o destinatario não desafiou o remetente, então não há desafio para aceitar
    if (!nomeDesafiante.equals(clienteDesafiante.getJogadorDesafiado())) {
      clienteDesafiado.enviarLinha(tipo, "404", "Nao ha desafio desse jogador", "");
      return;
    }

    // Verifica se o desafiante está em uma partida em andamento
    int idPartidaDesafiante = clienteDesafiado.getIdPartida();
    JogoPartida partidaAndamento = encontrarPartidaAndamento(idPartidaDesafiante);
    if (partidaAndamento != null) {
      clienteDesafiado.enviarLinha(tipo, "403", "Nao e possivel aceitar desafio durante uma partida em andamento", "");
      return;
    }
    // Verifica se o desafiado está em uma partida em andamento
    int idPartidaDesafiado = clienteDesafiante.getIdPartida();
    partidaAndamento = encontrarPartidaAndamento(idPartidaDesafiado);
    if (partidaAndamento != null) {
      clienteDesafiado.enviarLinha(tipo, "403", "Nao e possivel aceitar desafio de um jogador em partida em andamento",
          "");
      return;
    }

    // Os dois jogadores se desafiaram, vamos iniciar a partida diretamente
    aceitarDesafio(clienteDesafiado, clienteDesafiante, tipo);
  }

  private void aceitarDesafio(Cliente clienteDesafiado, Cliente clienteDesafiante, String tipo) {
    // Criando a lista de clientes para a nova partida
    List<Cliente> clientes = new ArrayList<>();
    clientes.add(clienteDesafiado);
    clientes.add(clienteDesafiante);

    int idPartida = proximoIdAutoIncrement();
    
    // Garante que não estão em nenhuma partida
    sairPartida(clienteDesafiado, false, tipo);
    sairPartida(clienteDesafiante, false, tipo);

    clienteDesafiado.setIdPartida(idPartida);
    clienteDesafiante.setIdPartida(idPartida);

    JogoPartida novaPartida = new JogoPartida(idPartida, clientes, null);

    iniciarJogoPartida(novaPartida);
  }

  public void recusarDesafioCliente(Cliente clienteDesafiado, Cliente clienteDesafiante, String tipo) {
    String nomeDesafiado = clienteDesafiado.getNome();

    // Se o destinatario não desafiou o remetente, então não há desafio para recusar
    if (!nomeDesafiado.equals(clienteDesafiante.getJogadorDesafiado())) {
      clienteDesafiado.enviarLinha(tipo, "404", "Nao ha desafio desse jogador", "");
      return;
    }

    // Remove o desafio
    clienteDesafiante.setJogadorDesafiado(null);

    // Notifica o desafiante que o desafio foi recusado
    notificarJogadorPartida(clienteDesafiante, tipo, "403", "Desafio recusado", "desafiado:" + nomeDesafiado);

    clienteDesafiado.enviarLinha(tipo, "200", "Desafio recusado com sucesso", "");
  }

  public void chatGlobalCliente(Cliente cliente, String mensagem, String tipo) {
    String nomeCliente = cliente.getNome();

    notificarTodos(tipo, "200", "Mensagem global",
        "nome:" + nomeCliente + Constants.SEPARADORATRIBUTO + "mensagem:" + mensagem);
  }

  public void chatPartidaCliente(Cliente cliente, String mensagem, String tipo) {
    String nomeCliente = cliente.getNome();
    JogoPartida partidaAndamento = encontrarPartidaAndamento(cliente.getIdPartida());
    if (partidaAndamento == null) {
      cliente.enviarLinha(tipo, "404", "Cliente nao esta em uma partida em andamento", "");
      return;
    }

    // Percorre todos os clientes da partida e envia a mensagem
    List<Jogador> jogadoresSnapshot;
    synchronized (partidaAndamento) {
      jogadoresSnapshot = new ArrayList<>(partidaAndamento.getTodosJogadores());
    }
    Iterator<Jogador> it = jogadoresSnapshot.iterator();
    while (it.hasNext()) {
      Jogador jogador = it.next();
      notificarJogadorPartida(jogador, tipo, "200", "Chat da partida",
          "nome:" + nomeCliente + Constants.SEPARADORATRIBUTO + "mensagem:" + mensagem);
    }
  }

  public void chatJogadorCliente(Cliente cliente, String nomeDestinatario, String mensagem, String tipo) {
    String nomeCliente = cliente.getNome();
    Cliente clienteDestinatario = listaCliente.get(nomeDestinatario);
    if (clienteDestinatario == null) {
      cliente.enviarLinha(tipo, "404", "Jogador destinatario nao existe", "");
      return;
    }

    String valor = "nome:" + nomeCliente + Constants.SEPARADORATRIBUTO + "mensagem:" + mensagem;

    // Envia a mensagem para o destinatário
    clienteDestinatario.enviarLinha(tipo, "200", "Chat privado", valor);
    // Também envia o remetente para confirmação
    cliente.enviarLinha(tipo, "200", "Chat privado", valor);
  }

  public void prontoPartidaCliente(Cliente cliente, String tipo) {
    int idPartida = cliente.getIdPartida();
    JogoPartida partidaAndamento = encontrarPartidaAndamento(idPartida);
    if (partidaAndamento == null) {
      cliente.enviarLinha(tipo, "404", "Cliente nao esta em uma partida em andamento", "");
      return;
    }
    partidaAndamento.definirJogadorPronto(cliente.getNome());
    cliente.enviarLinha(tipo, "200", "Jogador marcado como pronto", "");
    // Se todos jogadores estiverem prontos, notifica e avança o turno (define o
    // primeiro turno)
    if (partidaAndamento.todosJogadoresProntos()) {
      notificarJogadoresPartida(partidaAndamento, Constants.TIPOINICIOPARTIDA, "200", "Todos jogadores prontos", "");
      proximoTurno(partidaAndamento);
    }
  }

  public void moverCliente(Cliente cliente, int posicaoX, int posicaoY, boolean deslocamento, String tipo) {
    String nomeCliente = cliente.getNome();
    JogoPartida partidaAndamento = encontrarPartidaAndamento(cliente.getIdPartida());
    if (partidaAndamento == null) {
      cliente.enviarLinha(tipo, "404", "Cliente nao esta em uma partida em andamento", "");
      return;
    }

    synchronized (partidaAndamento) {
      if (!partidaAndamento.todosJogadoresProntos()) {
        cliente.enviarLinha(tipo, "202", "Jogadores ainda nao prontos", "");
      } else if (!nomeCliente.equals(partidaAndamento.getJogadorTurno())) {
        cliente.enviarLinha(tipo, "403", "Nao e o turno do jogador", "");
      } else if (!partidaAndamento.movimento(nomeCliente, posicaoX, posicaoY, deslocamento)) {
        cliente.enviarLinha(tipo, "400", "Movimento invalido",
            "campo:[posicaoX" + Constants.SEPARADORATRIBUTO + "posicaoY]");
      } else {
        if (deslocamento) {
          Jogador jogador = partidaAndamento.buscarJogadorPorNome(nomeCliente);
          posicaoX = jogador.getPosicao().getX();
          posicaoY = jogador.getPosicao().getY();
        }
        cliente.enviarLinha(tipo, "200", "Movimento realizado com sucesso", "x:" + posicaoX + ",y:" + posicaoY);
        // Evita que o timer dispare durante a troca de turno
        cancelarTimerTurno(partidaAndamento);
        proximoTurnoPartida(partidaAndamento);
      }
    }
  }

  public void atacarCliente(Cliente cliente, int posicaoX, int posicaoY, boolean deslocamento, String tipo) {
    String nomeCliente = cliente.getNome();
    JogoPartida partidaAndamento = encontrarPartidaAndamento(cliente.getIdPartida());
    if (partidaAndamento == null) {
      cliente.enviarLinha(tipo, "404", "Cliente nao esta em uma partida em andamento", "");
      return;
    }

    synchronized (partidaAndamento) {
      if (!partidaAndamento.todosJogadoresProntos()) {
        cliente.enviarLinha(tipo, "202", "Jogadores ainda nao prontos", "");
      } else if (!nomeCliente.equals(partidaAndamento.getJogadorTurno())) {
        cliente.enviarLinha(tipo, "403", "Nao e o turno do jogador", "");
      } else if (!partidaAndamento.ataque(nomeCliente, posicaoX, posicaoY, deslocamento)) {
        cliente.enviarLinha(tipo, "400", "Ataque invalido",
            "campo:[posicaoX" + Constants.SEPARADORATRIBUTO + "posicaoY]");
      } else {
        if (deslocamento) {
          Jogador jogador = partidaAndamento.buscarJogadorPorNome(nomeCliente);
          posicaoX = jogador.getPosicao().getX();
          posicaoY = jogador.getPosicao().getY();
        }
        cliente.enviarLinha(tipo, "200", "Ataque realizado com sucesso", "x:" + posicaoX + ",y:" + posicaoY);
        // Avança para o próximo turno
        cancelarTimerTurno(partidaAndamento);
        proximoTurnoPartida(partidaAndamento);
      }
    }
  }

  public void sonarCliente(Cliente cliente, int posicaoX, int posicaoY, boolean deslocamento, String tipo) {
    String nomeCliente = cliente.getNome();
    JogoPartida partidaAndamento = encontrarPartidaAndamento(cliente.getIdPartida());
    if (partidaAndamento == null) {
      cliente.enviarLinha(tipo, "404", "Cliente nao esta em uma partida em andamento", "");
      return;
    }

    synchronized (partidaAndamento) {
      if (!partidaAndamento.todosJogadoresProntos()) {
        cliente.enviarLinha(tipo, "202", "Jogadores ainda nao prontos", "");
      } else if (!nomeCliente.equals(partidaAndamento.getJogadorTurno())) {
        cliente.enviarLinha(tipo, "403", "Nao e o turno do jogador", "");
      } else if (!partidaAndamento.dispositivoProximidade(nomeCliente, posicaoX, posicaoY, deslocamento)) {
        cliente.enviarLinha(tipo, "400", "Sonar invalido",
            "campo:[posicaoX" + Constants.SEPARADORATRIBUTO + "posicaoY]");
      } else {

        if (deslocamento) {
          Jogador jogador = partidaAndamento.buscarJogadorPorNome(nomeCliente);
          posicaoX = jogador.getPosicao().getX();
          posicaoY = jogador.getPosicao().getY();
        }
        cliente.enviarLinha(tipo, "200", "Sonar utilizado com sucesso", "x:" + posicaoX + ",y:" + posicaoY);
        // Avança para o próximo turno
        cancelarTimerTurno(partidaAndamento);
        proximoTurnoPartida(partidaAndamento);
      }
    }
  }

  public void passarCliente(Cliente cliente, String tipo) {
    String nomeCliente = cliente.getNome();
    JogoPartida partidaAndamento = encontrarPartidaAndamento(cliente.getIdPartida());
    if (partidaAndamento == null) {
      cliente.enviarLinha(tipo, "404", "Cliente nao esta em uma partida em andamento", "");
      return;
    }

    synchronized (partidaAndamento) {
      if (!partidaAndamento.todosJogadoresProntos()) {
        cliente.enviarLinha(tipo, "202", "Jogadores ainda nao prontos", "");
      } else if (!nomeCliente.equals(partidaAndamento.getJogadorTurno())) {
        cliente.enviarLinha(tipo, "403", "Nao e o turno do jogador", "");
      } else {
        cliente.enviarLinha(tipo, "200", "Turno passado com sucesso", "");
        // Avança para o próximo turno
        cancelarTimerTurno(partidaAndamento);
        proximoTurnoPartida(partidaAndamento);
      }
    }
  }

  private void sairPartida(Cliente cliente, boolean enviarNotificacao, String tipo) {
    String nomeCliente = cliente.getNome();
    cliente.setJogadorDesafiado(null);

    // Caso o cliente esteja em uma partida em andamento
    JogoPartida partidaAndamento = encontrarPartidaAndamento(cliente.getIdPartida());
    if (partidaAndamento != null) {
      boolean avancarTurno = false;
      synchronized (partidaAndamento) {
        String turno = partidaAndamento.getJogadorTurno();
        partidaAndamento.removerJogador(partidaAndamento.buscarJogadorPorNome(nomeCliente));
        avancarTurno = turno != null && turno.equals(nomeCliente);
        cliente.setIdPartida(-1);
      }
      if (enviarNotificacao)
        cliente.enviarLinha(tipo, "200", "Saiu da partida em andamento com sucesso", "");
      if (avancarTurno) {
        proximoTurnoPartida(partidaAndamento);
      }
      return;
    }

    // Não está em uma partida em andamento, pega a partida do cliente
    Partida partida = encontrarPartida(cliente.getIdPartida());

    if (partida != null) {
      partida.removerCliente(cliente);
      cliente.setIdPartida(-1);
      if (enviarNotificacao)
        cliente.enviarLinha(tipo, "200", "Saiu da partida com sucesso", "");
    } else if (enviarNotificacao) {
      cliente.enviarLinha(tipo, "404", "Nao esta em partida", "");
    }
  }

  public void sairPartidaCliente(Cliente cliente, String tipo) {
    sairPartida(cliente, true, tipo);
  }

  public void sair(Cliente cliente, String codigo, String tipo) {
    String nomeCliente = cliente.getNome();
    System.out.println("Cliente desconectado: " + nomeCliente);

    sairPartida(cliente, false, tipo);

    // Remove o cliente da lista de clientes
    listaCliente.remove(nomeCliente);
    // Cancela o keepalive deste cliente
    cancelarKeepAlive(cliente);
    cliente.enviarLinha(tipo, codigo, "Desconectado com sucesso", "nomeCliente:" + nomeCliente);
    // Notificando todos sobre a saída do cliente
    notificarTodos(Constants.TIPOLISTARJOGADORES, "200", "Jogadores conectados",
        gerarListaJogadores());

    // Fecha o socket deste cliente
    try {
      Socket connectionSocket = cliente.getConnectionSocket();
      if (connectionSocket != null && !connectionSocket.isClosed()) {
        connectionSocket.close();
      }
    } catch (IOException e) {
      // ignora
    }
  }

  public void sairCliente(Cliente cliente, String tipo) {
    sair(cliente, "200", tipo);
  }

  // Método criado pelo Agente Copilot do VSCode ao pedir como se
  // fazia um turno de 15 segundos
  // Agenda (ou reinicia) o timer de 15s para o turno atual da partida
  private void agendarTimerTurno(JogoPartida jogoPartida) {
    int partidaId = jogoPartida.getId();
    // Cancela o anterior, se houver
    ScheduledFuture<?> anterior = turnTimers.remove(partidaId);
    if (anterior != null) {
      anterior.cancel(false);
    }
    final int turnoAgendado = jogoPartida.getNumTurno();
    ScheduledFuture<?> futuro = turnScheduler.schedule(() -> {
      try {
        // Verifica se ainda estamos no mesmo turno e captura o jogador do turno
        Jogador jogadorTurnoAtual;
        String nomeJogadorTurno;
        synchronized (jogoPartida) {
          if (jogoPartida.getNumTurno() != turnoAgendado) {
            return; // já avançou por ação do jogador
          }
          nomeJogadorTurno = jogoPartida.getJogadorTurno();
          jogadorTurnoAtual = jogoPartida.buscarJogadorPorNome(nomeJogadorTurno);
        }
        if (jogadorTurnoAtual != null) {
          notificarJogadorPartida(jogadorTurnoAtual, Constants.TIPOTURNOEXPIROU, "408", "Seu turno expirou",
              "nome:" + nomeJogadorTurno);
        }
        // Força avanço do turno (equivale a PASSAR)
        proximoTurnoPartida(jogoPartida);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }, Constants.TEMPO_TURNO, TimeUnit.SECONDS);
    turnTimers.put(partidaId, futuro);
  }

  // Método criado pelo Agente Copilot do VSCode ao pedir como se
  // fazia um turno de 15 segundos
  // Cancela o timer do turno da partida, se existir
  private void cancelarTimerTurno(JogoPartida jogoPartida) {
    int partidaId = jogoPartida.getId();
    ScheduledFuture<?> futuro = turnTimers.remove(partidaId);
    if (futuro != null) {
      futuro.cancel(false);
    }
  }

  public void keepAliveCliente(Cliente cliente, String tipo) {
    if (!Constants.KEEPALIVE)
      return;
    if (cliente == null)
      return;
    String chave = cliente.getNome();
    // Cancela agendamento anterior, se houver
    ScheduledFuture<?> anterior = keepAliveTimers.remove(chave);
    if (anterior != null) {
      anterior.cancel(false);
    }
    int tempo = Constants.TEMPO_KEEPALIVE;
    if ("teste".equals(cliente.getNome()) || "teste2".equals(cliente.getNome())) {
      tempo = 600; // tempo maior para cliente de teste
    }
    // Agenda novo timeout
    ScheduledFuture<?> futuro = keepAliveScheduler.schedule(() -> {
      try {
        // Tempo esgotado: desconecta o cliente
        sair(cliente, "408", Constants.TIPODESCONECTADO);
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        // Remove o handle para evitar vazamento
        keepAliveTimers.remove(chave);
      }
    }, tempo, TimeUnit.SECONDS);
    keepAliveTimers.put(chave, futuro);
  }

  private void cancelarKeepAlive(Cliente cliente) {
    if (!Constants.KEEPALIVE)
      return;
    if (cliente == null)
      return;
    String chave = cliente.getNome();
    ScheduledFuture<?> fut = keepAliveTimers.remove(chave);
    if (fut != null) {
      fut.cancel(false);
    }
  }
}
