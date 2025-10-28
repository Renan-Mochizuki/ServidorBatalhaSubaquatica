package classes;

import java.net.Socket;

public class Jogador extends Cliente {
  private Posicao posicao;
  private int numDispositivos;
  private int numMaxDispositivos;
  private int numMisseis;

  public Jogador(String nome, String token, Socket connectionSocket, int x, int y, int numMaxDispositivos) {
    super(nome, token, connectionSocket);
    this.posicao = new Posicao(x, y);
    this.numDispositivos = 0;
    this.numMaxDispositivos = numMaxDispositivos;
    this.numMisseis = 0;
  }

  public Posicao getPosicao() {
    return this.posicao;
  }

  public int getNumDispositivos() {
    return this.numDispositivos;
  }

  public int getNumMaxDispositivos() {
    return this.numMaxDispositivos;
  }

  public int getNumMisseis() {
    return this.numMisseis;
  }

  // Método para mover o jogador de acordo com um deslocamento em X e Y
  public Boolean mover(int posicaoX, int posicaoY) {
    // Interpreta posicaoX/posicaoY como coordenadas de destino (absolutas)
    // Verifica se o destino está dentro do deslocamento máximo a partir da posição
    // atual
    if (!this.getPosicao().distanciaPermitida(posicaoX, posicaoY, Constants.DISTANCIA_MOVIMENTO,
        Constants.MODO_DISTANCIA_MOVIMENTO)) {
      return false;
    }

    // Se a posição atual for igual à posição destino, não move
    if(this.getPosicao().getX() == posicaoX && this.getPosicao().getY() == posicaoY){
      return false;
    }

    // Verifica limites do tabuleiro
    if (posicaoX < 0 || posicaoX >= Constants.TAMANHO_TABULEIRO || posicaoY < 0
        || posicaoY >= Constants.TAMANHO_TABULEIRO) {
      return false;
    }

    // Atualiza posição para o destino
    this.posicao.setX(posicaoX);
    this.posicao.setY(posicaoY);
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

  // Método para adicionar míssil
  public void adicionarMissil() {
    this.numMisseis++;
  }

  // Método para remover míssil
  public Boolean removerMissil() {
    if (this.numMisseis > 0) {
      this.numMisseis--;
      return true;
    }
    return false;
  }
}