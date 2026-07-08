import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import javax.sound.midi.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class NoteGameClientGUI extends Application {

  private Socket socket;
  private BufferedReader in;
  private PrintWriter out;

  private Label statusLabel;
  private HBox answerBox;

  private List<Label> noteLabels = new ArrayList<>();
  private List<String> answerNotes = new ArrayList<>();

  private int myRole = 0;

  private List<List<String>> historyNotes = new ArrayList<>();
  private List<List<String>> historyJudge = new ArrayList<>();

  private VBox historyPageBox = new VBox(10);

  private final String[] NOTES = { "ド", "レ", "ミ", "ファ", "ソ", "ラ", "シ", "ド" };

  private Scene mainScene;
  private Scene historyScene;

  private Sequencer sequencer;

  private boolean canPlayMusic = false;

  @Override
  public void start(Stage stage) {

    statusLabel = new Label("サーバ接続中...");
    answerBox = new HBox(5);
    answerBox.setAlignment(Pos.CENTER);

    HBox noteButtons = new HBox(10);
    noteButtons.setAlignment(Pos.CENTER);

    for (String note : NOTES) {
      Button btn = new Button(note);
      btn.setPrefWidth(60);

      btn.setOnAction(e -> {
        if (answerNotes.size() < noteLabels.size()) {
          answerNotes.add(note);
          noteLabels.get(answerNotes.size() - 1).setText(note);
        }
      });

      noteButtons.getChildren().add(btn);
    }

    Button deleteBtn = new Button("一音消す");
    deleteBtn.setPrefWidth(120);

    deleteBtn.setOnAction(e -> {
      if (!answerNotes.isEmpty()) {
        answerNotes.remove(answerNotes.size() - 1);

        for (int i = 0; i < noteLabels.size(); i++) {
          if (i < answerNotes.size()) {
            noteLabels.get(i).setText(answerNotes.get(i));
          } else {
            noteLabels.get(i).setText("");
          }
        }
      }
    });

    Button sendBtn = new Button("送信");
    sendBtn.setPrefWidth(120);
    sendBtn.setOnAction(e -> sendAnswer());

    Button historyBtn = new Button("履歴を見る");
    historyBtn.setPrefWidth(120);
    historyBtn.setOnAction(e -> stage.setScene(historyScene));

    Button playBtn = new Button("曲を聴く");
    playBtn.setPrefWidth(120);
    playBtn.setOnAction(e -> playMidi());

    VBox mainRoot = new VBox(20, statusLabel, answerBox, noteButtons, deleteBtn, sendBtn, playBtn, historyBtn);
    mainRoot.setPadding(new Insets(20));
    mainRoot.setAlignment(Pos.CENTER);

    mainScene = new Scene(mainRoot, 450, 550);

    Label historyTitle = new Label("判定履歴");
    historyTitle.setStyle("-fx-font-size:18; -fx-font-weight:bold;");

    Button backBtn = new Button("戻る");
    backBtn.setPrefWidth(120);
    backBtn.setOnAction(e -> stage.setScene(mainScene));

    VBox historyRoot = new VBox(20, historyTitle, historyPageBox, backBtn);
    historyRoot.setPadding(new Insets(20));
    historyRoot.setAlignment(Pos.CENTER);

    historyScene = new Scene(historyRoot, 450, 550);

    stage.setScene(mainScene);
    stage.setTitle("音階あてゲーム クライアント");
    stage.show();

    new Thread(this::connectToServer).start();
  }

  private void playMidi() {

    if (!canPlayMusic) {
      statusLabel.setText("今はあなたのターンではありません");
      return;
    }

    try {
      if (sequencer == null) {
        sequencer = MidiSystem.getSequencer();
        sequencer.open();
      }

      if (sequencer.isRunning()) {
        sequencer.stop();
      }

      File midiFile = new File("kirakira1.mid");
      Sequence seq = MidiSystem.getSequence(midiFile);

      sequencer.setSequence(seq);
      sequencer.start();

      canPlayMusic = false;

    } catch (Exception e) {
      e.printStackTrace();
      Platform.runLater(() -> statusLabel.setText("MIDI再生エラー"));
    }
  }

  private void connectToServer() {
    try {
      socket = new Socket("localhost", 5000);
      in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
      out = new PrintWriter(socket.getOutputStream(), true);

      Platform.runLater(() -> statusLabel.setText("サーバに接続しました。"));

      while (true) {
        String line = in.readLine();
        if (line == null) break;
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
          statusLabel.setText("あなたのターン：" + idx + "段階");
historyPageBox.getChildren().clear();
          canPlayMusic = true;

          answerBox.getChildren().clear();
          noteLabels.clear();
          answerNotes.clear();

          for (int i = 2; i < parts.length; i++) {

            Label label = new Label("");
            label.setPrefSize(50, 50);
            label.setAlignment(Pos.CENTER);
            label.setStyle("-fx-border-color:black; -fx-background-color:white;");

            noteLabels.add(label);
            answerBox.getChildren().add(label);
          }
        });

        break;

      case "WAIT":
        Platform.runLater(() -> {
          statusLabel.setText("相手のターンです");
          canPlayMusic = false;
        });
        break;

      case "RESULT":

        Platform.runLater(() -> {

          List<String> judgeList = new ArrayList<>();

          for (int i = 1; i < parts.length; i++) {
            Label label = noteLabels.get(i - 1);
            judgeList.add(parts[i]);

            switch (parts[i]) {
              case "G":
                label.setStyle("-fx-background-color:#6aaa64; -fx-text-fill:white; -fx-font-size:18; -fx-border-color:black;");
                break;
              case "Y":
                label.setStyle("-fx-background-color:#c9b458; -fx-text-fill:white; -fx-font-size:18; -fx-border-color:black;");
                break;
              default:
                label.setStyle("-fx-background-color:#787c7e; -fx-text-fill:white; -fx-font-size:18; -fx-border-color:black;");
            }
          }

          historyNotes.add(new ArrayList<>(answerNotes));
          historyJudge.add(judgeList);

          // ★★★ 履歴ページに追加（音階タイルの背景色で判定を表す） ★★★
          HBox row = new HBox(10);
          row.setAlignment(Pos.CENTER);

          for (int i = 0; i < answerNotes.size(); i++) {

            String note = answerNotes.get(i);
            String judge = judgeList.get(i);

            Label tile = new Label(note);
            tile.setPrefSize(50, 50);
            tile.setAlignment(Pos.CENTER);

            switch (judge) {
              case "G":
                tile.setStyle("-fx-background-color:#6aaa64; -fx-text-fill:white; -fx-border-color:black;");
                break;
              case "Y":
                tile.setStyle("-fx-background-color:#c9b458; -fx-text-fill:white; -fx-border-color:black;");
                break;
              default:
                tile.setStyle("-fx-background-color:#787c7e; -fx-text-fill:white; -fx-border-color:black;");
            }

            row.getChildren().add(tile);
          }

          historyPageBox.getChildren().add(row);

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
        Platform.runLater(() -> statusLabel.setText("未知のメッセージ:" + line));
    }
  }

  private void sendAnswer() {
    if (out == null) return;
    if (answerNotes.isEmpty()) return;

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < answerNotes.size(); i++) {
      if (i != 0) sb.append(" ");
      sb.append(answerNotes.get(i));
    }

    out.println("ANSWER " + sb.toString());
    System.out.println("[送信] ANSWER " + sb.toString());
  }

  public static void main(String[] args) {
    launch(args);
  }
}
