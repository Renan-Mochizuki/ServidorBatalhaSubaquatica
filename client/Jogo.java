package client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.Timer;
import java.io.*;
import java.net.*;
import java.util.function.Consumer;

/**
 * Janela principal do jogo.
 * Tela 1: input para digitar o nome do usuário e botão para continuar.
 * Tela 2 (Home): área do jogo + seção à direita com lista de jogadores, cada um
 * com um botão ao lado.
 */
public class Jogo extends JFrame {
  private final CardLayout cardLayout = new CardLayout();
  private final JPanel root = new JPanel(cardLayout);

  // Estado
  private String jogadorAtual;
  private final DefaultListModel<String> jogadoresModel = new DefaultListModel<>();

  // Componentes compartilhados
  private JPanel painelLista; // painel vertical com linhas (nome + botao)
  private JLabel infoJogadorLabel; // label com nome do jogador atual na barra superior
  private JTextArea mensagensArea; // área de mensagens na Home
  private JTextField hostField; // host do servidor
  private JTextField portField; // porta do servidor
  private JLabel loginStatusLabel; // status na tela de login
  private JButton loginButton; // botão de login para habilitar/desabilitar

  // --- Configurações / estado do tabuleiro ---
  private static final int BOARD_SIZE = 16;
  private JButton[][] boardButtons = new JButton[BOARD_SIZE][BOARD_SIZE];
  private boolean[][] atacado = new boolean[BOARD_SIZE][BOARD_SIZE];

  private int startX = 0, startY = 0; // coordenadas iniciais configuráveis
  private int playerX = 0, playerY = 0; // coordenadas atuais do jogador
  private int moveRange = 3; // alcance de movimento configurável
  private int attackRange = 3; // alcance de ataque configurável
  private int sonarRange = 4; // alcance do sonar configurável

  private boolean playerTurn = true; // controle de turno
  private JLabel turnoLabel; // label de status do turno
  private ClientConnection connection; // conexão com servidor (opcional)

  private enum Modo {
    MOVER, ATACAR, SONAR
  }

  private Modo modoAtual = Modo.MOVER;

  // Controles de configuração do jogo
  private JSpinner spStartX, spStartY, spMove, spAttack, spSonar;

  public Jogo() {
    super("Batalha Subaquática");
    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    setMinimumSize(new Dimension(900, 600));
    setLocationRelativeTo(null);

    root.add(criarTelaLogin(), "login");
    root.add(criarTelaHome(), "home");
    root.add(criarTelaJogo(), "game");
    setContentPane(root);

    // Dados de exemplo para a lista de jogadores (poderia vir do servidor depois)
    jogadoresModel.addElement("Ana");
    jogadoresModel.addElement("Bruno");
    jogadoresModel.addElement("Carla");
    jogadoresModel.addElement("Diego");

    cardLayout.show(root, "login");
  }

