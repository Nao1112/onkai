import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Random;

public class NoteGameServer {

        // ゲームで使用する正解曲データ
        // 1つのString配列が1問分の音階を表す
        // 例：「ド ド ソ ソ ラ ラ ソ」
        private static final List<String[]> SONG = List.of(
                        new String[] { "ド", "ド", "ソ", "ソ", "ラ", "ラ", "ソ" },
                        new String[] { "ファ", "ファ", "ミ", "ミ", "レ", "レ", "ド" },
                        new String[] { "ソ", "ソ", "ファ", "ファ", "ミ", "ミ", "レ" },
                        new String[] { "ソ", "ソ", "ファ", "ファ", "ミ", "ミ", "レ" },
                        new String[] { "ド", "ド", "ソ", "ソ", "ラ", "ラ", "ソ" },
                        new String[] { "ファ", "ファ", "ミ", "ミ", "レ", "レ", "ド" });

        public static void main(String[] args) {

                // サーバが待ち受けるポート番号
                int port = 5000;

                // ServerSocketを作成し、クライアントからの接続を待つ
                try (ServerSocket serverSocket = new ServerSocket(port)) {

                        System.out.println("音階あてゲームサーバ起動: port=" + port);
                        System.out.println("プレイヤー接続待ち...");

                        // 1人目のプレイヤーの接続を待つ
                        Socket p1Socket = serverSocket.accept();
                        System.out.println("プレイヤー1接続: "
                                        + p1Socket.getInetAddress());

                        // 2人目のプレイヤーの接続を待つ
                        Socket p2Socket = serverSocket.accept();
                        System.out.println("プレイヤー2接続: "
                                        + p2Socket.getInetAddress());

                        // Socketを通信管理用クラスに変換
                        PlayerConnection player1 = new PlayerConnection(p1Socket);

                        PlayerConnection player2 = new PlayerConnection(p2Socket);

                        // 先攻プレイヤーをランダムで決定
                        // trueなら1、falseなら2
                        int firstPlayer = new Random().nextBoolean() ? 1 : 2;

                        // 現在回答するプレイヤー
                        int currentPlayer = firstPlayer;

                        // プレイヤー番号を通知
                        player1.sendLine("ROLE 1");
                        player2.sendLine("ROLE 2");

                        // ゲーム開始を通知
                        player1.sendLine("START");
                        player2.sendLine("START");

                        System.out.println("先攻プレイヤー: "
                                        + firstPlayer);

                        // 各プレイヤーが現在何問目まで正解したか管理
                        // [0] = Player1
                        // [1] = Player2
                        int[] measureIndex = { 0, 0 };

                        // ゲーム終了判定
                        boolean finished = false;

                        // 勝者番号
                        int winner = 0;

                        // 勝者が決まるまでゲームを繰り返す
                        while (!finished) {

                                // 現在回答するプレイヤー
                                PlayerConnection active = (currentPlayer == 1)
                                                ? player1
                                                : player2;

                                // 待機中のプレイヤー
                                PlayerConnection waiting = (currentPlayer == 1)
                                                ? player2
                                                : player1;

                                // 現在の問題を取得
                                String[] measureNotes = SONG.get(
                                                measureIndex[currentPlayer - 1]);

                                // クライアントへ送信する問題文を作成
                                // 例:
                                // MEASURE 0 ド ド ソ ソ ラ ラ ソ
                                StringBuilder sb = new StringBuilder();

                                sb.append("MEASURE ")
                                                .append(measureIndex[currentPlayer - 1]);

                                for (String note : measureNotes) {
                                        sb.append(" ")
                                                        .append(note);
                                }

                                // 回答するプレイヤーへ問題送信
                                active.sendLine(sb.toString());

                                // 相手には待機通知
                                waiting.sendLine("WAIT");

                                System.out.println(
                                                "送信: "
                                                                + sb
                                                                + " -> Player"
                                                                + currentPlayer);

                                // プレイヤーから回答を受信
                                // 例:
                                // ANSWER ド ド ソ ソ ラ ラ ソ
                                String answerLine = active.readLine();

                                // 切断された場合
                                if (answerLine == null) {

                                        System.out.println(
                                                        "Player"
                                                                        + currentPlayer
                                                                        + " 切断。ゲーム終了。");

                                        break;
                                }

                                System.out.println(
                                                "受信: "
                                                                + answerLine);

                                // ANSWER以外のデータの場合は無効回答
                                if (!answerLine.startsWith("ANSWER")) {

                                        active.sendLine(
                                                        "RESULT 0 0");

                                        // ターン交代
                                        currentPlayer = (currentPlayer == 1)
                                                        ? 2
                                                        : 1;

                                        continue;
                                }

                                // 受信した回答を空白で分割
                                String[] parts = answerLine.split("\\s+");

                                // ANSWER以降の音階だけを保存
                                String[] answerNotes = new String[parts.length - 1];

                                System.arraycopy(
                                                parts,
                                                1,
                                                answerNotes,
                                                0,
                                                answerNotes.length);

                                // 完全一致しているか確認
                                boolean correct = isCorrect(
                                                measureNotes,
                                                answerNotes);

                                // 位置が一致している音の数を取得
                                int matchCount = countMatches(
                                                measureNotes,
                                                answerNotes);

                                // Wordle形式で色判定
                                // G = 正しい位置
                                // Y = 位置違い
                                // B = 不正解
                                String[] colors = judgeWordle(
                                                answerNotes,
                                                measureNotes);

                                // RESULTメッセージ作成
                                // 例:
                                // RESULT G Y B G
                                sb.setLength(0);

                                sb.append("RESULT");

                                for (String c : colors) {
                                        sb.append(" ")
                                                        .append(c);
                                }

                                // 判定結果を送信
                                active.sendLine(
                                                sb.toString());

                                System.out.println(
                                                "判定: correct="
                                                                + correct
                                                                + ", match="
                                                                + matchCount);

                                // 完全正解の場合
                                if (correct) {

                                        // 次の問題へ進む
                                        measureIndex[currentPlayer - 1]++;

                                        // 全問題クリアした場合
                                        if (measureIndex[currentPlayer - 1] >= SONG.size()) {

                                                finished = true;

                                                winner = currentPlayer;
                                        } else {

                                                // 次の段階へ進むことを通知
                                                active.sendLine("NEXT");

                                        }
                                }

                                // ターン交代
                                currentPlayer = (currentPlayer == 1)
                                                ? 2
                                                : 1;
                        }

                        // 勝者がいる場合
                        if (winner != 0) {

                                System.out.println(
                                                "勝者: Player"
                                                                + winner);

                                // 両方に勝者通知
                                player1.sendLine(
                                                "WINNER " + winner);

                                player2.sendLine(
                                                "WINNER " + winner);
                        }

                        // 通信終了
                        player1.close();
                        player2.close();

                        System.out.println(
                                        "ゲーム終了。サーバ停止。");

                } catch (IOException e) {

                        // 通信エラー表示
                        e.printStackTrace();
                }
        }

