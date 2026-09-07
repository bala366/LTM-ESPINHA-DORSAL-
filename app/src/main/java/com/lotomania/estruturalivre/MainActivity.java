package com.lotomania.estruturalivre;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int REQ_OPEN = 1001;
    private static final int REQ_PDF = 1002;

    private static final int PURPLE = Color.rgb(106, 27, 154);
    private static final int GREEN = Color.rgb(11, 122, 59);
    private static final int BG = Color.rgb(247, 245, 250);

    // Busca genérica para uma estrutura livre informada pelo usuário.
    private static final int RESTARTS = 90;
    private static final int STEPS_PER_RESTART = 16000;

    private Uri selectedUri;
    private final List<Contest> contests = new ArrayList<>();
    private final List<TargetRow> targetRows = new ArrayList<>();

    private TextView fileLabel;
    private EditText startEdit;
    private EditText endEdit;
    private EditText absentEdit;
    private LinearLayout targetBox;
    private LinearLayout resultBox;
    private TextView status;
    private TextView progressDetail;
    private ProgressBar progressBar;
    private Button buildTargetsButton;
    private Button analyzeButton;
    private Button pdfButton;

    private Analysis lastAnalysis;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(buildUi());
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(14));
        header.setBackgroundColor(PURPLE);

        TextView clover = text("♣", 46, Color.WHITE, true);
        clover.setGravity(Gravity.CENTER);
        header.addView(clover, new LinearLayout.LayoutParams(dp(70), dp(80)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text("LOTOMANIA • ESTRUTURA LIVRE V1", 22, Color.WHITE, true));
        titles.addView(text("Você define a pontuação de cada concurso • ausentes • progresso • PDF", 14, Color.WHITE, false));
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(header);

        Button choose = button("1. ESCOLHER ARQUIVO DE RESULTADOS", Color.rgb(220,220,220), Color.DKGRAY);
        choose.setOnClickListener(v -> chooseFile());
        root.addView(choose, top(18));

        fileLabel = text("Nenhum arquivo selecionado", 14, Color.DKGRAY, false);
        root.addView(fileLabel, top(8));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        startEdit = numberEdit("Concurso inicial");
        endEdit = numberEdit("Concurso final");
        row.addView(startEdit, new LinearLayout.LayoutParams(0, dp(58), 1f));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, dp(58), 1f);
        rp.leftMargin = dp(12);
        row.addView(endEdit, rp);
        root.addView(row, top(12));

        buildTargetsButton = button("2. MONTAR CAMPOS DE PONTUAÇÃO DA JANELA", Color.rgb(220,220,220), Color.DKGRAY);
        buildTargetsButton.setOnClickListener(v -> buildTargetRows());
        root.addView(buildTargetsButton, top(10));

        targetBox = new LinearLayout(this);
        targetBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(targetBox, top(8));

        absentEdit = new EditText(this);
        absentEdit.setHint("Dezenas ausentes, ex.: 08 09 12 24 42 50 54 79");
        absentEdit.setTextSize(17);
        absentEdit.setMinLines(2);
        absentEdit.setSingleLine(false);
        root.addView(absentEdit, top(12));

        analyzeButton = button("3. GERAR JOGO DE 50 NESSA ESTRUTURA", Color.rgb(220,220,220), Color.DKGRAY);
        analyzeButton.setOnClickListener(v -> analyze());
        root.addView(analyzeButton, top(12));

        status = text("Defina a janela, crie os campos e informe a pontuação desejada de CADA concurso.", 14, Color.DKGRAY, false);
        root.addView(status, top(10));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, top(10));

        progressDetail = text("", 13, PURPLE, true);
        progressDetail.setVisibility(View.GONE);
        root.addView(progressDetail, top(5));

        pdfButton = button("4. GERAR PDF DO MESMO JOGO", GREEN, Color.WHITE);
        pdfButton.setEnabled(false);
        pdfButton.setOnClickListener(v -> requestPdf());
        root.addView(pdfButton, top(12));

        resultBox = new LinearLayout(this);
        resultBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultBox, top(12));

        return scroll;
    }

    private void chooseFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/plain","text/csv","application/octet-stream"});
        startActivityForResult(i, REQ_OPEN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;

        if (requestCode == REQ_OPEN) {
            selectedUri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            loadFile();
        } else if (requestCode == REQ_PDF) {
            writePdf(data.getData());
        }
    }

    private void loadFile() {
        contests.clear();
        Map<Integer, Contest> map = new LinkedHashMap<>();
        Pattern p = Pattern.compile("\\d+");

        try (InputStream in = getContentResolver().openInputStream(selectedUri);
             BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = p.matcher(line);
                List<Integer> a = new ArrayList<>();
                while (m.find()) {
                    try { a.add(Integer.parseInt(m.group())); } catch (Exception ignored) {}
                }
                Contest c = parseContest(a);
                if (c != null) map.put(c.number, c);
            }
        } catch (Exception e) {
            toast("Erro ao ler arquivo: " + e.getMessage());
            return;
        }

        contests.addAll(map.values());
        contests.sort(Comparator.comparingInt(c -> c.number));

        if (contests.isEmpty()) {
            toast("Nenhum concurso válido encontrado.");
            return;
        }

        startEdit.setText(String.valueOf(contests.get(Math.max(0, contests.size()-10)).number));
        endEdit.setText(String.valueOf(contests.get(contests.size()-1).number));
        fileLabel.setText(displayName(selectedUri) + " • " + contests.size() + " concursos");
        buildTargetRows();
        toast("Arquivo carregado.");
    }

    private Contest parseContest(List<Integer> a) {
        if (a.size() < 21) return null;
        int concurso = a.get(0);
        List<Integer> dezenas = new ArrayList<>();
        for (int i = 1; i < a.size(); i++) {
            int n = a.get(i);
            if (n >= 0 && n <= 99 && !dezenas.contains(n)) {
                dezenas.add(n);
                if (dezenas.size() == 20) break;
            }
        }
        if (dezenas.size() != 20) return null;
        return new Contest(concurso, dezenas);
    }

    private void buildTargetRows() {
        if (contests.isEmpty()) {
            toast("Escolha o arquivo primeiro.");
            return;
        }

        Integer start = intText(startEdit);
        Integer end = intText(endEdit);
        if (start == null || end == null || start > end) {
            toast("Janela inválida.");
            return;
        }

        List<Contest> interval = interval(start, end);
        if (interval.isEmpty() || interval.get(0).number != start || interval.get(interval.size()-1).number != end) {
            toast("Concurso inicial/final não encontrado.");
            return;
        }

        targetRows.clear();
        targetBox.removeAllViews();

        TextView title = text("PONTUAÇÃO DESEJADA POR CONCURSO", 18, PURPLE, true);
        targetBox.addView(title);

        for (int i = 0; i < interval.size(); i++) {
            Contest c = interval.get(i);
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);

            TextView label = text("Concurso " + c.number, 16, Color.DKGRAY, false);
            line.addView(label, new LinearLayout.LayoutParams(0, dp(54), 1f));

            EditText score = new EditText(this);
            score.setInputType(InputType.TYPE_CLASS_NUMBER);
            score.setTextSize(18);
            score.setGravity(Gravity.CENTER);
            score.setHint("0-20");
            score.setText("10");
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(92), dp(54));
            line.addView(score, sp);

            targetBox.addView(line, top(4));
            targetRows.add(new TargetRow(c.number, score));
        }

        status.setText("Campos criados: " + targetRows.size() + " concursos. Digite a pontuação exata desejada em cada um.");
    }

    private void analyze() {
        if (contests.isEmpty()) {
            toast("Escolha o arquivo.");
            return;
        }
        Integer start = intText(startEdit);
        Integer end = intText(endEdit);
        if (start == null || end == null) {
            toast("Informe a janela.");
            return;
        }

        List<Contest> interval = interval(start, end);
        if (interval.size() != targetRows.size()) {
            toast("Clique em MONTAR CAMPOS após alterar a janela.");
            return;
        }

        int[] targets = new int[targetRows.size()];
        for (int i = 0; i < targetRows.size(); i++) {
            Integer v = intText(targetRows.get(i).edit);
            if (v == null || v < 0 || v > 20) {
                toast("Pontuação inválida no concurso " + targetRows.get(i).contest);
                return;
            }
            targets[i] = v;
        }

        Set<Integer> absent = parseAbsent(absentEdit.getText().toString());

        analyzeButton.setEnabled(false);
        pdfButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        progressDetail.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        resultBox.removeAllViews();
        lastAnalysis = null;

        status.setText("Analisando estrutura e fazendo trocas...");

        new Thread(() -> {
            Analysis a = compute(interval, targets, absent, (pct, msg) -> runOnUiThread(() -> {
                progressBar.setProgress(pct);
                progressDetail.setText(pct + "% • " + msg);
            }));

            runOnUiThread(() -> {
                analyzeButton.setEnabled(true);
                progressBar.setProgress(100);
                progressDetail.setText("100% • análise concluída");

                if (a == null) {
                    status.setText("Não encontrei solução nesta busca. Tente novamente; a busca usa reinícios aleatórios.");
                    return;
                }

                lastAnalysis = a;
                pdfButton.setEnabled(true);
                status.setText(a.exact
                        ? "Concluído: estrutura exata encontrada."
                        : "Concluído: melhor aproximação encontrada; veja as diferenças.");
                render(a);
            });
        }).start();
    }

    private Analysis compute(List<Contest> interval, int[] targets, Set<Integer> absent, ProgressReporter reporter) {
        long seed = 17L;
        for (Contest c : interval) seed = seed * 31 + c.number;
        for (int t : targets) seed = seed * 37 + t;
        for (int d : absent) seed = seed * 41 + d;
        Random random = new Random(seed);

        Candidate best = null;
        long total = (long) RESTARTS * STEPS_PER_RESTART;
        long done = 0;
        long started = System.currentTimeMillis();

        for (int restart = 0; restart < RESTARTS; restart++) {
            Set<Integer> game = initialGame(absent, random);
            Candidate current = evaluate(game, interval, targets, absent);
            double currentEnergy = energy(current, absent.size());

            for (int step = 0; step < STEPS_PER_RESTART; step++) {
                done++;

                Set<Integer> testGame = mutate(current.game, absent, random);
                Candidate test = evaluate(testGame, interval, targets, absent);
                double e = energy(test, absent.size());

                boolean accept = e < currentEnergy;
                if (!accept) {
                    double frac = step / (double)Math.max(1, STEPS_PER_RESTART-1);
                    double temp = Math.max(1.0, 18000.0 * (1.0-frac)*(1.0-frac));
                    double delta = e-currentEnergy;
                    if (delta < temp*15) {
                        double prob = Math.exp(-delta/temp) * 0.05;
                        accept = random.nextDouble() < prob;
                    }
                }

                if (accept) {
                    current = test;
                    currentEnergy = e;
                }

                if (best == null || better(current, best)) best = current.copy();

                if (done % 1200 == 0 || done == 1) {
                    int pct = Math.min(99, (int)(done * 100 / total));
                    long elapsed = Math.max(1, System.currentTimeMillis()-started);
                    double speed = done / (elapsed/1000.0);
                    long eta = speed > 0 ? (long)((total-done)/speed) : 0;
                    reporter.update(pct,
                            "trocas " + done + "/" + total
                            + " • diferenças " + best.totalDiff
                            + " • ausentes " + best.absentIncluded + "/" + absent.size()
                            + " • linha mín. " + best.minLineCount
                            + " • ETA " + formatEta(eta));
                }

                if (best != null && best.totalDiff == 0 && best.absentIncluded == absent.size() && best.minLineCount >= 3) {
                    reporter.update(98, "estrutura exata + ausentes completas + linhas válidas");
                    return toAnalysis(best, interval, targets, absent);
                }
            }
        }

        return best == null ? null : toAnalysis(best, interval, targets, absent);
    }

    private Set<Integer> initialGame(Set<Integer> absent, Random random) {
        Set<Integer> game = new HashSet<>();
        for (int d : absent) if (game.size() < 50) game.add(d);
        List<Integer> rest = new ArrayList<>();
        for (int d = 0; d <= 99; d++) if (!game.contains(d)) rest.add(d);
        Collections.shuffle(rest, random);
        for (int d : rest) {
            if (game.size() >= 50) break;
            game.add(d);
        }
        return game;
    }

    private Set<Integer> mutate(Set<Integer> game, Set<Integer> absent, Random random) {
        List<Integer> inside = new ArrayList<>(game);
        List<Integer> outside = new ArrayList<>();
        for (int d = 0; d <= 99; d++) if (!game.contains(d)) outside.add(d);

        List<Integer> removable = new ArrayList<>();
        for (int d : inside) if (!absent.contains(d)) removable.add(d);
        if (removable.isEmpty()) removable = inside;

        List<Integer> missingAbsent = new ArrayList<>();
        for (int d : absent) if (!game.contains(d)) missingAbsent.add(d);

        int add = (!missingAbsent.isEmpty() && random.nextDouble() < 0.55)
                ? missingAbsent.get(random.nextInt(missingAbsent.size()))
                : outside.get(random.nextInt(outside.size()));
        int remove = removable.get(random.nextInt(removable.size()));

        Set<Integer> out = new HashSet<>(game);
        out.remove(remove);
        out.add(add);
        return out;
    }

    private Candidate evaluate(Set<Integer> game, List<Contest> interval, int[] targets, Set<Integer> absent) {
        Candidate c = new Candidate();
        c.game = new HashSet<>(game);
        c.hits = new int[interval.size()];
        c.targets = targets.clone();
        c.totalDiff = 0;
        c.sumHits = 0;

        for (int i = 0; i < interval.size(); i++) {
            int h = hits(game, interval.get(i).numbers);
            c.hits[i] = h;
            c.sumHits += h;
            c.totalDiff += Math.abs(h - targets[i]);
        }

        c.absentIncluded = 0;
        for (int d : absent) if (game.contains(d)) c.absentIncluded++;

        c.lineCounts = new int[10];
        for (int d : game) c.lineCounts[d/10]++;
        c.minLineCount = 99;
        c.lineDeficit = 0;
        for (int n : c.lineCounts) {
            c.minLineCount = Math.min(c.minLineCount, n);
            if (n < 3) c.lineDeficit += 3-n;
        }

        c.exact = c.totalDiff == 0 && c.lineDeficit == 0;
        return c;
    }

    private double energy(Candidate c, int absentTotal) {
        // Pontuações exatas são a prioridade máxima.
        double e = c.totalDiff * 1_000_000.0;
        e += c.lineDeficit * 250_000.0;
        e += (absentTotal - c.absentIncluded) * 100_000.0;
        // Desempate: maior soma de acertos na janela.
        e -= c.sumHits * 10.0;
        return e;
    }

    private boolean better(Candidate a, Candidate b) {
        if (a.totalDiff != b.totalDiff) return a.totalDiff < b.totalDiff;
        if (a.lineDeficit != b.lineDeficit) return a.lineDeficit < b.lineDeficit;
        if (a.absentIncluded != b.absentIncluded) return a.absentIncluded > b.absentIncluded;
        return a.sumHits > b.sumHits;
    }

    private Analysis toAnalysis(Candidate c, List<Contest> interval, int[] targets, Set<Integer> absent) {
        Analysis a = new Analysis();
        a.game = new TreeSet<>(c.game);
        a.interval = new ArrayList<>(interval);
        a.targets = targets.clone();
        a.hits = c.hits.clone();
        a.absent = new TreeSet<>(absent);
        a.absentIncluded = c.absentIncluded;
        a.lineCounts = c.lineCounts.clone();
        a.minLineCount = c.minLineCount;
        a.totalDiff = c.totalDiff;
        a.exact = c.exact;
        return a;
    }

    private void render(Analysis a) {
        resultBox.removeAllViews();

        addSection("RESUMO",
                "Janela: " + a.interval.get(0).number + " a " + a.interval.get(a.interval.size()-1).number + "\n"
                + "Concursos: " + a.interval.size() + "\n"
                + "Diferença total da estrutura: " + a.totalDiff + "\n"
                + "Estrutura exata: " + (a.totalDiff == 0 ? "SIM" : "NÃO") + "\n"
                + "Ausentes incluídas: " + a.absentIncluded + "/" + a.absent.size() + "\n"
                + "Menor quantidade em uma linha: " + a.minLineCount);

        StringBuilder game = new StringBuilder();
        int idx = 0;
        for (int d : a.game) {
            if (idx > 0 && idx % 10 == 0) game.append("\n");
            game.append(String.format(Locale.US, "%02d ", d));
            idx++;
        }
        addSection("JOGO DE 50 DEZENAS", game.toString().trim());

        StringBuilder absent = new StringBuilder();
        absent.append("Informadas: ").append(fmt(a.absent)).append("\n");
        Set<Integer> in = new TreeSet<>(a.absent);
        in.retainAll(a.game);
        Set<Integer> out = new TreeSet<>(a.absent);
        out.removeAll(a.game);
        absent.append("Entraram: ").append(in.size()).append("/").append(a.absent.size()).append("\n");
        absent.append("Incluídas: ").append(fmt(in)).append("\n");
        absent.append("Fora: ").append(out.isEmpty() ? "nenhuma" : fmt(out));
        addSection("DEZENAS AUSENTES", absent.toString());

        StringBuilder scores = new StringBuilder();
        for (int i = 0; i < a.interval.size(); i++) {
            Contest c = a.interval.get(i);
            int got = a.hits[i];
            int want = a.targets[i];
            scores.append("Concurso ").append(c.number)
                    .append(": desejado ").append(want)
                    .append(" • encontrado ").append(got);
            if (got == want) scores.append("  OK");
            else scores.append("  diferença ").append(got-want);
            scores.append("\n");
        }
        addSection("PONTUAÇÃO CONCURSO POR CONCURSO", scores.toString().trim());

        StringBuilder lines = new StringBuilder();
        for (int r = 0; r < 10; r++) {
            lines.append(String.format(Locale.US, "%02d-%02d: %d dezenas%s\n",
                    r*10, r*10+9, a.lineCounts[r], a.lineCounts[r] >= 3 ? "" : "  !! abaixo de 3"));
        }
        addSection("DISTRIBUIÇÃO POR LINHAS", lines.toString().trim());

        StringBuilder why = new StringBuilder();
        for (int d : a.game) {
            List<String> motivos = new ArrayList<>();
            if (a.absent.contains(d)) motivos.add("AUSENTE");
            for (int i = 0; i < a.interval.size(); i++) {
                if (a.interval.get(i).numbers.contains(d)) {
                    motivos.add(String.valueOf(a.interval.get(i).number));
                }
            }
            why.append(String.format(Locale.US, "%02d : %s\n", d,
                    motivos.isEmpty() ? "equilíbrio/troca" : android.text.TextUtils.join(" • ", motivos)));
        }
        addSection("AUDITORIA / JUSTIFICATIVA DAS DEZENAS", why.toString().trim());
    }

    private void requestPdf() {
        if (lastAnalysis == null) return;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/pdf");
        i.putExtra(Intent.EXTRA_TITLE, "LOTOMANIA_ESTRUTURA_LIVRE.pdf");
        startActivityForResult(i, REQ_PDF);
    }

    private void writePdf(Uri uri) {
        if (lastAnalysis == null || uri == null) return;

        PdfDocument pdf = new PdfDocument();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        int pageNo = 1;
        PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,pageNo).create());
        Canvas c = page.getCanvas();
        int y = 40;

        p.setColor(PURPLE); p.setTextSize(18); p.setTypeface(Typeface.DEFAULT_BOLD);
        c.drawText("LOTOMANIA - ESTRUTURA LIVRE", 30, y, p); y += 26;

        p.setColor(Color.BLACK); p.setTextSize(9); p.setTypeface(Typeface.MONOSPACE);

        List<String> lines = pdfLines(lastAnalysis);
        for (String line : lines) {
            if (y > 805) {
                pdf.finishPage(page);
                pageNo++;
                page = pdf.startPage(new PdfDocument.PageInfo.Builder(595,842,pageNo).create());
                c = page.getCanvas();
                y = 35;
                p.setColor(Color.BLACK); p.setTextSize(9); p.setTypeface(Typeface.MONOSPACE);
            }
            c.drawText(line.length() > 100 ? line.substring(0,100) : line, 30, y, p);
            y += 12;
        }

        pdf.finishPage(page);

        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
            pdf.writeTo(out);
            toast("PDF gerado.");
        } catch (Exception e) {
            toast("Erro ao gerar PDF: " + e.getMessage());
        } finally {
            pdf.close();
        }
    }

    private List<String> pdfLines(Analysis a) {
        List<String> out = new ArrayList<>();
        out.add("JANELA: " + a.interval.get(0).number + " A " + a.interval.get(a.interval.size()-1).number);
        out.add("ESTRUTURA EXATA: " + (a.totalDiff == 0 ? "SIM" : "NAO") + " | DIFERENCA TOTAL: " + a.totalDiff);
        out.add("AUSENTES: " + fmt(a.absent) + " | INCLUIDAS " + a.absentIncluded + "/" + a.absent.size());
        out.add("");
        out.add("JOGO DE 50:");
        List<Integer> game = new ArrayList<>(a.game);
        for (int i = 0; i < game.size(); i += 10) {
            out.add(fmt(new TreeSet<>(game.subList(i, Math.min(i+10,game.size())))));
        }
        out.add("");
        out.add("PONTUACAO:");
        for (int i = 0; i < a.interval.size(); i++) {
            out.add("Concurso " + a.interval.get(i).number
                    + " | desejado " + a.targets[i]
                    + " | encontrado " + a.hits[i]);
        }
        out.add("");
        out.add("LINHAS:");
        for (int r = 0; r < 10; r++) {
            out.add(String.format(Locale.US,"%02d-%02d: %d", r*10,r*10+9,a.lineCounts[r]));
        }
        out.add("");
        out.add("JUSTIFICATIVA:");
        for (int d : a.game) {
            List<String> motivos = new ArrayList<>();
            if (a.absent.contains(d)) motivos.add("AUSENTE");
            for (Contest ct : a.interval) if (ct.numbers.contains(d)) motivos.add(String.valueOf(ct.number));
            out.add(String.format(Locale.US,"%02d : %s", d,
                    motivos.isEmpty() ? "equilibrio/troca" : android.text.TextUtils.join(" | ", motivos)));
        }
        return out;
    }

    private List<Contest> interval(int start, int end) {
        List<Contest> out = new ArrayList<>();
        for (Contest c : contests) if (c.number >= start && c.number <= end) out.add(c);
        return out;
    }

    private int hits(Set<Integer> game, Set<Integer> nums) {
        int h = 0;
        for (int d : game) if (nums.contains(d)) h++;
        return h;
    }

    private Set<Integer> parseAbsent(String s) {
        Set<Integer> out = new TreeSet<>();
        Matcher m = Pattern.compile("\\d+").matcher(s == null ? "" : s);
        while (m.find()) {
            try {
                int n = Integer.parseInt(m.group());
                if (n >= 0 && n <= 99) out.add(n);
            } catch (Exception ignored) {}
        }
        return out;
    }

    private Integer intText(EditText e) {
        try { return Integer.parseInt(e.getText().toString().trim()); }
        catch (Exception ex) { return null; }
    }

    private String fmt(Set<Integer> s) {
        StringBuilder b = new StringBuilder();
        int i = 0;
        for (int n : s) {
            if (i++ > 0) b.append(" ");
            b.append(String.format(Locale.US,"%02d",n));
        }
        return b.toString();
    }

    private void addSection(String title, String body) {
        TextView t = text(title, 19, PURPLE, true);
        resultBox.addView(t, top(18));
        TextView b = text(body, 15, Color.rgb(20,20,20), false);
        b.setTypeface(Typeface.MONOSPACE);
        b.setPadding(dp(12),dp(12),dp(12),dp(12));
        b.setBackgroundColor(Color.WHITE);
        resultBox.addView(b, top(6));
    }

    private String displayName(Uri uri) {
        String name = "arquivo";
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception ignored) {}
        return name;
    }

    private String formatEta(long sec) {
        if (sec < 60) return sec + "s";
        return (sec/60) + "m " + (sec%60) + "s";
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private EditText numberEdit(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setTextSize(18);
        return e;
    }

    private Button button(String label, int bg, int fg) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(16);
        b.setTextColor(fg);
        b.setBackgroundColor(bg);
        b.setAllCaps(false);
        b.setMinHeight(dp(58));
        return b;
    }

    private LinearLayout.LayoutParams top(int px) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
        p.topMargin = dp(px);
        return p;
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        Toast.makeText(this,s,Toast.LENGTH_LONG).show();
    }

    private interface ProgressReporter {
        void update(int percent, String message);
    }

    private static class Contest {
        final int number;
        final Set<Integer> numbers = new HashSet<>();
        Contest(int n, List<Integer> nums) {
            number = n;
            numbers.addAll(nums);
        }
    }

    private static class TargetRow {
        final int contest;
        final EditText edit;
        TargetRow(int contest, EditText edit) {
            this.contest = contest;
            this.edit = edit;
        }
    }

    private static class Candidate {
        Set<Integer> game;
        int[] hits;
        int[] targets;
        int totalDiff;
        int absentIncluded;
        int[] lineCounts;
        int minLineCount;
        int lineDeficit;
        int sumHits;
        boolean exact;

        Candidate copy() {
            Candidate c = new Candidate();
            c.game = new HashSet<>(game);
            c.hits = hits.clone();
            c.targets = targets.clone();
            c.totalDiff = totalDiff;
            c.absentIncluded = absentIncluded;
            c.lineCounts = lineCounts.clone();
            c.minLineCount = minLineCount;
            c.lineDeficit = lineDeficit;
            c.sumHits = sumHits;
            c.exact = exact;
            return c;
        }
    }

    private static class Analysis {
        Set<Integer> game;
        List<Contest> interval;
        int[] targets;
        int[] hits;
        Set<Integer> absent;
        int absentIncluded;
        int[] lineCounts;
        int minLineCount;
        int totalDiff;
        boolean exact;
    }
}
