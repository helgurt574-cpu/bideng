import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;

/**
 * Paketlenecek sesleri uygulamanın kullandığı sherpa-onnx Java API'siyle dener.
 * Kullanım: java -cp sherpa-onnx-jvm.jar -Dsherpa_onnx.native.path=DIR tools/TtsSmokeTest.java TTS_DIR
 */
public class TtsSmokeTest {
    public static void main(String[] args) {
        String tts = args[0];
        String[][] samples = {
            // Türkçe özel harfler (ğ ş ı İ) Windows'ta bozulmamalı
            {"tr_TR_fahrettin_medium", "Işığı söndürüp güzel bir kitap okuyoruz; İstanbul'da ağaçlar yeşil."},
            {"tr_TR_dfki_medium", "Çağdaş Türkçe metinler düzgün okunmalıdır."},
            {"vits-piper-ar_JO-kareem-low", "مرحبا، هذه جملة تجريبية للقراءة."},
            {"vits-piper-fa_IR-amir-medium", "سلام، این یک جمله آزمایشی است."},
            {"vits-piper-fa_IR-gyro-medium", "امیدواریم برنامه این متن را درست بخواند."},
        };
        int failures = 0;
        for (String[] sample : samples) {
            String dir = tts + "/" + sample[0];
            OfflineTtsConfig config = OfflineTtsConfig.builder()
                .setModel(OfflineTtsModelConfig.builder()
                    .setVits(OfflineTtsVitsModelConfig.builder()
                        .setModel(dir + "/model.onnx")
                        .setTokens(dir + "/tokens.txt")
                        .setDataDir(tts + "/espeak-ng-data")
                        .build())
                    .setNumThreads(2)
                    .build())
                .build();
            OfflineTts engine = new OfflineTts(config);
            GeneratedAudio audio = engine.generate(sample[1], 0, 1.0f);
            double seconds = audio.getSamples().length / (double) audio.getSampleRate();
            engine.release();
            // Cümle başına en az ~1,5 saniye ses beklenir; harfler kaybolursa çok kısa çıkar
            boolean ok = seconds > 1.5;
            System.out.printf("%s: %.2f s %s%n", sample[0], seconds, ok ? "tamam" : "BAŞARISIZ");
            if (!ok) failures++;
        }
        if (failures > 0) System.exit(1);
    }
}
