package classes;

import java.util.HashMap;
import java.util.Map;

// -- TODAS ROTAS --
// CADASTRAR <nomeCliente>
// LISTARPARTIDAS
// LISTARJOGADORES
// ENTRARPARTIDA <nome> <token> <idPartida>
// DESAFIAR <nomeDesafiante> <token> <nomeDesafiado>
// ACEITARDESAFIO <nomeDesafiado> <token> <nomeDesafiante>
// RECUSARDESAFIO <nomeDesafiado> <token> <nomeDesafiante>
// CHATGLOBAL <nome> <token> <mensagem>
// CHATPARTIDA <nome> <token> <mensagem>
// CHATJOGADOR <nome> <token> <nomeDestinatario> <mensagem>
// PRONTOPARTIDA <nome> <token>
// MOVER <nome> <token> <posicaoX> <posicaoY> <modoDeslocamento>
// ATACAR <nome> <token> <posicaoX> <posicaoY> <modoDeslocamento>
// SONAR <nome> <token> <posicaoX> <posicaoY> <modoDeslocamento>
// PASSAR <nome> <token>
// SAIRPARTIDA <nome> <token>
// SAIR <nome> <token>
// KEEPALIVE <nome> <token>

// HashMap apenas para permitir ao cliente enviar comandos com textos diferentes
public class Tradutor {
  Map<String, String> tradutor;

  public Tradutor() {
    tradutor = new HashMap<String, String>();
    tradutor.put("CADASTRO", "CADASTRAR");
    tradutor.put("DESAFIO", "DESAFIAR");
    tradutor.put("PRONTO", "PRONTOPARTIDA");
    tradutor.put("CHAT", "CHATGLOBAL");
    tradutor.put("MOVIMENTO", "MOVER");
    tradutor.put("ATAQUE", "ATACAR");
    tradutor.put("MISSIL", "ATACAR");
    tradutor.put("DISPOSITIVOPROXIMIDADE", "SONAR");
    tradutor.put("PULAR", "PASSAR");
    tradutor.put("PING", "KEEPALIVE");
    tradutor.put("REGISTER", "CADASTRAR");
    tradutor.put("LISTGAMES", "LISTARPARTIDAS");
    tradutor.put("LISTPLAYERS", "LISTARJOGADORES");
    tradutor.put("JOINGAME", "ENTRARPARTIDA");
    tradutor.put("CHALLENGE", "DESAFIAR");
    tradutor.put("ACCEPTCHALLENGE", "ACEITARDESAFIO");
    tradutor.put("REJECTCHALLENGE", "RECUSARDESAFIO");
    tradutor.put("GLOBALCHAT", "CHATGLOBAL");
    tradutor.put("GAMECHAT", "CHATPARTIDA");
    tradutor.put("PLAYERCHAT", "CHATJOGADOR");
    tradutor.put("MOVE", "MOVER");
    tradutor.put("ATTACK", "ATACAR");
    tradutor.put("SONAR", "SONAR");
    tradutor.put("PASS", "PASSAR");
    tradutor.put("LEAVEGAME", "SAIRPARTIDA");
    tradutor.put("EXIT", "SAIR");
    tradutor.put(Constants.TIPOCADASTRAR, "CADASTRAR");
    tradutor.put(Constants.TIPOLISTARPARTIDAS, "LISTARPARTIDAS");
    tradutor.put(Constants.TIPOLISTARJOGADORES, "LISTARJOGADORES");
    tradutor.put(Constants.TIPOENTRARPARTIDA, "ENTRARPARTIDA");
    tradutor.put(Constants.TIPODESAFIAR, "DESAFIAR");
    tradutor.put(Constants.TIPOACEITARDESAFIO, "ACEITARDESAFIO");
    tradutor.put(Constants.TIPORECUSARDESAFIO, "RECUSARDESAFIO");
    tradutor.put(Constants.TIPOCHATGLOBAL, "CHATGLOBAL");
    tradutor.put(Constants.TIPOCHATPARTIDA, "CHATPARTIDA");
    tradutor.put(Constants.TIPOCHATJOGADOR, "CHATJOGADOR");
    tradutor.put(Constants.TIPOPRONTOPARTIDA, "PRONTOPARTIDA");
    tradutor.put(Constants.TIPOMOVER, "MOVER");
    tradutor.put(Constants.TIPOATACAR, "ATACAR");
    tradutor.put(Constants.TIPOSONAR, "SONAR");
    tradutor.put(Constants.TIPOPASSAR, "PASSAR");
    tradutor.put(Constants.TIPOSAIRPARTIDA, "SAIRPARTIDA");
    tradutor.put(Constants.TIPOSAIR, "SAIR");
    tradutor.put(Constants.TIPOKEEPALIVE, "KEEPALIVE");
  }

  public boolean containsKey(String key) {
    return tradutor.containsKey(key);
  }

  public String get(String key) {
    return tradutor.get(key);
  }
}
