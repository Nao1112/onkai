import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * 音階あてゲーム GUIクライアント
 * 24FI024 大村直 / 24FI059 塩手凛
 */
public class NoteGameClientGUI extends Application {

  private Socket socket;
  private BufferedReader in;
  private PrintWriter out;

  private Label statusLabel;
  private TextField answerField;
  private int myRole = 0;

  private final String[] NOTES = { "ド", "レ", "ミ", "ファ", "ソ", "ラ", "シ", "ド" };

  @Override
  public void start(Stage stage) {
    statusLabel = new Label("サーバ接続中...");
    answerField = new TextField();
    answerField.setEditable(false);

    // 音階ボタン
    HBox noteButtons = new HBox(10);
    noteButtons.setAlignment(Pos.CENTER);
    for (String note : NOTES) {
      Button btn = new Button(note);
      btn.setPrefWidth(60);
      btn.setOnAction(e -> {
        answerField.setText(answerField.getText() + " " + note);
      });
      noteButtons.getChildren().add(btn);
    }

    // 送信ボタン
    Button sendBtn = new Button("送信");
    sendBtn.setPrefWidth(120);
    sendBtn.setOnAction(e -> sendAnswer());

    VBox root = new VBox(20, statusLabel, answerField, noteButtons, sendBtn);
    root.setPadding(new Insets(20));
    root.setAlignment(Pos.CENTER);

    stage.setScene(new Scene(root, 400, 300));
    stage.setTitle("音階あてゲーム クライアント");
    stage.show();

    // 通信開始
    new Thread(this::connectToServer).start();
  }

  private void connectToServer() {
    try {
      socket = new Socket("localhost", 5000);
      in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
      out = new PrintWriter(socket.getOutputStream(), true);

      Platform.runLater(() -> statusLabel.setText("サーバに接続しました。"));

      // サーバからのメッセージ受信ループ
      while (true) {
        String line = in.readLine();
        if (line == null)
          break;

        System.out.println("[受信] " + line);
        handleServerMessage(line);
      }

    } catch (IOException e) {
      Platform.runLater(() -> statusLabel.setText("サーバ接続エラー"));
    }
  }

  private void handleServerMessage(String line) {
    String[] parts = line.split("\\s+");
    String cmd = parts[0];

    switch (cmd) {

      case "ROLE":
        myRole = Integer.parseInt(parts[1]);
        Platform.runLater(() -> statusLabel.setText("あなたは Player" + myRole));
        break;

      case "START":
        Platform.runLater(() -> statusLabel.setText("ゲーム開始"));
        break;

      case "MEASURE":
        int idx = Integer.parseInt(parts[1]);
        Platform.runLater(() -> {
          answerField.setText("");
          statusLabel.setText("あなたのターン（小節 " + idx + "）");
        });
        break;

      case "WAIT":
        Platform.runLater(() -> statusLabel.setText("相手のターンです"));
        break;

      case "RESULT":
        int correct = Integer.parseInt(parts[1]);
        int match = Integer.parseInt(parts[2]);

        Platform.runLater(() -> {
          if (correct == 1) {
            statusLabel.setText("◎ 正解！ 一致数: " + match);
          } else {
            statusLabel.setText("× 不正解 一致数: " + match);
          }
        });
        break;

      case "WINNER":
        int winner = Integer.parseInt(parts[1]);
        Platform.runLater(() -> {
          if (winner == myRole) {
            statusLabel.setText("🎉 あなたの勝ち！");
          } else {
            statusLabel.setText("😢 あなたの負け…");
          }
        });
        break;

      default:
        Platform.runLater(() -> statusLabel.setText("未知のメッセージ: " + line));
    }
  }

  private void sendAnswer() {
    if (out == null)
      return;

    String ans = answerField.getText().trim();
    if (ans.isEmpty())
      return;

    out.println("ANSWER " + ans);
    System.out.println("[送信] ANSWER " + ans);
  }

  public static void main(String[] args) {
    launch(args);
  }
}
