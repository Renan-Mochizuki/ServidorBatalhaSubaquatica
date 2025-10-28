package classes;

import java.util.*;

public class Partida {
  // Esses atributos são default para poderem ser acessados pela classe filha
  // (JogoPartida)
  private int id;
  private List<Cliente> clientes;
  private Boolean andamento; // true para partida em andamento

  public Partida(int id) {
    this.id = id;
    this.clientes = new ArrayList<>();
    this.andamento = false;
  }

  public synchronized Boolean adicionarCliente(Cliente cliente) {
    if (this.clientes.size() >= Constants.NUMERO_JOGADORES) {
      return false;
    }
    this.clientes.add(cliente);
    return true;
  }

  public synchronized Boolean removerCliente(Cliente cliente) {
    return this.clientes.remove(cliente);
  }

  public int getId() {
    return this.id;
  }

  public synchronized Boolean getAndamento() {
    return this.andamento;
  }

  public synchronized String getInfo() {
    return "id:" + this.id + ",andamento:" + this.andamento + ",numjogadores:" + this.clientes.size();
  }

  public synchronized Boolean partidaLotada() {
    return this.clientes.size() >= Constants.NUMERO_JOGADORES;
  }

  public synchronized Boolean iniciarPartida() {
    // Se ainda não houver clientes suficientes ou já está em andamento, não inicia
    // a partida
    if (this.clientes.size() < Constants.NUMERO_JOGADORES || this.andamento == true) {
      return false;
    }
    this.andamento = true;
    return true;
  }

  public synchronized List<Cliente> getClientes() {
    return new ArrayList<>(this.clientes);
  }

  // Método default para ser acessado pela classe filha (JogoPartida)
  synchronized void setAndamento(Boolean andamento) {
    this.andamento = andamento;
  }

  synchronized void limparClientes() {
    this.clientes.clear();
  }
}
