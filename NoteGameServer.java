import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Random;

public class NoteGameServer {

  private static final List<String[]> SONG = List.of(
      new String[] { "ド", "ド", "ソ", "ソ", "ラ", "ラ", "ソ" },
      new String[] { "ファ", "ファ", "ミ", "ミ", "レ", "レ", "ド" });

  public static void main(String[] args) {

    int port = 5000;

    try (ServerSocket serverSocket = new ServerSocket(port)) {

      System.out.println("音階あてゲームサーバ起動: port=" + port);
      System.out.println("プレイヤー接続待ち...");

      Socket p1Socket = serverSocket.accept();
      System.out.println("プレイヤー1接続: " + p1Socket.getInetAddress());

      Socket p2Socket = serverSocket.accept();
      System.out.println("プレイヤー2接続: " + p2Socket.getInetAddress());

      PlayerConnection player1 = new PlayerConnection(p1Socket);
      PlayerConnection player2 = new PlayerConnection(p2Socket);

      int firstPlayer = new Random().nextBoolean() ? 1 : 2;
      int currentPlayer = firstPlayer;

      player1.sendLine("ROLE 1");
      player2.sendLine("ROLE 2");

      player1.sendLine("START");
      player2.sendLine("START");

      // ★ 最初の段階の MIDI を送信
      player1.sendMidi("kirakira1.mid");
      player2.sendMidi("kirakira1.mid");

      System.out.println("先攻プレイヤー: " + firstPlayer);

      int[] measureIndex = { 0, 0 };

      boolean finished = false;
      int winner = 0;

      while (!finished) {

        PlayerConnection active = (currentPlayer == 1) ? player1 : player2;
        PlayerConnection waiting = (currentPlayer == 1) ? player2 : player1;

        String[] measureNotes = SONG.get(measureIndex[currentPlayer - 1]);

        StringBuilder sb = new StringBuilder();
        sb.append("MEASURE ").append(measureIndex[currentPlayer - 1]);

        for (String note : measureNotes) {
          sb.append(" ").append(note);
        }

        active.sendLine(sb.toString());
        waiting.sendLine("WAIT");

        System.out.println("送信: " + sb + " -> Player" + currentPlayer);

        String answerLine = active.readLine();

        if (answerLine == null) {
          System.out.println("Player" + currentPlayer + " 切断。ゲーム終了。");
          break;
        }

        System.out.println("受信: " + answerLine);

        if (!answerLine.startsWith("ANSWER")) {
          active.sendLine("RESULT 0 0");
          currentPlayer = (currentPlayer == 1) ? 2 : 1;
          continue;
        }

        String[] parts = answerLine.split("\\s+");
        String[] answerNotes = new String[parts.length - 1];
        System.arraycopy(parts, 1, answerNotes, 0, answerNotes.length);

        boolean correct = isCorrect(measureNotes, answerNotes);
        int matchCount = countMatches(measureNotes, answerNotes);
        String[] colors = judgeWordle(answerNotes, measureNotes);

        sb.setLength(0);
        sb.append("RESULT");
        for (String c : colors)
          sb.append(" ").append(c);

        active.sendLine(sb.toString());

        System.out.println("判定: correct=" + correct + ", match=" + matchCount);

        if (correct) {

          measureIndex[currentPlayer - 1]++;

          if (measureIndex[currentPlayer - 1] >= SONG.size()) {

            finished = true;
            winner = currentPlayer;

          } else {

            // ★★★ 次の段階の MIDI を送信 ★★★
            if (measureIndex[currentPlayer - 1] == 1) {
              active.sendMidi("kirakira2.mid");
            }

            active.sendLine("NEXT");
          }
        }

        currentPlayer = (currentPlayer == 1) ? 2 : 1;
      }

      if (winner != 0) {
        System.out.println("勝者: Player" + winner);
        player1.sendLine("WINNER " + winner);
        player2.sendLine("WINNER " + winner);
      }

      player1.sendLine("GAMEEND");
      player2.sendLine("GAMEEND");

      player1.close();
      player2.close();

      System.out.println("ゲーム終了。サーバ停止。");

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static boolean isCorrect(String[] measure, String[] answer) {
    if (measure.length != answer.length)
      return false;
    for (int i = 0; i < measure.length; i++) {
      if (!measure[i].equals(answer[i]))
        return false;
    }
    return true;
  }

  private static int countMatches(String[] measure, String[] answer) {
    int len = Math.min(measure.length, answer.length);
    int count = 0;
    for (int i = 0; i < len; i++) {
      if (measure[i].equals(answer[i]))
        count++;
    }
    return count;
  }

  private static String[] judgeWordle(String[] answer, String[] correct) {
    String[] result = new String[answer.length];
    boolean[] used = new boolean[correct.length];

    for (int i = 0; i < answer.length; i++) {
      if (i < correct.length && answer[i].equals(correct[i])) {
        result[i] = "G";
        used[i] = true;
      }
    }

    for (int i = 0; i < answer.length; i++) {
      if (result[i] != null)
        continue;

      boolean found = false;
      for (int j = 0; j < correct.length; j++) {
        if (!used[j] && answer[i].equals(correct[j])) {
          found = true;
          used[j] = true;
          break;
        }
      }

      result[i] = found ? "Y" : "B";
    }

    return result;
  }

  private static class PlayerConnection {

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;

    PlayerConnection(Socket socket) throws IOException {
      this.socket = socket;
      this.in = new DataInputStream(socket.getInputStream());
      this.out = new DataOutputStream(socket.getOutputStream());
    }

    void sendLine(String line) {
      try {
        out.writeUTF(line);
        out.flush();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    String readLine() throws IOException {
      return in.readUTF();
    }

    void sendMidi(String filename) throws IOException {

      File file = new File(filename);

      sendLine("MIDI");
      out.writeLong(file.length());

      FileInputStream fis = new FileInputStream(file);
      byte[] buffer = new byte[4096];

      int len;
      while ((len = fis.read(buffer)) != -1) {
        out.write(buffer, 0, len);
      }

      out.flush();
      fis.close();
    }

    void close() {
      try {
        in.close();
      } catch (IOException ignored) {
      }
      try {
        out.close();
      } catch (IOException ignored) {
      }
      try {
        socket.close();
      } catch (IOException ignored) {
      }
    }
  }
}
