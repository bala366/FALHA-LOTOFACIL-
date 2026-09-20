package com.lotofacil.falhas;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity {
    private static final int PICK_FILE = 701;

    private final int GREEN = Color.rgb(11,93,42);
    private final int GREEN2 = Color.rgb(25,130,65);
    private final int RED = Color.rgb(190,38,38);
    private final int LIGHT = Color.rgb(245,248,246);

    private TextView fileText, statusText, resultText, last3Text;
    private ProgressBar progressBar;
    private Button analyzeButton, pdfButton;
    private List<MotorCore.Record> records;
    private MotorCore.AnalysisResult result;
    private String selectedName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    private TextView tv(String text, float sp, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(Color.rgb(28,28,28));
        v.setPadding(12,8,12,8);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setBackgroundColor(GREEN);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(12,8,12,8);
        b.setLayoutParams(lp);
        return b;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(LIGHT);
        scroll.addView(root);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(18,20,18,20);
        header.setBackgroundColor(GREEN);

        TextView clover = tv("♣", 38, true);
        clover.setTextColor(Color.WHITE);
        clover.setGravity(Gravity.CENTER);
        header.addView(clover, new LinearLayout.LayoutParams(70,-2));

        LinearLayout htxt = new LinearLayout(this);
        htxt.setOrientation(LinearLayout.VERTICAL);
        TextView title = tv("LOTOFÁCIL FALHAS", 23, true);
        title.setTextColor(Color.WHITE);
        TextView sub = tv("Padrão + Pareto + influência + backtest cego", 13, false);
        sub.setTextColor(Color.WHITE);
        htxt.addView(title); htxt.addView(sub);
        header.addView(htxt, new LinearLayout.LayoutParams(0,-2,1));
        root.addView(header);

        TextView info = tv(
            "O aplicativo projeta as 10 dezenas que devem ficar no pote. " +
            "O complemento dessas falhas vira o jogo final de 15 dezenas. " +
            "10 falhas corretas = 15 pontos; 9 = 14; 8 = 13; 7 = 12.",
            14, false);
        info.setPadding(18,16,18,10);
        root.addView(info);

        Button openButton = button("1. Selecionar arquivo de resultados TXT/CSV");
        root.addView(openButton);
        openButton.setOnClickListener(v -> openFile());

        fileText = tv("Nenhum arquivo selecionado.", 13, true);
        fileText.setPadding(18,4,18,10);
        root.addView(fileText);

        last3Text = tv("Depois de carregar a base, os três últimos concursos aparecerão aqui.", 13, false);
        last3Text.setPadding(18,8,18,8);
        root.addView(last3Text);

        analyzeButton = button("2. ANALISAR FALHAS E GERAR JOGO DE 15");
        analyzeButton.setEnabled(false);
        root.addView(analyzeButton);
        analyzeButton.setOnClickListener(v -> analyze());

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1,26);
        pp.setMargins(18,10,18,4);
        root.addView(progressBar, pp);

        statusText = tv("Aguardando arquivo.", 13, true);
        statusText.setPadding(18,4,18,8);
        root.addView(statusText);

        TextView rh = tv("RESULTADO", 18, true);
        rh.setTextColor(GREEN);
        rh.setPadding(18,16,18,4);
        root.addView(rh);

        resultText = tv("O resultado será mostrado aqui.", 14, false);
        resultText.setTextIsSelectable(true);
        resultText.setPadding(18,8,18,10);
        root.addView(resultText);

        pdfButton = button("GERAR NOVAMENTE PDF DO VOLANTE");
        pdfButton.setEnabled(false);
        root.addView(pdfButton);
        pdfButton.setOnClickListener(v -> {
            if (result != null) savePdf(result);
        });

        TextView foot = tv("PDF: verde = jogo final de 15 dezenas; vermelho = 10 falhas projetadas. O PDF é salvo automaticamente na pasta Download.", 12, false);
        foot.setPadding(18,8,18,24);
        root.addView(foot);

        setContentView(scroll);
    }

    private void openFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/*");
        String[] mimes = {"text/plain","text/csv","application/csv","application/octet-stream"};
        i.putExtra(Intent.EXTRA_MIME_TYPES, mimes);
        startActivityForResult(i, PICK_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_FILE || resultCode != RESULT_OK || data == null || data.getData() == null) return;

        Uri uri = data.getData();
        selectedName = displayName(uri);
        fileText.setText("Arquivo: " + selectedName);
        statusText.setText("Lendo arquivo...");
        progressBar.setProgress(0);
        analyzeButton.setEnabled(false);
        pdfButton.setEnabled(false);
        result = null;

        new Thread(() -> {
            try {
                String text = readUri(uri);
                List<MotorCore.Record> parsed = MotorCore.parseText(text);
                records = parsed;
                StringBuilder last = new StringBuilder();
                last.append("Concursos carregados: ").append(parsed.size()).append("\n");
                for (int x=parsed.size()-3; x<parsed.size(); x++) {
                    MotorCore.Record r = parsed.get(x);
                    last.append("Concurso ").append(r.contest)
                        .append(" | Falhas: ").append(MotorCore.fmt(r.failures)).append("\n");
                }
                runOnUiThread(() -> {
                    last3Text.setText(last.toString());
                    statusText.setText("Base carregada. Toque em ANALISAR.");
                    analyzeButton.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    statusText.setText("ERRO ao ler: " + e.getMessage());
                    resultText.setText("Confira se o arquivo é da Lotofácil e contém 15 dezenas por concurso.");
                });
            }
        }).start();
    }

    private String displayName(Uri uri) {
        String name = "resultados.txt";
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return name;
    }

    private String readUri(Uri uri) throws IOException {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("Não consegui abrir o arquivo.");
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    private void analyze() {
        if (records == null) return;
        analyzeButton.setEnabled(false);
        pdfButton.setEnabled(false);
        resultText.setText("Processando histórico...");
        progressBar.setProgress(1);

        new Thread(() -> {
            try {
                MotorCore.AnalysisResult r = MotorCore.analyze(records, (stage, percent) -> runOnUiThread(() -> {
                    statusText.setText(stage);
                    progressBar.setProgress(percent);
                }));
                result = r;
                String text = prettyResult(r);
                runOnUiThread(() -> {
                    resultText.setText(text);
                    analyzeButton.setEnabled(true);
                    pdfButton.setEnabled(true);
                    statusText.setText("Análise concluída. Salvando PDF na pasta Download...");
                });
                savePdf(r);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    analyzeButton.setEnabled(true);
                    statusText.setText("ERRO: " + e.getMessage());
                    resultText.setText("A análise não terminou. Se aparecer uma mensagem de erro, envie a tela para correção.");
                });
            }
        }).start();
    }

    private String prettyResult(MotorCore.AnalysisResult r) {
        StringBuilder s = new StringBuilder();
        s.append("PERFIL CAMPEÃO: ").append(r.championProfile).append("\n");
        s.append("Falhas repetidas aprendidas: ").append(r.repetitionPrincipal).append("\n\n");
        s.append("10 FALHAS PROJETADAS\n").append(MotorCore.fmt(r.failures)).append("\n\n");
        s.append("JOGO FINAL DE 15\n").append(MotorCore.fmt(r.game)).append("\n\n");
        s.append("Falhas repetidas do último: ").append(r.repeatedFailures).append("\n");
        s.append(String.format(Locale.US,"Perímetro: média %.3f | 12+ %d | 13+ %d | 14+ %d | 15 %d\n",r.perimeter.media,r.perimeter.p12,r.perimeter.p13,r.perimeter.p14,r.perimeter.p15));
        s.append("Padrão do jogo: pares ").append(r.props.pares).append(" | primos ").append(r.props.primos).append(" | fib ").append(r.props.fib).append(" | miolo ").append(r.props.miolo).append(" | soma ").append(r.props.soma).append("\n\n");
        s.append("BACKTEST CEGO\n");
        for (MotorCore.BacktestLine l : r.backtest) {
            s.append(String.format(Locale.US,"%s: média %.3f | 15=%d 14=%d 13=%d 12=%d 11=%d\n",l.profile,l.stats.media,l.stats.dist[15],l.stats.dist[14],l.stats.dist[13],l.stats.dist[12],l.stats.dist[11]));
        }
        return s.toString();
    }

    private void savePdf(MotorCore.AnalysisResult r) {
        try {
            String filename = "LOTOFACIL_FALHAS_CONCURSO_" + r.lastContest + ".pdf";
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) throw new IOException("Não foi possível criar o PDF em Download.");

            PdfDocument pdf = new PdfDocument();
            PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(595,842,1).create();
            PdfDocument.Page page = pdf.startPage(info);
            Canvas c = page.getCanvas();
            drawPdf(c, r);
            pdf.finishPage(page);

            OutputStream out = getContentResolver().openOutputStream(uri, "w");
            if (out == null) throw new IOException("Não foi possível gravar o PDF.");
            pdf.writeTo(out);
            out.flush(); out.close(); pdf.close();

            runOnUiThread(() -> {
                statusText.setText("PDF SALVO NA PASTA DOWNLOAD: " + filename);
                Toast.makeText(this, "PDF salvo em Download", Toast.LENGTH_LONG).show();
            });
        } catch (Exception e) {
            runOnUiThread(() -> statusText.setText("Análise concluída, mas houve erro ao salvar PDF: " + e.getMessage()));
        }
    }

    private void drawPdf(Canvas c, MotorCore.AnalysisResult r) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        c.drawColor(Color.WHITE);

        p.setColor(GREEN); p.setStyle(Paint.Style.FILL);
        c.drawRect(0,0,595,94,p);
        p.setColor(Color.WHITE); p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(25);
        c.drawText("LOTOFÁCIL FALHAS",32,42,p);
        p.setTextSize(12); p.setTypeface(Typeface.DEFAULT);
        c.drawText("Padrão + Pareto + influência + backtest cego",32,66,p);
        c.drawText("Base até o concurso " + r.lastContest,32,84,p);

        p.setColor(Color.rgb(30,30,30)); p.setTextSize(13); p.setTypeface(Typeface.DEFAULT_BOLD);
        c.drawText("10 FALHAS PROJETADAS",32,126,p);
        p.setColor(RED); p.setTextSize(16);
        c.drawText(MotorCore.fmt(r.failures),32,150,p);

        p.setColor(Color.rgb(30,30,30)); p.setTextSize(13);
        c.drawText("JOGO FINAL DE 15 DEZENAS",32,186,p);
        p.setColor(GREEN2); p.setTextSize(16);
        c.drawText(MotorCore.fmt(r.game),32,210,p);

        p.setColor(Color.rgb(45,45,45)); p.setTextSize(11); p.setTypeface(Typeface.DEFAULT);
        c.drawText("Perfil campeão: " + r.championProfile + " | falhas repetidas do último: " + r.repeatedFailures,32,238,p);
        c.drawText(String.format(Locale.US,"Perímetro: média %.3f | 12+ %d | 13+ %d | 14+ %d | 15 %d",r.perimeter.media,r.perimeter.p12,r.perimeter.p13,r.perimeter.p14,r.perimeter.p15),32,257,p);

        int startX = 73, startY = 292, cell = 86, gap = 5;
        Set<Integer> game = new HashSet<>(); for (int n:r.game) game.add(n);
        Set<Integer> fail = new HashSet<>(); for (int n:r.failures) fail.add(n);
        p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextAlign(Paint.Align.CENTER);

        for (int n=1;n<=25;n++) {
            int row=(n-1)/5, col=(n-1)%5;
            float x=startX+col*(cell+gap), y=startY+row*(cell+gap);
            if(game.contains(n)) p.setColor(GREEN2); else p.setColor(RED);
            c.drawRoundRect(x,y,x+cell,y+cell,12,12,p);
            p.setColor(Color.WHITE); p.setTextSize(25);
            c.drawText(String.format(Locale.US,"%02d",n),x+cell/2f,y+cell/2f+9,p);
        }

        p.setTextAlign(Paint.Align.LEFT); p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(11); p.setColor(GREEN2); c.drawText("VERDE = jogo final (15)",32,770,p);
        p.setColor(RED); c.drawText("VERMELHO = falhas projetadas (10)",210,770,p);
        p.setColor(Color.DKGRAY); p.setTextSize(9);
        c.drawText("10 falhas corretas = 15 pontos | 9 = 14 | 8 = 13 | 7 = 12 | 6 = 11",32,795,p);
        c.drawText("Estudo estatístico histórico; não há garantia de premiação.",32,814,p);
    }
}