        // 音階が完全一致しているか判定する
        private static boolean isCorrect(
                        String[] measure,
                        String[] answer) {

                // 音数が違う場合は不正解
                if (measure.length != answer.length)
                        return false;

                // 1音ずつ比較
                for (int i = 0; i < measure.length; i++) {

                        if (!measure[i].equals(answer[i]))
                                return false;
                }

                return true;
        }

        // 同じ位置の音が何個あるか数える
        private static int countMatches(
                        String[] measure,
                        String[] answer) {

                int len = Math.min(
                                measure.length,
                                answer.length);

                int count = 0;

                for (int i = 0; i < len; i++) {

                        if (measure[i].equals(answer[i]))
                                count++;
                }

                return count;
        }

        // Wordle風の色判定
        private static String[] judgeWordle(
                        String[] answer,
                        String[] correct) {

                String[] result = new String[answer.length];

                // すでに使用した正解音を記録
                boolean[] used = new boolean[correct.length];

                // まず完全一致(G)を確認
                for (int i = 0; i < answer.length; i++) {

                        if (i < correct.length
                                        && answer[i].equals(correct[i])) {

                                result[i] = "G";

                                used[i] = true;
                        }
                }

                // 次に黄色(Y)・灰色(B)を確認
                for (int i = 0; i < answer.length; i++) {

                        // すでにGならスキップ
                        if (result[i] != null)
                                continue;

                        boolean found = false;

                        // 正解音の中に存在するか確認
                        for (int j = 0; j < correct.length; j++) {

                                if (!used[j]
                                                && answer[i].equals(correct[j])) {

                                        found = true;

                                        used[j] = true;

                                        break;
                                }
                        }

                        result[i] = found ? "Y" : "B";
                }

                return result;
        }

        // プレイヤーとの通信を管理するクラス
        private static class PlayerConnection {

                private final Socket socket;

                private final BufferedReader in;

                private final PrintWriter out;

                // 通信準備
                PlayerConnection(Socket socket)
                                throws IOException {

                        this.socket = socket;

                        // 受信用
                        this.in = new BufferedReader(
                                        new InputStreamReader(
                                                        socket.getInputStream(),
                                                        "UTF-8"));

                        // 送信用
                        this.out = new PrintWriter(
                                        socket.getOutputStream(),
                                        true);
                }

                // メッセージ送信
                void sendLine(String line) {

                        out.println(line);
                }

                // メッセージ受信
                String readLine()
                                throws IOException {

                        return in.readLine();
                }

                // 通信終了処理
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