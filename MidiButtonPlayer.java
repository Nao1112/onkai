import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import javax.sound.midi.*;

public class MidiButtonPlayer extends Application {

  private Sequencer sequencer;

  @Override
  public void start(Stage stage) throws Exception {

    // Sequencer の準備
    sequencer = MidiSystem.getSequencer();
    sequencer.open();

    // kirakira1.mid を読み込む      onkaiファイルにmidiファイルを置く
    Sequence sequence = MidiSystem.getSequence(new java.io.File("kirakira1.mid"));
    sequencer.setSequence(sequence);

    // ボタン
    Button playBtn = new Button("きらきら星を再生");
    playBtn.setPrefWidth(200);

    playBtn.setOnAction(e -> {
      try {
        playMidi();
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    });

    VBox root = new VBox(20, playBtn);
    root.setAlignment(Pos.CENTER);

    stage.setScene(new Scene(root, 300, 200));
    stage.setTitle("MIDI 再生テスト");
    stage.show();
  }

  private void playMidi() throws Exception {
    // 再生中なら止める
    if (sequencer.isRunning()) {
      sequencer.stop();
    }

    sequencer.setTickPosition(0); // 先頭から再生
    sequencer.start();
  }

  @Override
  public void stop() {
    if (sequencer != null) {
      sequencer.stop();
      sequencer.close();
    }
  }

  public static void main(String[] args) {
    launch(args);
  }
}
