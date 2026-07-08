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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

// JavaFXアプリケーションとして動作するクライアント
public class NoteGameClientGUI extends Application {

    // サーバとの通信に使用するSocket
    private Socket socket;

    // サーバからデータを受信するためのReader
    private BufferedReader in;

    // サーバへデータを送信するためのWriter
    private PrintWriter out;

    // ゲーム状態を表示するラベル
    private Label statusLabel;

    // 入力した音階を表示する領域
    private HBox answerBox;

    // 音階入力欄のLabelを管理するリスト
    private List<Label> noteLabels = new ArrayList<>();

    // プレイヤーが入力した音階を保存するリスト
    private List<String> answerNotes = new ArrayList<>();

    // 自分のプレイヤー番号
    private int myRole = 0;

    // 音階ボタンとして表示する音
    private final String[] NOTES = { "ド", "レ", "ミ", "ファ", "ソ", "ラ", "シ", "ド" };

    // JavaFX画面作成処理
    @Override
    public void start(Stage stage) {

        // 状態表示ラベル
        statusLabel = new Label("サーバ接続中...");

        // 入力表示部分
        answerBox = new HBox(5);

        answerBox.setAlignment(Pos.CENTER);

        // 音階ボタンを配置する領域
        HBox noteButtons = new HBox(10);

        noteButtons.setAlignment(Pos.CENTER);

        // 音階ボタンを生成
        for (String note : NOTES) {

            Button btn = new Button(note);

            btn.setPrefWidth(60);

            // ボタン押下時の処理
            btn.setOnAction(e -> {

                // 入力可能な数を超えていない場合
                if (answerNotes.size() < noteLabels.size()) {

                    // 入力した音を保存
                    answerNotes.add(note);

                    // 対応する表示欄に音を表示
                    noteLabels.get(
                            answerNotes.size() - 1)
                            .setText(note);

                }

            });

            noteButtons.getChildren()
                    .add(btn);

        }

        // 回答送信ボタン
        Button sendBtn = new Button("送信");

        sendBtn.setPrefWidth(120);

        // 押されたら回答送信
        sendBtn.setOnAction(e -> sendAnswer());

        // 画面配置
        VBox root = new VBox(
                20,
                statusLabel,
                answerBox,
                noteButtons,
                sendBtn);

        root.setPadding(
                new Insets(20));

        root.setAlignment(
                Pos.CENTER);

        // ウィンドウ設定
        stage.setScene(
                new Scene(root, 400, 300));

        stage.setTitle(
                "音階あてゲーム クライアント");

        stage.show();

        // 別スレッドでサーバ接続開始
        new Thread(
                this::connectToServer)
                .start();

    }

    // サーバへ接続する処理
    private void connectToServer() {

        try {

            // localhost:5000へ接続
            socket = new Socket(
                    "localhost",
                    5000);

            // 受信用設定
            in = new BufferedReader(
                    new InputStreamReader(
                            socket.getInputStream(),
                            "UTF-8"));

            // 送信用設定
            out = new PrintWriter(
                    socket.getOutputStream(),
                    true);

            // JavaFX画面更新はメインスレッドで行う
            Platform.runLater(() -> statusLabel.setText(
                    "サーバに接続しました。"));

            // サーバからのメッセージを待ち続ける
            while (true) {

                String line = in.readLine();

                // 接続終了
                if (line == null)
                    break;

                System.out.println(
                        "[受信] "
                                + line);

                // 受信内容を処理
                handleServerMessage(line);

            }

        } catch (IOException e) {

            Platform.runLater(() -> statusLabel.setText(
                    "サーバ接続エラー"));

        }

    }

    // サーバから受信したメッセージを処理
    private void handleServerMessage(
            String line) {

        // 空白で分割
        String[] parts = line.split("\\s+");

        // 命令部分
        String cmd = parts[0];

        switch (cmd) {

            // プレイヤー番号受信
            case "ROLE":

                myRole = Integer.parseInt(parts[1]);

                Platform.runLater(() -> statusLabel.setText(
                        "あなたは Player"
                                + myRole));

                break;

            // ゲーム開始通知
            case "START":

                Platform.runLater(() -> statusLabel.setText(
                        "ゲーム開始"));

                break;

            // 問題受信
            case "MEASURE":

                int idx = Integer.parseInt(parts[1]);

                Platform.runLater(() -> {

                    statusLabel.setText(
                            "あなたのターン："
                                    + idx
                                    + "段階");

                    // 前回入力を削除
                    answerBox.getChildren()
                            .clear();

                    noteLabels.clear();

                    answerNotes.clear();

                    // 音数分だけ入力欄を作成
                    for (int i = 2; i < parts.length; i++) {

                        Label label = new Label("");

                        label.setPrefSize(
                                50,
                                50);

                        label.setAlignment(
                                Pos.CENTER);

                        // 枠線と背景設定
                        label.setStyle(
                                "-fx-border-color:black;"
                                        +
                                        "-fx-background-color:white;");

                        noteLabels.add(label);

                        answerBox.getChildren()
                                .add(label);

                    }

                });

                break;

            // 相手のターン
            case "WAIT":

                Platform.runLater(() -> statusLabel.setText(
                        "相手のターンです"));

                break;

            // 判定結果受信
            case "RESULT":

                Platform.runLater(() -> {

                    // G/Y/Bごとに色変更
                    for (int i = 1; i < parts.length; i++) {

                        Label label = noteLabels.get(i - 1);

                        switch (parts[i]) {

                            // 正しい位置
                            case "G":

                                label.setStyle(
                                        "-fx-background-color:#6aaa64;"
                                                +
                                                "-fx-text-fill:white;"
                                                +
                                                "-fx-font-size:18;"
                                                +
                                                "-fx-border-color:black;");

                                break;

                            // 音は正しいが位置違い
                            case "Y":

                                label.setStyle(
                                        "-fx-background-color:#c9b458;"
                                                +
                                                "-fx-text-fill:white;"
                                                +
                                                "-fx-font-size:18;"
                                                +
                                                "-fx-border-color:black;");

                                break;

                            // 存在しない音
                            default:

                                label.setStyle(
                                        "-fx-background-color:#787c7e;"
                                                +
                                                "-fx-text-fill:white;"
                                                +
                                                "-fx-font-size:18;"
                                                +
                                                "-fx-border-color:black;");

                        }

                    }

                });

                break;

            // 勝敗通知
            case "WINNER":

                int winner = Integer.parseInt(parts[1]);

                Platform.runLater(() -> {

                    if (winner == myRole) {

                        statusLabel.setText(
                                "🎉 あなたの勝ち！");

                    } else {

                        statusLabel.setText(
                                "😢 あなたの負け…");

                    }

                });

                break;

            // 未対応メッセージ
            default:

                Platform.runLater(() -> statusLabel.setText(
                        "未知のメッセージ:"
                                + line));

        }

    }

    // 入力した音階をサーバへ送信
    private void sendAnswer() {

        // 接続されていない場合
        if (out == null)
            return;

        StringBuilder sb = new StringBuilder();

        // 音階を空白区切りで連結
        for (int i = 0; i < answerNotes.size(); i++) {

            if (i != 0)
                sb.append(" ");

            sb.append(
                    answerNotes.get(i));

        }

        String ans = sb.toString();

        // 入力なしの場合送信しない
        if (ans.isEmpty())
            return;

        // ANSWER形式で送信
        out.println(
                "ANSWER "
                        + ans);

        System.out.println(
                "[送信] ANSWER "
                        + ans);

    }

    public static void main(String[] args) {

        // JavaFX起動
        launch(args);

    }

}