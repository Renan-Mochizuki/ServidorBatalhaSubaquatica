package classes;

import java.util.*;

public class JogoPartida extends Partida {
  private List<DispositivoProximidade> dispositivos;
  private List<Jogador> jogadores;
  private String jogadorTurno;

  public JogoPartida(int id, List<Cliente> clientes) {
    super(id);
    setAndamento(true);
    this.dispositivos = new ArrayList<>();
    this.jogadores = new ArrayList<>();
    this.jogadorTurno = null;
    // Não iremos copiar clientes diretamente, pois queremos transformá-los em
    // Jogadores, poderiamos criar usar a mesma lista clientes já existente, mas
    // teriamos que ficar convertendo para Jogador, então para facilitar criamos uma
    // lista nova de jogadores
    Random random = new Random();

    // Vamos percorrer cada cliente e instanciar um novo Jogador a partir dele
    Iterator<Cliente> iterator = clientes.iterator();
    while (iterator.hasNext()) {
      Cliente cl = iterator.next();
      // Variavel para a posição do jogador que vai ser aleatória
      int x1 = 0;
      int y1 = 0;
      // Variavel de controle do loop
      Boolean posicaoValida = false;

      // Código meio ineficiente para garantir que os jogadores não sejam posicionados
      // aleatóriamente um perto do outro de acordo com a constante
      // PROXIMIDADE_INICIAL_JOGADORES
      while (!posicaoValida) {
        posicaoValida = true;
        // Crie um x e y aleatórios
        x1 = random.nextInt(16);
        y1 = random.nextInt(16);
        // Vamos verificar para todos os jogadores já instanciados no jogo para
        // verificar se essas coordenadas geradas estão próximas de qualquer jogador
        Iterator<Jogador> iterator2 = this.getJogadores().iterator();
        while (iterator2.hasNext()) {
          // Obtendo o jogador e suas coordenadas
          Jogador jogadorJaInstanciado = iterator2.next();
          // Verifica se a distância entre o jogador gerado e o já instanciado é menor que
          // a
          // permitida
          if (jogadorJaInstanciado.getPosicao().distanciaPermitida(x1, y1, Constants.PROXIMIDADE_INICIAL_JOGADORES,
              Constants.MODO_MOVIMENTO)) {
            posicaoValida = false;
            break;
          }
        }
      }
      Jogador j = new Jogador(cl.getNome(), cl.getToken(), cl.getConnectionSocket(), x1, y1,
          Constants.NUMERO_MAX_DISPOSITIVOS_JOGADOR);
      this.jogadores.add(j);
    }
  }

  public int getId() {
    return super.getId();
  }

  public List<Jogador> getJogadores() {
    return this.jogadores;
  }

  public Jogador buscarJogadorPorNome(String nome) {
    Iterator<Jogador> iterator = this.jogadores.iterator();
    while (iterator.hasNext()) {
      Jogador j = iterator.next();
      if (j.getNome().equals(nome)) {
        return j;
      }
    }
    return null;
  }

  public String getJogadorTurno() {
    return jogadorTurno;
  }

  public void setJogadorTurno(String jogadorTurno) {
    this.jogadorTurno = jogadorTurno;
  }

  public String proximoTurno() {
    if (jogadores.isEmpty())
      return null;

    // Verifica se não há turno definido ainda
    if (jogadorTurno == null) {
      jogadorTurno = jogadores.get(0).getNome();
      return jogadorTurno;
    }

    // Percorre a lista de jogadores para encontrar o jogador com o turno atual e
    // avança para o próximo jogador da lista de jogadores dessa partida
    for (int i = 0; i < jogadores.size(); i++) {
      if (jogadores.get(i).getNome().equals(jogadorTurno)) {
        // Garantir que volta ao início da lista se estiver no último jogador
        int proximoIndice = (i + 1) % jogadores.size();
        jogadorTurno = jogadores.get(proximoIndice).getNome();
        return jogadorTurno;
      }
    }
    return null;
  }

  public Boolean movimento(String nomeJogador, int posicaoX, int posicaoY) {
    Jogador jogador = buscarJogadorPorNome(nomeJogador);
    if (jogador != null) {
      return jogador.mover(posicaoX, posicaoY);
    }
    return false;
  }

  public Boolean atacar(String nomeJogador, int posicaoX, int posicaoY) {
    Jogador atacante = buscarJogadorPorNome(nomeJogador);

    if (atacante == null || !atacante.getPosicao().distanciaPermitida(posicaoX, posicaoY, Constants.ALCANCE_ATAQUE, Constants.MODO_ATAQUE)) {
      return false;
    }

    Iterator<Jogador> iterator = this.jogadores.iterator();
    while (iterator.hasNext()) {
      Jogador jogadorAlvo = iterator.next();
      if(jogadorAlvo == atacante) {
        continue;
      }

      if (jogadorAlvo.getPosicao().distanciaPermitida(posicaoX, posicaoY, Constants.ALCANCE_ATAQUE, Constants.MODO_ATAQUE)) {
        removerJogador(jogadorAlvo);
        return true;
      }
    }

    return true;
  }

  public void removerJogador(Jogador jogador) {
    this.jogadores.remove(jogador);
  }

  public void finalizarPartida() {
    setAndamento(false);
  }

  // Método gerado pelo Agente Copilot no VSCode
  public void imprimirPartida() {
    int tamanho = Constants.TAMANHO_TABULEIRO;

    // Inicializa o tabuleiro com '.'
    String[][] tab = new String[tamanho][tamanho];
    for (int y = 0; y < tamanho; y++) {
      for (int x = 0; x < tamanho; x++) {
        tab[y][x] = ".";
      }
    }

    // Marca a posição de cada jogador com seu nome
    for (int i = 0; i < jogadores.size(); i++) {
      Jogador j = jogadores.get(i);
      if (j != null && j.getPosicao() != null) {
        int x = j.getPosicao().getX();
        int y = j.getPosicao().getY();
        if (x >= 0 && x < tamanho && y >= 0 && y < tamanho) {
          // Se já houver algo naquela posição (dois jogadores no mesmo local), marca com
          // '*'
          if (!tab[y][x].equals(".")) {
            tab[y][x] = "*";
          } else {
            if (j.getNome().length() > 1) {
              tab[y][x] = Integer.toString(i + 1);
            } else {
              tab[y][x] = j.getNome();
            }
          }
        }
      }
    }

    // Imprime cabeçalho com índices de coluna
    System.out.println("Partida ID: " + getId());
    System.out.print("    ");
    for (int x = 0; x < tamanho; x++) {
      System.out.print(String.format("%2d ", x));
    }
    System.out.println();

    // Imprime cada linha
    for (int y = 0; y < tamanho; y++) {
      System.out.print(String.format("%2d ", y));
      System.out.print(" ");
      for (int x = 0; x < tamanho; x++) {
        System.out.print(String.format(" %s ", tab[y][x]));
      }
      System.out.println();
    }
    System.out.println();
  }

}
