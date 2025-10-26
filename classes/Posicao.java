package classes;

// Classe para representar uma posição no tabuleiro
public class Posicao {
  private int x;
  private int y;

  Posicao(int x, int y) {
    this.x = x;
    this.y = y;
  }

  public int getX() {
    return this.x;
  }

  public int getY() {
    return this.y;
  }

  // Sets vão ser <default>
  Boolean setX(int x) {
    // Verifica se o valor está dentro dos limites do tabuleiro
    if (x < 0 || x >= Constants.TAMANHO_TABULEIRO) {
      return false;
    }
    this.x = x;
    return true;
  }

  Boolean setY(int y) {
    // Verifica se o valor está dentro dos limites do tabuleiro
    if (y < 0 || y >= Constants.TAMANHO_TABULEIRO) {
      return false;
    }
    this.y = y;
    return true;
  }

  // Método para calcular se a distância até uma posição destino está dentro do
  // deslocamento máximo permitido
  // retorna true se estiver dentro do limite, false caso contrário
  Boolean distanciaPermitida(int xDestino, int yDestino, int deslocamentoMaximo, int modo) {
    int distanciaX = Math.abs(this.x - xDestino);
    int distanciaY = Math.abs(this.y - yDestino);

    // Modo de detecção em formato de quadrado
    if (modo == 0) {
      return distanciaX <= deslocamentoMaximo && distanciaY <= deslocamentoMaximo;
    }

    // Modo de detecção em formato de losango
    int distanciaTotal = distanciaX + distanciaY;
    return distanciaTotal <= deslocamentoMaximo;
  }
}
