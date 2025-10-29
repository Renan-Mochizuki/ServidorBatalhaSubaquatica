package classes;

import java.io.*;
import java.net.*;

public class Cliente {
  private String nome;
  private String token;
  private int idPartida; // ID da partida que o cliente está participando
  private String jogadorDesafiado; // Nome do jogador que este cliente desafiou
  private Socket connectionSocket;

  public Cliente(String nome, String token, Socket connectionSocket) {
    this.nome = nome;
    this.token = token;
    this.idPartida = -1;
    this.jogadorDesafiado = null;
    this.connectionSocket = connectionSocket;
  }

  public String getNome() {
    return this.nome;
  }

  public boolean validarToken(String token) {
    return this.token.equals(token);
  }

  public int getIdPartida() {
    return this.idPartida;
  }

  public void setIdPartida(int idPartida) {
    this.idPartida = idPartida;
  }

  public Socket getConnectionSocket() {
    return this.connectionSocket;
  }

  public void setConnectionSocket(Socket socket) {
    this.connectionSocket = socket;
  }

  public String getJogadorDesafiado() {
    return this.jogadorDesafiado;
  }

  public void setJogadorDesafiado(String jogadorDesafiado) {
    this.jogadorDesafiado = jogadorDesafiado;
  }

  // Esse método vai ser <default> para ser acessado pela classe filha (Jogador)
  String getToken() {
    return this.token;
  }

  // Enviar uma linha para o cliente de maneira mais segura
  // Código gerado pelo Copilot ao pedir por um método único de envio de mensagem
  // ao cliente
  public boolean enviarLinha(String tipo, String codigo, String mensagem, String valor) {
    synchronized (this) {
      Socket socket = this.connectionSocket;
      if (socket == null || socket.isClosed() || socket.isOutputShutdown()) {
        return false;
      }
      try {
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        String spr = Constants.SEPARADOR;
        String linha = tipo + spr + codigo + spr + mensagem + spr + valor;
        out.writeBytes(linha + "\n");
        out.flush();
        return true;
      } catch (IOException e) {
        System.err.println("Erro ao enviar para o cliente " + this.nome + ": " + e.getMessage());
        try {
          socket.close();
        } catch (IOException ignored) {
        }
        return false;
      }
    }
  }
}