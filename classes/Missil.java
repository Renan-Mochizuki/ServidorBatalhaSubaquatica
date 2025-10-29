package classes;

public class Missil implements IDetector {
  int num; // Turno que esse missil foi lançado
  Posicao posicao;
  int alcance;
  Jogador jogadorDono;

  public Missil(int x, int y, int alcance, Jogador jogadorDono, int num) {
    this.posicao = new Posicao(x, y);
    this.jogadorDono = jogadorDono;
    this.alcance = alcance;
    this.num = num;
  }

  public Posicao getPosicao() {
    return this.posicao;
  }

  public Jogador getJogadorDono() {
    return this.jogadorDono;
  }

  public int getAlcance() {
    return this.alcance;
  }

  public int getNum() {
    return this.num;
  }

  public int getModoAlcance() {
    return Constants.MODO_ALCANCE_ATAQUE;
  }
}