  private JPanel criarTelaLogin() {
    JPanel container = new JPanel(new GridBagLayout());
    container.setBorder(new EmptyBorder(24, 24, 24, 24));

    JPanel box = new JPanel();
    box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
    box.setBorder(new EmptyBorder(16, 16, 16, 16));
    box.setBackground(new Color(245, 248, 255));
    box.setMaximumSize(new Dimension(500, 200));

    JLabel titulo = new JLabel("Bem-vindo à Batalha Subaquática");
    titulo.setAlignmentX(Component.CENTER_ALIGNMENT);
    titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 20f));

    JLabel rotulo = new JLabel("Digite seu nome de jogador:");
    rotulo.setAlignmentX(Component.LEFT_ALIGNMENT);
    rotulo.setBorder(new EmptyBorder(12, 0, 4, 0));

    JTextField campoNome = new JTextField();
    campoNome.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

    // Linha de servidor/porta
    JPanel serverRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    JLabel lblServidor = new JLabel("Servidor:");
    hostField = new JTextField("localhost");
    hostField.setPreferredSize(new Dimension(140, 28));
    JLabel lblPorta = new JLabel("Porta:");
    portField = new JTextField("9876");
    portField.setPreferredSize(new Dimension(80, 28));
    serverRow.add(lblServidor);
    serverRow.add(hostField);
    serverRow.add(lblPorta);
    serverRow.add(portField);

    // Status do login
    loginStatusLabel = new JLabel(" ");
    loginStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    loginStatusLabel.setForeground(new Color(120, 0, 0));

    loginButton = new JButton("Entrar no jogo");
    loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
    loginButton.addActionListener((ActionEvent e) -> {
      String nome = campoNome.getText();
      if (nome == null || nome.trim().isEmpty()) {
        JOptionPane.showMessageDialog(this, "Por favor, digite um nome válido.",
            "Nome obrigatório", JOptionPane.WARNING_MESSAGE);
        return;
      }
      String host = hostField.getText().trim().isEmpty() ? "localhost" : hostField.getText().trim();
      int porta;
      try {
        porta = Integer.parseInt(portField.getText().trim());
      } catch (NumberFormatException ex) {
        JOptionPane.showMessageDialog(this, "Porta inválida.", "Erro", JOptionPane.ERROR_MESSAGE);
        return;
      }
      jogadorAtual = nome.trim();
      tentarConectarELogar(host, porta, jogadorAtual);
    });

    box.add(titulo);
    box.add(rotulo);
    box.add(campoNome);
    box.add(Box.createVerticalStrut(8));
    box.add(serverRow);
    box.add(Box.createVerticalStrut(12));
    box.add(loginButton);
    box.add(Box.createVerticalStrut(4));
    box.add(loginStatusLabel);

    container.add(box);
    return container;
  }

  private JPanel criarTelaHome() {
    JPanel home = new JPanel(new BorderLayout());

    // Área central (placeholder para o tabuleiro/ação do jogo)
    JPanel centro = new JPanel();
    centro.setLayout(new BoxLayout(centro, BoxLayout.Y_AXIS));
    centro.setBorder(new EmptyBorder(24, 24, 24, 24));

    JLabel placeholder = new JLabel("Área do jogo (Home)");
    placeholder.setAlignmentX(Component.CENTER_ALIGNMENT);
    placeholder.setFont(placeholder.getFont().deriveFont(Font.PLAIN, 18f));
    placeholder.setForeground(new Color(70, 70, 70));

    JButton irParaJogo = new JButton("Ir para o jogo");
    irParaJogo.setAlignmentX(Component.CENTER_ALIGNMENT);
    irParaJogo.addActionListener(e -> {
      iniciarPartida();
      cardLayout.show(root, "game");
    });

    centro.add(placeholder);
    centro.add(Box.createVerticalStrut(12));
    centro.add(irParaJogo);

    // Seção da direita: lista de jogadores com botões
    JPanel direita = new JPanel(new BorderLayout());
    direita.setPreferredSize(new Dimension(280, 600));
    direita.setBorder(new EmptyBorder(12, 12, 12, 12));

    JLabel titulo = new JLabel("Jogadores online");
    titulo.setBorder(new EmptyBorder(0, 0, 8, 0));
    titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 14f));

    painelLista = new JPanel();
    painelLista.setLayout(new BoxLayout(painelLista, BoxLayout.Y_AXIS));
    JScrollPane scroll = new JScrollPane(painelLista,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.getVerticalScrollBar().setUnitIncrement(16);

    direita.add(titulo, BorderLayout.NORTH);
    direita.add(scroll, BorderLayout.CENTER);

    // Barra superior com info do jogador atual
    JPanel topo = new JPanel(new BorderLayout());
    topo.setBorder(new EmptyBorder(8, 12, 8, 12));
    infoJogadorLabel = new JLabel("");
    topo.add(infoJogadorLabel, BorderLayout.WEST);

    // Caixa de mensagens na parte inferior
    JPanel mensagensPanel = new JPanel(new BorderLayout());
    mensagensPanel.setBorder(new EmptyBorder(8, 12, 12, 12));
    JLabel lblMsg = new JLabel("Mensagens");
    lblMsg.setFont(lblMsg.getFont().deriveFont(Font.BOLD));
    mensagensArea = new JTextArea(5, 20);
    mensagensArea.setEditable(false);
    mensagensArea.setLineWrap(true);
    mensagensArea.setWrapStyleWord(true);
    JScrollPane spMsgs = new JScrollPane(mensagensArea,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    mensagensPanel.add(lblMsg, BorderLayout.NORTH);
    mensagensPanel.add(spMsgs, BorderLayout.CENTER);

    home.add(topo, BorderLayout.NORTH);
    home.add(centro, BorderLayout.CENTER);
    home.add(direita, BorderLayout.EAST);
    home.add(mensagensPanel, BorderLayout.SOUTH);
    return home;
  }

  private void atualizarListaJogadores() {
    // Constrói lista final a exibir: jogadores de exemplo + atual (se não existir).
    List<String> nomes = new ArrayList<>();
    for (int i = 0; i < jogadoresModel.size(); i++) {
      String n = jogadoresModel.getElementAt(i);
      if (!Objects.equals(n, jogadorAtual)) {
        nomes.add(n);
      }
    }
    // Garante que o atual apareça no topo da lista, marcado.
    // Ele aparece visualmente, mas o botão ao lado fica desabilitado.
    if (infoJogadorLabel != null) {
      infoJogadorLabel.setText(jogadorAtual == null ? "" : ("Jogador: " + jogadorAtual));
    }
    painelLista.removeAll();

    // Atualiza caixa de mensagens da Home
    if (mensagensArea != null) {
      StringBuilder sb = new StringBuilder();
      sb.append("Ana: Olá!\n");
      sb.append("Bruno: Vamos jogar?\n");
      if (jogadorAtual != null && !jogadorAtual.isEmpty()) {
        sb.append(jogadorAtual).append(": Oi!\n");
      }
      mensagensArea.setText(sb.toString());
      mensagensArea.setCaretPosition(mensagensArea.getDocument().getLength());
    }

    // Primeiro, o próprio jogador
    if (jogadorAtual != null && !jogadorAtual.isEmpty()) {
      painelLista.add(criarLinhaJogador(jogadorAtual + " (você)", false));
      painelLista.add(Box.createVerticalStrut(6));
    }

    // Depois, os demais
    for (String nome : nomes) {
      painelLista.add(criarLinhaJogador(nome, true));
      painelLista.add(Box.createVerticalStrut(6));
    }

    painelLista.revalidate();
    painelLista.repaint();
  }

  private JPanel criarLinhaJogador(String nome, boolean habilitarBotao) {
    JPanel linha = new JPanel(new BorderLayout());
    linha.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(220, 225, 235)),
        new EmptyBorder(8, 8, 8, 8)));
    JLabel label = new JLabel(nome);
    JButton botao = new JButton("Ação");
    botao.setEnabled(habilitarBotao);
    botao.addActionListener(e -> {
      // Integração: exemplo de envio de mensagem ao servidor quando clica em um
      // jogador
      if (connection != null && connection.isConnected()) {
        connection.sendLine("INTERACT " + nome);
      } else {
        JOptionPane.showMessageDialog(
            this,
            "Sem conexão com o servidor (modo offline)",
            "Ação",
            JOptionPane.INFORMATION_MESSAGE);
      }
    });

    linha.add(label, BorderLayout.CENTER);
    linha.add(botao, BorderLayout.EAST);
    return linha;
  }

  // --- Tela de jogo (tabuleiro 16x16) ---
  private JPanel criarTelaJogo() {
    JPanel tela = new JPanel(new BorderLayout());
    tela.setBorder(new EmptyBorder(8, 8, 8, 8));

    // Barra superior com configurações
    JPanel configuracoes = new JPanel();
    configuracoes.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 4));

    spStartX = new JSpinner(new SpinnerNumberModel(startX, 0, BOARD_SIZE - 1, 1));
    spStartY = new JSpinner(new SpinnerNumberModel(startY, 0, BOARD_SIZE - 1, 1));
    spMove = new JSpinner(new SpinnerNumberModel(moveRange, 0, BOARD_SIZE, 1));
    spAttack = new JSpinner(new SpinnerNumberModel(attackRange, 0, BOARD_SIZE, 1));
    spSonar = new JSpinner(new SpinnerNumberModel(sonarRange, 0, BOARD_SIZE, 1));

    JButton aplicar = new JButton("Aplicar & Reiniciar");
    aplicar.addActionListener(e -> {
      startX = (int) spStartX.getValue();
      startY = (int) spStartY.getValue();
      moveRange = (int) spMove.getValue();
      attackRange = (int) spAttack.getValue();
      sonarRange = (int) spSonar.getValue();
      iniciarPartida();
    });

    configuracoes.add(new JLabel("Início X:"));
    configuracoes.add(spStartX);
    configuracoes.add(new JLabel("Y:"));
    configuracoes.add(spStartY);
    configuracoes.add(new JLabel("Movimento:"));
    configuracoes.add(spMove);
    configuracoes.add(new JLabel("Ataque:"));
    configuracoes.add(spAttack);
    configuracoes.add(new JLabel("Sonar:"));
    configuracoes.add(spSonar);
    configuracoes.add(aplicar);

    // Seleção de modo
    JToggleButton btMover = new JToggleButton("Mover");
    JToggleButton btAtacar = new JToggleButton("Atacar");
    JToggleButton btSonar = new JToggleButton("Sonar");
    ButtonGroup grupoModo = new ButtonGroup();
    grupoModo.add(btMover);
    grupoModo.add(btAtacar);
    grupoModo.add(btSonar);
    btMover.setSelected(true);
    btMover.addActionListener(e -> modoAtual = Modo.MOVER);
    btAtacar.addActionListener(e -> modoAtual = Modo.ATACAR);
    btSonar.addActionListener(e -> modoAtual = Modo.SONAR);

    configuracoes.add(new JLabel(" | Modo:"));
    configuracoes.add(btMover);
    configuracoes.add(btAtacar);
    configuracoes.add(btSonar);

    tela.add(configuracoes, BorderLayout.NORTH);

    // Tabuleiro (16x16)
    JPanel boardPanel = new JPanel(new GridLayout(BOARD_SIZE, BOARD_SIZE, 1, 1));
    boardPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
    for (int y = 0; y < BOARD_SIZE; y++) {
      for (int x = 0; x < BOARD_SIZE; x++) {
        final int cx = x, cy = y;
        JButton b = new JButton();
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setPreferredSize(new Dimension(32, 32));
        b.setFocusPainted(false);
        b.addActionListener(e -> onCellClick(cx, cy));
        boardButtons[y][x] = b;
        boardPanel.add(b);
      }
    }
    tela.add(boardPanel, BorderLayout.CENTER);

    // Barra inferior: status de turno
    JPanel status = new JPanel(new BorderLayout());
    status.setBorder(new EmptyBorder(4, 8, 4, 8));
    turnoLabel = new JLabel("Turno: Jogador");
    status.add(turnoLabel, BorderLayout.WEST);

    JButton voltarHome = new JButton("Voltar para Home");
    voltarHome.addActionListener(e -> cardLayout.show(root, "home"));
    status.add(voltarHome, BorderLayout.EAST);

    tela.add(status, BorderLayout.SOUTH);
    return tela;
  }

  private void iniciarPartida() {
    // reinicia estado
    for (int y = 0; y < BOARD_SIZE; y++) {
      for (int x = 0; x < BOARD_SIZE; x++) {
        atacado[y][x] = false;
      }
    }
    playerX = clamp(startX, 0, BOARD_SIZE - 1);
    playerY = clamp(startY, 0, BOARD_SIZE - 1);
    playerTurn = true;
    atualizarStatusTurno();
    atualizarTabuleiro();
  }

  private void onCellClick(int x, int y) {
    if (!playerTurn)
      return; // aguarda inimigo

    switch (modoAtual) {
      case MOVER:
        if (alcance(playerX, playerY, x, y) <= moveRange) {
          playerX = x;
          playerY = y;
          if (connection != null && connection.isConnected()) {
            connection.sendLine("MOVE " + x + " " + y);
          }
          atualizarTabuleiro();
          fimDoTurnoDoJogador();
        } else {
          beepMsg("Fora do alcance de movimento.");
        }
        break;
      case ATACAR:
        if (alcance(playerX, playerY, x, y) <= attackRange) {
          atacado[y][x] = true;
          if (connection != null && connection.isConnected()) {
            connection.sendLine("ATTACK " + x + " " + y);
          }
          atualizarTabuleiro();
          // Aqui poderia haver lógica de acerto/erro
          fimDoTurnoDoJogador();
        } else {
          beepMsg("Fora do alcance de ataque.");
        }
        break;
      case SONAR:
        if (alcance(playerX, playerY, x, y) <= sonarRange) {
          if (connection != null && connection.isConnected()) {
            connection.sendLine("SONAR " + x + " " + y);
          } else {
            JOptionPane.showMessageDialog(this,
                "Sonar ping em (" + x + ", " + y + ")",
                "Sonar", JOptionPane.INFORMATION_MESSAGE);
          }
          fimDoTurnoDoJogador();
        } else {
          beepMsg("Fora do alcance do sonar.");
        }
        break;
    }
  }

  private void fimDoTurnoDoJogador() {
    playerTurn = false;
    atualizarStatusTurno();
    // Integração com servidor: informe fim do turno para o servidor decidir.
    if (connection != null && connection.isConnected()) {
      connection.sendLine("END_TURN");
      // Aguarde mensagens do servidor; quando receber "TURN_PLAYER", reabilitará no
      // handler.
    } else {
      // Offline: simula turno do inimigo por 2 segundos.
      Timer t = new Timer(2000, e -> {
        playerTurn = true;
        atualizarStatusTurno();
      });
      t.setRepeats(false);
      t.start();
    }
  }

  private void atualizarStatusTurno() {
    if (turnoLabel != null) {
      turnoLabel.setText(playerTurn ? "Turno: Jogador" : "Turno: Inimigo...");
    }
    // Opcional: desabilitar o tabuleiro quando não é o turno do jogador
    boolean enabled = playerTurn;
    for (int y = 0; y < BOARD_SIZE; y++) {
      for (int x = 0; x < BOARD_SIZE; x++) {
        if (boardButtons[y][x] != null)
          boardButtons[y][x].setEnabled(enabled);
      }
    }
  }

  private void atualizarTabuleiro() {
    for (int y = 0; y < BOARD_SIZE; y++) {
      for (int x = 0; x < BOARD_SIZE; x++) {
        JButton b = boardButtons[y][x];
        if (b == null)
          continue;
        b.setText("");
        if (x == playerX && y == playerY) {
          b.setBackground(new Color(90, 160, 255)); // jogador
        } else if (atacado[y][x]) {
          b.setBackground(new Color(240, 120, 120)); // atacado
          b.setText("X");
        } else {
          b.setBackground(Color.WHITE);
        }
      }
    }
  }

  private int alcance(int x1, int y1, int x2, int y2) {
    // Distância Chebyshev: max(|dx|, |dy|)
    return Math.max(Math.abs(x1 - x2), Math.abs(y1 - y2));
  }

  private int clamp(int v, int min, int max) {
    return Math.max(min, Math.min(max, v));
  }

  private void beepMsg(String msg) {
    Toolkit.getDefaultToolkit().beep();
    JOptionPane.showMessageDialog(this, msg, "Aviso", JOptionPane.WARNING_MESSAGE);
  }

  // === Integração com servidor ===
  private void tentarConectarELogar(String host, int port, String nome) {
    loginButton.setEnabled(false);
    loginStatusLabel.setForeground(new Color(0, 70, 120));
    loginStatusLabel.setText("Conectando em " + host + ":" + port + "...");

    SwingWorker<Void, Void> worker = new SwingWorker<>() {
      private String erro;

      @Override
      protected Void doInBackground() {
        try {
          if (connection != null) {
            try {
              connection.close();
            } catch (Exception ignore) {
            }
          }
          connection = new ClientConnection(host, port);
          connection.setOnMessage(Jogo.this::handleServerMessage);
          connection.connect();

          // Envia mensagem de login – ajuste o protocolo conforme seu servidor
          connection.sendLine("LOGIN " + nome);
        } catch (IOException ex) {
          erro = ex.getMessage();
        }
        return null;
      }

      @Override
      protected void done() {
        if (erro != null) {
          loginStatusLabel.setForeground(new Color(120, 0, 0));
          loginStatusLabel.setText("Falha ao conectar: " + erro);
          loginButton.setEnabled(true);
        } else {
          loginStatusLabel.setForeground(new Color(0, 120, 0));
          loginStatusLabel.setText("Conectado. Aguardando resposta do servidor...");
          // IMPORTANTE: a decisão de avançar para a outra tela OU mostrar erro
          // fica no método handleServerMessage(), quando processarmos
          // LOGIN_OK/LOGIN_FAIL.
          // Isso evita travar a UI aguardando resposta.
        }
      }
    };
    worker.execute();
  }

  // Ponto central para tratar mensagens vindas do servidor
  private void handleServerMessage(String line) {
    if (line == null)
      return;
    // Garanta que atualizações de UI ocorram na EDT
    SwingUtilities.invokeLater(() -> {
      // Exemplos de protocolo – ajuste conforme o seu servidor
      if (line.startsWith("LOGIN_OK")) {
        // Decisão de avançar para HOME
        atualizarListaJogadores();
        cardLayout.show(root, "home");
        loginButton.setEnabled(true);
        loginStatusLabel.setText(" ");
      } else if (line.startsWith("LOGIN_FAIL")) {
        // Exemplo: LOGIN_FAIL Motivo do erro
        String motivo = line.length() > 10 ? line.substring(10).trim() : "Cadastro não realizado";
        loginStatusLabel.setForeground(new Color(120, 0, 0));
        loginStatusLabel.setText(motivo);
        JOptionPane.showMessageDialog(this, motivo, "Cadastro não realizado", JOptionPane.WARNING_MESSAGE);
        loginButton.setEnabled(true);
      } else if (line.startsWith("HOME_MSG ")) {
        // Exemplo: HOME_MSG Ana: Olá!
        if (mensagensArea != null) {
          mensagensArea.append(line.substring(9) + "\n");
          mensagensArea.setCaretPosition(mensagensArea.getDocument().getLength());
        }
      } else if (line.startsWith("ENEMY_ATTACK ")) {
        // Exemplo: ENEMY_ATTACK x y
        String[] p = line.split(" ");
        if (p.length >= 3) {
          try {
            int x = Integer.parseInt(p[1]);
            int y = Integer.parseInt(p[2]);
            if (x >= 0 && x < BOARD_SIZE && y >= 0 && y < BOARD_SIZE) {
              atacado[y][x] = true;
              atualizarTabuleiro();
            }
          } catch (NumberFormatException ignored) {
          }
        }
      } else if (line.equals("TURN_PLAYER")) {
        // Servidor devolveu o turno ao jogador
        playerTurn = true;
        atualizarStatusTurno();
      } else if (line.equals("TURN_ENEMY")) {
        playerTurn = false;
        atualizarStatusTurno();
      }
    });
  }

  // Cliente simples baseado em socket, com thread de leitura
  private static class ClientConnection {
    private final String host;
    private final int port;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Thread readerThread;
    private volatile boolean running;
    private Consumer<String> onMessage;

    ClientConnection(String host, int port) {
      this.host = host;
      this.port = port;
    }

    void setOnMessage(Consumer<String> handler) {
      this.onMessage = handler;
    }

    void connect() throws IOException {
      socket = new Socket(host, port);
      in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
      out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
      running = true;
      readerThread = new Thread(() -> {
        try {
          String line;
          while (running && (line = in.readLine()) != null) {
            if (onMessage != null)
              onMessage.accept(line);
          }
        } catch (IOException ignored) {
        } finally {
          running = false;
          try {
            socket.close();
          } catch (Exception ignore) {
          }
        }
      }, "Server-Reader");
      readerThread.setDaemon(true);
      readerThread.start();
    }

    boolean isConnected() {
      return socket != null && socket.isConnected() && !socket.isClosed();
    }

    synchronized void sendLine(String text) {
      if (out != null) {
        out.print(text + "\n");
        out.flush();
      }
    }

    void close() throws IOException {
      running = false;
      if (socket != null)
        socket.close();
    }
  }

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
      // Usa o look and feel do sistema, se disponível
      try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      } catch (Exception ignored) {
      }

      new Jogo().setVisible(true);
    });
  }
}
