package classes;

public class Jogador extends Cliente {
  private Posicao posicao;
  private int numDispositivos;
  private int numMaxDispositivos;

  public Jogador(String nome, String token, int x, int y, int numMaxDispositivos) {
    super(nome, token);
    this.posicao = new Posicao(x, y);
    this.numDispositivos = 0;
    this.numMaxDispositivos = numMaxDispositivos;
  }

  public Posicao getPosicao() {
    return this.posicao;
  }

  // Método para mover o jogador de acordo com um deslocamento em X e Y
  public Boolean mover(int deslocamentoX, int deslocamentoY) {
    int novoX = posicao.getX() + deslocamentoX;
    int novoY = posicao.getY() + deslocamentoY;
    if (novoX < 0 || novoX >= Constants.TAMANHO_TABULEIRO || novoY < 0 || novoY >= Constants.TAMANHO_TABULEIRO) {
      return false;
    }
    this.posicao.setX(novoX);
    this.posicao.setY(novoY);
    return true;
  }

  // Método para adicionar dispositivo
  public Boolean adicionarDispositivo() {
    if (this.numDispositivos < this.numMaxDispositivos) {
      this.numDispositivos++;
      return true;
    }
    return false;
  }

  // Método para remover dispositivo
  public Boolean removerDispositivo() {
    if (this.numDispositivos > 0) {
      this.numDispositivos--;
      return true;
    }
    return false;
  }
}