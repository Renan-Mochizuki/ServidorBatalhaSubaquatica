package classes;

public class DispositivoProximidade implements IDetector {
  // Identificador discriminante do dispositivo de um jogador (1 a
  // numMaxDispositivos)
  private int num;
  private Posicao posicao;
  private int alcance;
  private Jogador jogadorDono;

  public DispositivoProximidade(int x, int y, int alcance, Jogador jogadorDono, int num) {
    this.posicao = new Posicao(x, y);
    this.jogadorDono = jogadorDono;
    this.alcance = alcance;
    this.num = num;
  }

  public Posicao getPosicao() {
    return this.posicao;
  }

  public int getAlcance() {
    return this.alcance;
  }

  public Jogador getJogadorDono() {
    return this.jogadorDono;
  }

  public int getNum() {
    return this.num;
  }

  public int getModoAlcance() {
    return Constants.MODO_ALCANCE_DISPOSITIVO_PROXIMIDADE;
  }
}
