package server;

import java.util.*;
import classes.*;
import java.io.*;
import java.net.*;

public class GameManager {
  private int idAutoIncrement;

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
  public void notificarJogadorPartida(Cliente cliente, String mensagem, String valor) throws IOException {
    // Sincroniza por cliente para evitar escritas concorrentes no mesmo socket
    synchronized (cliente) {
      DataOutputStream outToClient = new DataOutputStream(cliente.getConnectionSocket().getOutputStream());
      outToClient.writeBytes("1|" + mensagem + "|" + valor + "\n");
    }
  }

  // Método que notifica todos os jogadores de uma partida com uma mensagem e um
  // valor passado
  public void notificarJogadoresPartida(JogoPartida jogoPartida, String mensagem, String valor) throws IOException {
    // Percorre todos os clientes da partida e envia a mensagem
    synchronized (jogoPartida) {
      Iterator<Jogador> it = jogoPartida.getTodosJogadores().iterator();
      while (it.hasNext()) {
        Jogador jogador = it.next();
        notificarJogadorPartida(jogador, mensagem, valor);
      }
    }
  }

  // Método que notifica o dono do dispositivo sobre os jogadores detectados por
  // seus dispositivos de proximidade
  public void notificarJogadoresDetectados(JogoPartida jogoPartida) throws IOException {
    synchronized (jogoPartida) {
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
  }

  // Método para tentar iniciar a partida, toda vez que um cliente se conecta a
  // partida ele tentará iniciar a partida, se chama o método iniciarPartida da
  // Partida que apenas muda o estado de andamento, se conseguir, devemos
  // instanciar agora o JogoPartida que instancia os jogadores e inicia o jogo
  public void tentarIniciarPartida(Partida partida) throws IOException {
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
  public void iniciarJogoPartida(JogoPartida novaPartida) throws IOException {
    synchronized (jogoPartidas) {
      jogoPartidas.add(novaPartida);
    }

    System.out.println("Partida iniciada: " + novaPartida.getId());
    novaPartida.imprimirPartida();

    // Devemos notificar os clientes dessa partida que a partida iniciou
    notificarJogadoresPartida(novaPartida, "Partida iniciada", novaPartida.getId() + "");

    String jogadorTurno;
    synchronized (novaPartida) {
      jogadorTurno = novaPartida.proximoTurno();
    }
    notificarJogadoresPartida(novaPartida, "Turno do jogador", jogadorTurno);
  }

  // Método que passa para o próximo turno da partida e determina os
  // acontecimentos da partida para notificar os jogadores
  public void proximoTurnoPartida(JogoPartida jogoPartida) throws IOException {
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

  // Método que notifica o dono do dispositivo sobre os jogadores acertados por
  // seus misseis
  public void lidarJogadoresAtaques(JogoPartida jogoPartida) throws IOException {
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
  }

  // Método que roda cada turno, se a partida terminou, notifica os jogadores e
  // chama outra função para finalizar a partida e remover da lista
  public Boolean verificarFimJogoPartida(JogoPartida jogoPartida) throws IOException {
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
  public void finalizarJogoPartida(JogoPartida jogoPartida) {
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

  //
  // MÉTODOS PARA AÇÕES DOS CLIENTES
  //

  public void cadastrarCliente(Map<String, Cliente> listaCliente, String nomeCliente, Socket connectionSocket)
      throws IOException {
    DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());

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
  }

  public void listarPartidasCliente(DataOutputStream outToClient) throws IOException {
    StringBuilder partidasServidor = new StringBuilder();

    synchronized (partidas) {
      Iterator<Partida> it = partidas.iterator();
      while (it.hasNext()) {
        Partida partida = it.next();
        partidasServidor.append(partida.getInfo()).append(";");
      }
    }

    outToClient.writeBytes("1|Partidas públicas|" + partidasServidor + "\n");
  }

  public void listarJogadoresCliente(DataOutputStream outToClient) throws IOException {
    StringBuilder jogadoresServidor = new StringBuilder();

    synchronized (listaCliente) {
      Iterator<Cliente> it = listaCliente.values().iterator();
      while (it.hasNext()) {
        Cliente cliente = it.next();
        jogadoresServidor.append(cliente.getNome()).append(";");
      }
    }

    outToClient.writeBytes("1|Jogadores conectados|" + jogadoresServidor + "\n");
  }

  public void entrarPartidaCliente(Cliente cliente, int idPartida, DataOutputStream outToClient) throws IOException {
    String nomeCliente = cliente.getNome();
    // Percorre todas partidas e verifica se o id
    Partida partidaEscolhida = encontrarPartida(idPartida);

    // Partida não encontrada
    if (partidaEscolhida == null) {
      outToClient.writeBytes("0|Partida inexistente|\n");
      return;
    }

    // Se a partida estiver lotada, não pode entrar
    if (partidaEscolhida.partidaLotada() == true) {
      outToClient.writeBytes("0|Partida lotada|\n");
      return;
    }

    // Pega o id da partida armazenada em cliente
    int idPartidaCliente = cliente.getIdPartida();

    // Se o cliente já está na partida escolhida, não faz nada
    if (idPartidaCliente == idPartida) {
      outToClient.writeBytes("1|Cliente ja esta nessa partida|\n");
      return;
    }

    // Se o cliente já estava em alguma partida
    if (idPartidaCliente != -1) {
      JogoPartida partidaAndamento = encontrarPartidaAndamento(idPartidaCliente);
      if (partidaAndamento != null) {
        outToClient.writeBytes("0|Nao e possivel trocar de partida durante um jogo|\n");
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

    outToClient.writeBytes("1|Entrou na partida com sucesso|\n");

    // Tenta iniciar a partida
    tentarIniciarPartida(partidaEscolhida);
  }

  public void desafiarCliente(Cliente clienteDesafiante, Cliente clienteDesafiado, DataOutputStream outToClient)
      throws IOException {
    String nomeDesafiante = clienteDesafiante.getNome();
    String nomeDesafiado = clienteDesafiado.getNome();

    // Verifica se o desafiante está em uma partida em andamento
    int idPartidaDesafiante = clienteDesafiante.getIdPartida();
    JogoPartida partidaAndamento = encontrarPartidaAndamento(idPartidaDesafiante);
    if (partidaAndamento != null) {
      outToClient.writeBytes("0|Nao e possivel desafiar durante uma partida em andamento|\n");
      return;
    }

    // Se o destinatario não desafiou o remetente, então vamos apenas enviar o nosso
    // desafio para ele
    if (!nomeDesafiante.equals(clienteDesafiado.getJogadorDesafiado())) {
      notificarJogadorPartida(clienteDesafiado, "Desafio recebido", nomeDesafiante);

      clienteDesafiante.setJogadorDesafiado(nomeDesafiado);

      outToClient.writeBytes("1|Desafio enviado com sucesso|\n");
      return;
    }

    // Os dois jogadores se desafiaram, vamos iniciar a partida diretamente

    // Criando a lista de clientes para a nova partida
    List<Cliente> clientes = new ArrayList<>();
    clientes.add(clienteDesafiante);
    clientes.add(clienteDesafiado);

    int idPartida = proximoIdAutoIncrement();

    clienteDesafiante.setIdPartida(idPartida);
    clienteDesafiado.setIdPartida(idPartida);

    JogoPartida novaPartida = new JogoPartida(idPartida, clientes, null);

    iniciarJogoPartida(novaPartida);
  }

  public void moverCliente(Cliente cliente, int posicaoX, int posicaoY, Boolean deslocamento,
      DataOutputStream outToClient) throws IOException {
    String nomeCliente = cliente.getNome();
    JogoPartida partidaAndamento = encontrarPartidaAndamento(cliente.getIdPartida());
    if (partidaAndamento == null) {
      outToClient.writeBytes("0|Cliente nao esta em uma partida em andamento|\n");
      return;
    }

    // Garante atomicidade por partida
    synchronized (partidaAndamento) {
      if (!nomeCliente.equals(partidaAndamento.getJogadorTurno())) {
        outToClient.writeBytes("0|Nao e o turno do jogador|\n");
      } else if (!partidaAndamento.movimento(nomeCliente, posicaoX, posicaoY, deslocamento)) {
        outToClient.writeBytes("0|Movimento invalido|\n");
      } else {
        outToClient.writeBytes("1|Movimento realizado com sucesso|\n");
        proximoTurnoPartida(partidaAndamento);
      }
    }
  }

  public void atacarCliente(Cliente cliente, int posicaoX, int posicaoY, Boolean deslocamento,
      DataOutputStream outToClient) throws IOException {
    String nomeCliente = cliente.getNome();
    JogoPartida partidaAndamento = encontrarPartidaAndamento(cliente.getIdPartida());
    if (partidaAndamento == null) {
      outToClient.writeBytes("0|Cliente nao esta em uma partida em andamento|\n");
      return;
    }

    synchronized (partidaAndamento) {
      if (!nomeCliente.equals(partidaAndamento.getJogadorTurno())) {
        outToClient.writeBytes("0|Nao e o turno do jogador|\n");
      } else if (!partidaAndamento.ataque(nomeCliente, posicaoX, posicaoY, deslocamento)) {
        outToClient.writeBytes("0|Ataque invalido|\n");
      } else {
        outToClient.writeBytes("1|Ataque realizado com sucesso|\n");
        // Avança para o próximo turno
        proximoTurnoPartida(partidaAndamento);
      }
    }
  }

  public void sonarCliente(Cliente cliente, int posicaoX, int posicaoY, Boolean deslocamento,
      DataOutputStream outToClient) throws IOException {
    String nomeCliente = cliente.getNome();
    JogoPartida partidaAndamento = encontrarPartidaAndamento(cliente.getIdPartida());
    if (partidaAndamento == null) {
      outToClient.writeBytes("0|Cliente nao esta em uma partida em andamento|\n");
      return;
    }

    synchronized (partidaAndamento) {
      // Realiza a ação do dispositivo de proximidade dentro do lock da partida
      if (!nomeCliente.equals(partidaAndamento.getJogadorTurno())) {
        outToClient.writeBytes("0|Nao e o turno do jogador|\n");
      } else if (!partidaAndamento.dispositivoProximidade(nomeCliente, posicaoX, posicaoY, deslocamento)) {
        outToClient.writeBytes("0|Sonar invalido|\n");
      } else {
        outToClient.writeBytes("1|Sonar utilizado com sucesso|\n");
        // Avança para o próximo turno
        proximoTurnoPartida(partidaAndamento);
      }
    }
  }

  public void passarCliente(Cliente cliente, DataOutputStream outToClient) throws IOException {
    String nomeCliente = cliente.getNome();
    JogoPartida partidaAndamento = encontrarPartidaAndamento(cliente.getIdPartida());
    if (partidaAndamento == null) {
      outToClient.writeBytes("0|Cliente nao esta em uma partida em andamento|\n");
      return;
    }

    synchronized (partidaAndamento) {
      if (!nomeCliente.equals(partidaAndamento.getJogadorTurno())) {
        outToClient.writeBytes("0|Nao e o turno do jogador|\n");
      } else {
        outToClient.writeBytes("1|Turno passado com sucesso|\n");
        // Avança para o próximo turno
        proximoTurnoPartida(partidaAndamento);
      }
    }
  }

  public void sairPartidaCliente(Cliente cliente, DataOutputStream outToClient) throws IOException {
    String nomeCliente = cliente.getNome();
    cliente.setJogadorDesafiado(null);

    // Caso o cliente esteja em uma partida em andamento
    JogoPartida partidaAndamento = encontrarPartidaAndamento(cliente.getIdPartida());
    if (partidaAndamento != null) {
      synchronized (partidaAndamento) {
        String turno = partidaAndamento.getJogadorTurno();
        partidaAndamento.removerJogador(partidaAndamento.buscarJogadorPorNome(nomeCliente));
        if (turno.equals(nomeCliente)) {
          proximoTurnoPartida(partidaAndamento);
        }
        cliente.setIdPartida(-1);
        outToClient.writeBytes("1|Saiu da partida|\n");
      }
      return;
    }

    // Não está em uma partida em andamento, pega a partida do cliente
    Partida partida = encontrarPartida(cliente.getIdPartida());

    if (partida != null) {
      partida.removerCliente(cliente);
      cliente.setIdPartida(-1);
      outToClient.writeBytes("1|Saiu da partida|\n");
    } else {
      outToClient.writeBytes("0|Partida inexistente|\n");
    }
  }

}
