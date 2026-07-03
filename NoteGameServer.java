import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Random;

public class NoteGameServer {

  // ドレミファソラシドだけで構成された曲（例: きらきら星風）
  private static final List<String[]> SONG = List.of(
      new String[] { "ド", "ド", "ソ", "ソ", "ラ", "ラ", "ソ" },
      new String[] { "ファ", "ファ", "ミ", "ミ", "レ", "レ", "ド" },
      new String[] { "ソ", "ソ", "ファ", "ファ", "ミ", "ミ", "レ" },
      new String[] { "ソ", "ソ", "ファ", "ファ", "ミ", "ミ", "レ" },
      new String[] { "ド", "ド", "ソ", "ソ", "ラ", "ラ", "ソ" },
      new String[] { "ファ", "ファ", "ミ", "ミ", "レ", "レ", "ド" });

  public static void main(String[] args) {
    int port = 5000;

    try (ServerSocket serverSocket = new ServerSocket(port)) {
      System.out.println("音階あてゲームサーバ起動: port=" + port);
      System.out.println("プレイヤー接続待ち...");

      // プレイヤー1接続
      Socket p1Socket = serverSocket.accept();
      System.out.println("プレイヤー1接続: " + p1Socket.getInetAddress());

      // プレイヤー2接続
      Socket p2Socket = serverSocket.accept();
      System.out.println("プレイヤー2接続: " + p2Socket.getInetAddress());

      PlayerConnection player1 = new PlayerConnection(p1Socket);
      PlayerConnection player2 = new PlayerConnection(p2Socket);

      // 先攻後攻をランダム決定
      int firstPlayer = new Random().nextBoolean() ? 1 : 2;
      int currentPlayer = firstPlayer;

      // ROLE 通知
      player1.sendLine("ROLE 1");
      player2.sendLine("ROLE 2");

      // ゲーム開始通知
      player1.sendLine("START");
      player2.sendLine("START");

      System.out.println("先攻プレイヤー: " + firstPlayer);

      int[] measureIndex = {0,0};
      boolean finished = false;
      int winner = 0;

      while (!finished) {
        PlayerConnection active = (currentPlayer == 1) ? player1 : player2;
        PlayerConnection waiting = (currentPlayer == 1) ? player2 : player1;

        // ターン側に小節と音階を送信、相手には WAIT
        String[] measureNotes = SONG.get(measureIndex[currentPlayer-1]);
        StringBuilder sb = new StringBuilder();
        sb.append("MEASURE ").append(measureIndex)[currentPlayer-1];
        for (String note : measureNotes) {
          sb.append(" ").append(note);
        }
        active.sendLine(sb.toString());
        waiting.sendLine("WAIT");

        System.out.println("送信: " + sb + " -> Player" + currentPlayer);

        // 回答受信
        String answerLine = active.readLine();
        if (answerLine == null) {
          System.out.println("Player" + currentPlayer + " 切断。ゲーム終了。");
          break;
        }
        System.out.println("受信: " + answerLine + " <- Player" + currentPlayer);

        if (!answerLine.startsWith("ANSWER")) {
          // 不正なメッセージは不正解扱いでターン交代
          active.sendLine("RESULT 0 0");
          currentPlayer = (currentPlayer == 1) ? 2 : 1;
          continue;
        }

        String[] parts = answerLine.split("\\s+");
        String[] answerNotes = new String[parts.length - 1];
        System.arraycopy(parts, 1, answerNotes, 0, answerNotes.length);

        // 判定
        boolean correct = isCorrect(measureNotes, answerNotes);
        int matchCount = countMatches(measureNotes, answerNotes);

        active.sendLine("RESULT " + (correct ? 1 : 0) + " " + matchCount);
        System.out.println("判定: correct=" + correct + ", match=" + matchCount);

        if (correct) {
          // 最終小節なら勝利
          if (measureIndex[currentPlayer-1] == SONG.size() - 1) {
            finished = true;
            winner = currentPlayer;
            break;
          }
          active.sendLine("RESULT 1 " + matchCount);
          
        } else {
          active.sendLine("RESULT 0 " + matchCount);
        }
         // 正解・不正解に関係なくターン交代
                currentPlayer = (currentPlayer == 1) ? 2 : 1;
      }

      if (winner != 0) {
        System.out.println("勝者: Player" + winner);
        player1.sendLine("WINNER " + winner);
        player2.sendLine("WINNER " + winner);
      }

      player1.close();
      player2.close();
      System.out.println("ゲーム終了。サーバ停止。");

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  // 完全一致判定
  private static boolean isCorrect(String[] measure, String[] answer) {
    if (measure.length != answer.length)
      return false;
    for (int i = 0; i < measure.length; i++) {
      if (!measure[i].equals(answer[i]))
        return false;
    }
    return true;
  }

  // 位置一致している音階の数
  private static int countMatches(String[] measure, String[] answer) {
    int len = Math.min(measure.length, answer.length);
    int count = 0;
    for (int i = 0; i < len; i++) {
      if (measure[i].equals(answer[i]))
        count++;
    }
    return count;
  }

  // プレイヤーとの接続ラッパ
  private static class PlayerConnection {
    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;

    PlayerConnection(Socket socket) throws IOException {
      this.socket = socket;
      this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
      this.out = new PrintWriter(socket.getOutputStream(), true);
    }

    void sendLine(String line) {
      out.println(line);
    }

    String readLine() throws IOException {
      return in.readLine();
    }

    void close() {
      try {
        in.close();
      } catch (IOException ignored) {
      }
      out.close();
      try {
        socket.close();
      } catch (IOException ignored) {
      }
    }
  }
}
