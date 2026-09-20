package com.lotofacil.falhas;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MotorCore {
    private MotorCore() {}

    public interface Progress {
        void update(String stage, int percent);
    }

    public static final int MIN_HIST = 70;
    private static final int MAX_BACKTEST = 120;
    private static final int LOOKBACK_ESTADO = 500;
    private static final int LOOKBACK_INFLUENCIA = 400;
    private static final int LOOKBACK_DUPLAS = 220;
    private static final int LOOKBACK_REPETICAO = 180;
    private static final int LOOKBACK_PADRAO = 180;
    private static final int LOOKBACK_PERIMETRO = 140;

    private static final Set<Integer> PRIMOS = setOf(2,3,5,7,11,13,17,19,23);
    private static final Set<Integer> FIB = setOf(1,2,3,5,8,13,21);
    private static final Set<Integer> MIOLO = setOf(7,8,9,12,13,14,17,18,19);

    private static final Map<String, double[]> PROFILES = new LinkedHashMap<>();
    static {
        // estado, influencia, duplas, recente, tendencia, historica, pareto
        PROFILES.put("PARETO",     new double[]{0.22,0.22,0.16,0.14,0.10,0.08,0.08});
        PROFILES.put("PADRAO",     new double[]{0.36,0.14,0.10,0.13,0.12,0.08,0.07});
        PROFILES.put("INFLUENCIA", new double[]{0.15,0.34,0.25,0.09,0.07,0.05,0.05});
        PROFILES.put("RECENTE",    new double[]{0.13,0.14,0.09,0.30,0.20,0.08,0.06});
        PROFILES.put("CONSENSO",   new double[]{0.20,0.20,0.15,0.16,0.12,0.09,0.08});
    }

    public static final class Record {
        public int contest;
        public int[] draw;
        public int[] failures;
        public int drawMask;
        public int failMask;
    }

    public static final class Feature {
        public double estado, influencia, duplas, recente, tendencia, historica, pareto;
        public double lag1, lag2, lag3, f10, f20, f50, f100;
        public int casosEstado, streak, paretoLayer;
        public String estadoTxt;
    }

    public static final class Stats {
        public int tests;
        public int sumPoints;
        public final int[] dist = new int[16];
        public double media;
        public double score;
    }

    public static final class BacktestLine {
        public String profile;
        public Stats stats;
    }

    public static final class Perimeter {
        public double media, score;
        public int p12, p13, p14, p15;
    }

    public static final class Props {
        public int pares, primos, fib, miolo, moldura, soma, seq, rep;
        public int[] linhas, colunas;
    }

    private static final class PatternModel {
        Map<String, Double> mean = new HashMap<>();
        Map<String, Double> sd = new HashMap<>();
        double lineMean, lineSd, colMean, colSd;
    }

    private static final class Candidate {
        int[] failures;
        int[] game;
        double score, individual, pareto, pattern, synergy;
        Perimeter perimeter;
        Props props;
    }

    public static final class AnalysisResult {
        public int lastContest;
        public int[] failures;
        public int[] game;
        public int repeatedFailures;
        public int repetitionPrincipal;
        public int[] topRepetitions;
        public Map<Integer,Integer> repetitionCounts = new TreeMap<>();
        public String championProfile;
        public List<BacktestLine> backtest = new ArrayList<>();
        public double finalScore;
        public Perimeter perimeter;
        public Props props;
        public String report;
    }

    private static Set<Integer> setOf(int... nums) {
        Set<Integer> s = new HashSet<>();
        for (int n : nums) s.add(n);
        return s;
    }

    private static int bit(int n) { return 1 << (n - 1); }

    private static int mask(int[] nums) {
        int m = 0;
        for (int n : nums) m |= bit(n);
        return m;
    }

    private static int[] sortedArray(Collection<Integer> c) {
        int[] a = new int[c.size()];
        int i = 0;
        for (int n : c) a[i++] = n;
        Arrays.sort(a);
        return a;
    }

    private static int[] complement(int[] nums) {
        boolean[] present = new boolean[26];
        for (int n : nums) if (n >= 1 && n <= 25) present[n] = true;
        int[] out = new int[10];
        int k = 0;
        for (int n=1;n<=25;n++) if (!present[n]) out[k++] = n;
        return out;
    }

    public static String fmt(int[] nums) {
        int[] c = nums.clone();
        Arrays.sort(c);
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<c.length;i++) {
            if (i>0) sb.append(' ');
            sb.append(String.format(Locale.US, "%02d", c[i]));
        }
        return sb.toString();
    }

    public static List<Record> parseText(String text) {
        List<Record> records = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String[] lines = text.split("\\R");
        int sequential = 1;
        Pattern p = Pattern.compile("\\d+");

        for (String line : lines) {
            Matcher m = p.matcher(line);
            List<Integer> vals = new ArrayList<>();
            while (m.find()) {
                try { vals.add(Integer.parseInt(m.group())); } catch (Exception ignored) {}
            }
            if (vals.size() < 15) continue;

            int bestStart = -1;
            int[] bestDraw = null;
            for (int i=0;i<=vals.size()-15;i++) {
                int[] block = new int[15];
                boolean ok = true;
                boolean[] used = new boolean[26];
                for (int j=0;j<15;j++) {
                    int n = vals.get(i+j);
                    if (n < 1 || n > 25 || used[n]) { ok = false; break; }
                    used[n] = true;
                    block[j] = n;
                }
                if (ok) {
                    Arrays.sort(block);
                    bestStart = i;
                    bestDraw = block;
                }
            }
            if (bestDraw == null) continue;

            int contest = sequential;
            for (int i=bestStart-1;i>=0;i--) {
                if (vals.get(i) > 25) { contest = vals.get(i); break; }
            }
            if (contest == sequential && vals.size() >= 16 && vals.get(0) > 25) contest = vals.get(0);

            String key = contest + ":" + Arrays.toString(bestDraw);
            if (!seen.add(key)) continue;

            Record r = new Record();
            r.contest = contest;
            r.draw = bestDraw;
            r.failures = complement(bestDraw);
            if (r.failures.length != 10) continue;
            r.drawMask = mask(r.draw);
            r.failMask = mask(r.failures);
            records.add(r);
            sequential++;
        }

        records.sort(Comparator.comparingInt(a -> a.contest));
        if (records.size() < MIN_HIST) {
            throw new IllegalArgumentException("Foram encontrados apenas " + records.size() + " concursos válidos. Use pelo menos " + MIN_HIST + ".");
        }
        return records;
    }

    private static double mean(List<Double> v) {
        if (v.isEmpty()) return 0.0;
        double s=0; for(double x:v)s+=x; return s/v.size();
    }

    private static double meanInt(List<Integer> v) {
        if (v.isEmpty()) return 0.0;
        long s=0; for(int x:v)s+=x; return (double)s/v.size();
    }

    private static double sdInt(List<Integer> v) {
        if (v.size()<2) return 0.0;
        double m=meanInt(v), s=0;
        for(int x:v){ double d=x-m; s+=d*d; }
        return Math.sqrt(s/v.size());
    }

    private static double clamp(double x) { return Math.max(0.0, Math.min(1.0, x)); }
    private static double laplace(int success, int total, double a, double b) { return (success+a)/(total+a+b); }

    private static double failRate(List<Record> records, int d, int window) {
        int start = window > 0 ? Math.max(0, records.size()-window) : 0;
        int cnt=0;
        int b=bit(d);
        for(int i=start;i<records.size();i++) if((records.get(i).failMask & b)!=0) cnt++;
        return (double)cnt/(records.size()-start);
    }

    private static int streak(List<Record> records, int d) {
        int b=bit(d), s=0;
        for(int i=records.size()-1;i>=0;i--) {
            if((records.get(i).failMask & b)!=0) s++; else break;
        }
        return s;
    }

    private static Object[] state3(List<Record> records, int d) {
        int b=bit(d);
        int[] current = new int[3];
        for(int j=0;j<3;j++) current[j]=((records.get(records.size()-3+j).failMask & b)!=0)?1:0;
        int start=Math.max(2, records.size()-LOOKBACK_ESTADO);
        int total=0, success=0;
        for(int i=start;i<records.size()-1;i++) {
            int a=((records.get(i-2).failMask & b)!=0)?1:0;
            int c=((records.get(i-1).failMask & b)!=0)?1:0;
            int e=((records.get(i).failMask & b)!=0)?1:0;
            if(a==current[0] && c==current[1] && e==current[2]) {
                total++;
                if((records.get(i+1).failMask & b)!=0) success++;
            }
        }
        double base=failRate(records,d,0);
        double prob=total>0?laplace(success,total,2.0,3.0):base;
        String txt=""+(current[0]==1?'F':'S')+(current[1]==1?'F':'S')+(current[2]==1?'F':'S');
        return new Object[]{prob,total,txt};
    }

    private static final class LagData {
        int[][] totals = new int[4][26];
        int[][][] xy = new int[4][26][26];
    }

    private static LagData buildLags(List<Record> records) {
        LagData d=new LagData();
        int start=Math.max(0, records.size()-LOOKBACK_INFLUENCIA-3);
        for(int gap=1;gap<=3;gap++) {
            for(int i=start;i<records.size()-gap;i++) {
                for(int x:records.get(i).failures) {
                    d.totals[gap][x]++;
                    for(int y:records.get(i+gap).failures) d.xy[gap][x][y]++;
                }
            }
        }
        return d;
    }

    private static double influenceFrom(int[] source, int target, int gap, LagData ld, double base) {
        List<Double> vals=new ArrayList<>();
        for(int x:source) {
            int t=ld.totals[gap][x];
            if(t<=0) continue;
            vals.add(laplace(ld.xy[gap][x][target],t,1.0,1.5));
        }
        if(vals.isEmpty()) return base;
        vals.sort(Collections.reverseOrder());
        double all=mean(vals);
        double top=0; int n=Math.min(5,vals.size());
        for(int i=0;i<n;i++) top+=vals.get(i);
        top/=n;
        return all*0.65+top*0.35;
    }

    private static final class PairData {
        List<int[]> pairs = new ArrayList<>();
        int[] total;
        int[][] next;
    }

    private static PairData buildPairs(List<Record> records) {
        int[] f1=records.get(records.size()-1).failures;
        PairData pd=new PairData();
        for(int i=0;i<f1.length;i++) for(int j=i+1;j<f1.length;j++) pd.pairs.add(new int[]{f1[i],f1[j]});
        pd.total=new int[pd.pairs.size()];
        pd.next=new int[pd.pairs.size()][26];
        int start=Math.max(0, records.size()-LOOKBACK_DUPLAS-1);
        for(int i=start;i<records.size()-1;i++) {
            int fm=records.get(i).failMask;
            for(int p=0;p<pd.pairs.size();p++) {
                int[] pair=pd.pairs.get(p);
                int pm=bit(pair[0])|bit(pair[1]);
                if((fm&pm)==pm) {
                    pd.total[p]++;
                    for(int y:records.get(i+1).failures) pd.next[p][y]++;
                }
            }
        }
        return pd;
    }

    private static double pairInfluence(int target, PairData pd, double base) {
        List<Double> vals=new ArrayList<>();
        for(int p=0;p<pd.pairs.size();p++) {
            if(pd.total[p]<3) continue;
            vals.add(laplace(pd.next[p][target],pd.total[p],1.0,1.5));
        }
        if(vals.isEmpty()) return base;
        vals.sort(Collections.reverseOrder());
        int n=Math.min(7,vals.size()); double s=0;
        for(int i=0;i<n;i++)s+=vals.get(i);
        return s/n;
    }

    private static Map<Integer,Integer> paretoLayers(Map<Integer,double[]> obj) {
        Set<Integer> remaining=new HashSet<>(obj.keySet());
        Map<Integer,Integer> layer=new HashMap<>();
        int level=0;
        while(!remaining.isEmpty()) {
            List<Integer> front=new ArrayList<>();
            for(int a:remaining) {
                boolean dominated=false;
                double[] va=obj.get(a);
                for(int b:remaining) {
                    if(a==b)continue;
                    double[] vb=obj.get(b);
                    boolean noWorse=true, better=false;
                    for(int k=0;k<va.length;k++) {
                        if(vb[k]<va[k]) { noWorse=false; break; }
                        if(vb[k]>va[k]) better=true;
                    }
                    if(noWorse&&better){dominated=true;break;}
                }
                if(!dominated)front.add(a);
            }
            if(front.isEmpty()) {
                for(int a:remaining)layer.put(a,level);
                break;
            }
            for(int a:front){layer.put(a,level);remaining.remove(a);} level++;
        }
        return layer;
    }

    private static Feature[] buildFeatures(List<Record> records) {
        Feature[] f=new Feature[26];
        LagData ld=buildLags(records);
        PairData pd=buildPairs(records);
        int[] f1=records.get(records.size()-1).failures;
        int[] f2=records.get(records.size()-2).failures;
        int[] f3=records.get(records.size()-3).failures;
        Map<Integer,double[]> objs=new HashMap<>();

        for(int d=1;d<=25;d++) {
            Feature x=new Feature();
            double hist=failRate(records,d,0);
            x.f10=failRate(records,d,10); x.f20=failRate(records,d,20); x.f50=failRate(records,d,50); x.f100=failRate(records,d,100);
            Object[] st=state3(records,d);
            x.estado=(double)st[0]; x.casosEstado=(int)st[1]; x.estadoTxt=(String)st[2];
            x.lag1=influenceFrom(f1,d,1,ld,hist); x.lag2=influenceFrom(f2,d,2,ld,hist); x.lag3=influenceFrom(f3,d,3,ld,hist);
            x.influencia=x.lag1*0.56+x.lag2*0.29+x.lag3*0.15;
            x.duplas=pairInfluence(d,pd,hist);
            x.recente=x.f20*0.50+x.f50*0.30+x.f100*0.20;
            x.streak=streak(records,d);
            x.tendencia=clamp(0.40+(x.f10-x.f50)*0.85+(x.f20-x.f100)*0.45+Math.min(x.streak,4)*0.025);
            x.historica=hist;
            f[d]=x;
            objs.put(d,new double[]{x.estado,x.influencia,x.duplas,x.recente,x.tendencia});
        }
        Map<Integer,Integer> layers=paretoLayers(objs);
        int max=0; for(int v:layers.values())max=Math.max(max,v);
        for(int d=1;d<=25;d++) {
            f[d].paretoLayer=layers.get(d);
            f[d].pareto=max==0?1.0:1.0-(double)f[d].paretoLayer/(max+1.0);
        }
        return f;
    }

    private static double profileScore(Feature f, String profile) {
        double[] w=PROFILES.get(profile);
        return f.estado*w[0]+f.influencia*w[1]+f.duplas*w[2]+f.recente*w[3]+f.tendencia*w[4]+f.historica*w[5]+f.pareto*w[6];
    }

    private static final class RepetitionModel {
        int principal;
        int[] top;
        Map<Integer,Integer> counts=new TreeMap<>();
    }

    private static RepetitionModel learnRepetition(List<Record> records) {
        int start=Math.max(0,records.size()-LOOKBACK_REPETICAO-1);
        Map<Integer,Double> weights=new HashMap<>();
        Map<Integer,Integer> counts=new HashMap<>();
        int total=Math.max(1,(records.size()-1)-start);
        int pos=0;
        for(int i=start;i<records.size()-1;i++,pos++) {
            int rep=Integer.bitCount(records.get(i).failMask & records.get(i+1).failMask);
            double weight=1.0+2.0*((double)(pos+1)/total);
            weights.put(rep,weights.getOrDefault(rep,0.0)+weight);
            counts.put(rep,counts.getOrDefault(rep,0)+1);
        }
        List<Integer> keys=new ArrayList<>(weights.keySet());
        keys.sort((a,b)->{
            int c=Double.compare(weights.get(b),weights.get(a)); if(c!=0)return c;
            c=Integer.compare(counts.getOrDefault(b,0),counts.getOrDefault(a,0)); if(c!=0)return c;
            return Integer.compare(Math.abs(a-4),Math.abs(b-4));
        });
        RepetitionModel rm=new RepetitionModel();
        rm.principal=keys.isEmpty()?4:keys.get(0);
        int n=Math.min(3,keys.size()); rm.top=new int[n]; for(int i=0;i<n;i++)rm.top[i]=keys.get(i);
        rm.counts.putAll(counts);
        return rm;
    }

    private static int[] buildGroup(Feature[] f, int lastFailMask, int repetitions, String profile) {
        List<Integer> inside=new ArrayList<>(), outside=new ArrayList<>();
        for(int d=1;d<=25;d++) {
            if((lastFailMask&bit(d))!=0)inside.add(d); else outside.add(d);
        }
        Comparator<Integer> cmp=(a,b)->{
            int c=Double.compare(profileScore(f[b],profile),profileScore(f[a],profile)); if(c!=0)return c;
            c=Integer.compare(f[a].paretoLayer,f[b].paretoLayer); if(c!=0)return c;
            c=Double.compare(f[b].estado,f[a].estado); if(c!=0)return c;
            c=Double.compare(f[b].influencia,f[a].influencia); if(c!=0)return c;
            c=Double.compare(f[b].duplas,f[a].duplas); if(c!=0)return c;
            return Integer.compare(a,b);
        };
        inside.sort(cmp); outside.sort(cmp);
        repetitions=Math.max(0,Math.min(10,repetitions)); int fresh=10-repetitions;
        if(repetitions>inside.size()||fresh>outside.size())throw new IllegalStateException("Quantidade de repetidas incompatível.");
        int[] out=new int[10]; int k=0;
        for(int i=0;i<repetitions;i++)out[k++]=inside.get(i);
        for(int i=0;i<fresh;i++)out[k++]=outside.get(i);
        Arrays.sort(out); return out;
    }

    private static int pointsFromFailures(int k){return 5+k;}

    private static void register(Stats s,int[] pred,Record target) {
        int k=Integer.bitCount(mask(pred)&target.failMask);
        int pts=pointsFromFailures(k);
        s.tests++; s.sumPoints+=pts; s.dist[pts]++;
    }

    private static double backtestScore(Stats s) {
        int n=Math.max(1,s.tests); double avg=(double)s.sumPoints/n;
        return s.dist[15]*500.0+s.dist[14]*120.0+s.dist[13]*28.0+s.dist[12]*7.0+s.dist[11]*1.5+avg*n*0.20;
    }

    private static List<BacktestLine> backtest(List<Record> records, Progress progress) {
        int start=Math.max(MIN_HIST,records.size()-MAX_BACKTEST);
        Map<String,Stats> stats=new LinkedHashMap<>();
        for(String p:PROFILES.keySet())stats.put(p,new Stats());
        int total=records.size()-start;
        for(int idx=start;idx<records.size();idx++) {
            List<Record> hist=records.subList(0,idx);
            Record target=records.get(idx);
            Feature[] feat=buildFeatures(hist);
            RepetitionModel rm=learnRepetition(hist);
            int lastFail=hist.get(hist.size()-1).failMask;
            for(String p:PROFILES.keySet()) register(stats.get(p),buildGroup(feat,lastFail,rm.principal,p),target);
            int done=idx-start+1;
            if(progress!=null)progress.update("Backtest cego " + done + "/" + total,5+(int)(55.0*done/Math.max(1,total)));
        }
        List<BacktestLine> lines=new ArrayList<>();
        for(String p:PROFILES.keySet()) {
            Stats s=stats.get(p); s.media=s.tests>0?(double)s.sumPoints/s.tests:0.0; s.score=backtestScore(s);
            BacktestLine l=new BacktestLine(); l.profile=p;l.stats=s;lines.add(l);
        }
        lines.sort((a,b)->{
            int c=Double.compare(b.stats.score,a.stats.score); if(c!=0)return c;
            c=Integer.compare(b.stats.dist[15],a.stats.dist[15]); if(c!=0)return c;
            c=Integer.compare(b.stats.dist[14],a.stats.dist[14]); if(c!=0)return c;
            c=Integer.compare(b.stats.dist[13],a.stats.dist[13]); if(c!=0)return c;
            return Double.compare(b.stats.media,a.stats.media);
        });
        return lines;
    }

    private static Props props(int[] game,int[] last) {
        Props p=new Props();
        Set<Integer>s=new HashSet<>();for(int n:game)s.add(n);
        for(int n:game){if(n%2==0)p.pares++;if(PRIMOS.contains(n))p.primos++;if(FIB.contains(n))p.fib++;if(MIOLO.contains(n))p.miolo++;else p.moldura++;p.soma+=n;}
        p.linhas=new int[5];p.colunas=new int[5];
        for(int n:game){p.linhas[(n-1)/5]++;p.colunas[(n-1)%5]++;}
        int seq=0,max=0,prev=-99;for(int n:game){if(n==prev+1)seq++;else seq=1;max=Math.max(max,seq);prev=n;}p.seq=max;
        if(last!=null){Set<Integer>ls=new HashSet<>();for(int n:last)ls.add(n);for(int n:game)if(ls.contains(n))p.rep++;}
        return p;
    }

    private static PatternModel learnPattern(List<Record> records) {
        int start=Math.max(0,records.size()-LOOKBACK_PADRAO);
        Map<String,List<Integer>> v=new HashMap<>();
        for(String k:new String[]{"pares","primos","fib","miolo","soma","seq","rep"})v.put(k,new ArrayList<>());
        List<Integer> lines=new ArrayList<>(),cols=new ArrayList<>();
        for(int i=start;i<records.size();i++) {
            int[] last=i>start?records.get(i-1).draw:null;
            Props p=props(records.get(i).draw,last);
            v.get("pares").add(p.pares);v.get("primos").add(p.primos);v.get("fib").add(p.fib);v.get("miolo").add(p.miolo);v.get("soma").add(p.soma);v.get("seq").add(p.seq);if(last!=null)v.get("rep").add(p.rep);
            for(int x:p.linhas)lines.add(x);for(int x:p.colunas)cols.add(x);
        }
        PatternModel m=new PatternModel();
        for(String k:v.keySet()){m.mean.put(k,meanInt(v.get(k)));m.sd.put(k,Math.max(sdInt(v.get(k)),0.75));}
        m.lineMean=meanInt(lines);m.lineSd=Math.max(sdInt(lines),0.75);m.colMean=meanInt(cols);m.colSd=Math.max(sdInt(cols),0.75);return m;
    }

    private static double patternScore(Props p, PatternModel m) {
        double z=0;
        z+=Math.abs(p.pares-m.mean.get("pares"))/m.sd.get("pares");
        z+=Math.abs(p.primos-m.mean.get("primos"))/m.sd.get("primos");
        z+=Math.abs(p.fib-m.mean.get("fib"))/m.sd.get("fib");
        z+=Math.abs(p.miolo-m.mean.get("miolo"))/m.sd.get("miolo");
        z+=Math.abs(p.soma-m.mean.get("soma"))/m.sd.get("soma");
        z+=Math.abs(p.seq-m.mean.get("seq"))/m.sd.get("seq");
        z+=Math.abs(p.rep-m.mean.get("rep"))/m.sd.get("rep");
        for(int x:p.linhas)z+=0.35*Math.abs(x-m.lineMean)/m.lineSd;
        for(int x:p.colunas)z+=0.35*Math.abs(x-m.colMean)/m.colSd;
        return 1.0/(1.0+z/10.0);
    }

    private static Perimeter perimeter(List<Record> records,int[] failures) {
        Perimeter p=new Perimeter();
        int fm=mask(failures);int end=Math.max(0,records.size()-1);int start=Math.max(0,end-LOOKBACK_PERIMETRO);int n=0,sum=0;
        for(int i=start;i<end;i++) {
            int pts=5+Integer.bitCount(fm&records.get(i).failMask);n++;sum+=pts;
            if(pts>=12)p.p12++;if(pts>=13)p.p13++;if(pts>=14)p.p14++;if(pts>=15)p.p15++;
        }
        if(n==0)return p;
        p.media=(double)sum/n;
        p.score=clamp(((p.media-5.0)/10.0)*0.45+((double)p.p12/n)*0.25+((double)p.p13/n)*0.18+((double)p.p14/n)*0.09+((double)p.p15/n)*0.03);
        return p;
    }

    private static double synergy(List<Record> records,int[] failures) {
        int start=Math.max(0,records.size()-Math.min(180,records.size()));
        int n=records.size()-start; if(n<=0)return 0;
        double sum=0;int pairs=0;
        for(int i=0;i<failures.length;i++)for(int j=i+1;j<failures.length;j++) {
            int pm=bit(failures[i])|bit(failures[j]);int c=0;
            for(int k=start;k<records.size();k++)if((records.get(k).failMask&pm)==pm)c++;
            sum+=(double)c/n;pairs++;
        }
        return pairs==0?0:clamp((sum/pairs)/0.25);
    }

    private static int[] gameFromFailures(int[] failures) {
        Set<Integer> f=new HashSet<>();for(int n:failures)f.add(n);int[] g=new int[15];int k=0;for(int n=1;n<=25;n++)if(!f.contains(n))g[k++]=n;return g;
    }

    private static Candidate evaluate(List<Record> records,Feature[] f,int[] failures,String profile,PatternModel pm) {
        Candidate c=new Candidate();c.failures=failures.clone();c.game=gameFromFailures(failures);
        double ind=0,par=0;for(int d:failures){ind+=profileScore(f[d],profile);par+=f[d].pareto;}ind/=10;par/=10;
        c.individual=ind;c.pareto=par;c.perimeter=perimeter(records,failures);c.props=props(c.game,records.get(records.size()-1).draw);c.pattern=patternScore(c.props,pm);c.synergy=synergy(records,failures);
        c.score=ind*0.42+c.perimeter.score*0.22+c.pattern*0.16+par*0.10+c.synergy*0.10;return c;
    }

    private static String key(int[] a){return Arrays.toString(a);}

    private static List<int[]> neighbors(int[] base,int lastFailMask) {
        Set<String>seen=new HashSet<>();List<int[]>out=new ArrayList<>();
        int[] b=base.clone();Arrays.sort(b);seen.add(key(b));out.add(b);
        Set<Integer>bs=new HashSet<>();for(int x:b)bs.add(x);
        List<Integer>inSel=new ArrayList<>(),outSel=new ArrayList<>(),inAvail=new ArrayList<>(),outAvail=new ArrayList<>();
        for(int x:b){if((lastFailMask&bit(x))!=0)inSel.add(x);else outSel.add(x);}
        for(int d=1;d<=25;d++)if(!bs.contains(d)){if((lastFailMask&bit(d))!=0)inAvail.add(d);else outAvail.add(d);}
        for(int rem:inSel)for(int add:inAvail){Set<Integer>s=new HashSet<>(bs);s.remove(rem);s.add(add);int[]a=sortedArray(s);if(seen.add(key(a)))out.add(a);}
        for(int rem:outSel)for(int add:outAvail){Set<Integer>s=new HashSet<>(bs);s.remove(rem);s.add(add);int[]a=sortedArray(s);if(seen.add(key(a)))out.add(a);}
        return out;
    }

    private static Candidate buildFinal(List<Record> records,Feature[] feat,String champion,int[] reps,Progress progress) {
        PatternModel pm=learnPattern(records);int lastFail=records.get(records.size()-1).failMask;
        LinkedHashMap<String,int[]> candidates=new LinkedHashMap<>();
        for(int r:reps)for(String p:PROFILES.keySet())for(int[]a:neighbors(buildGroup(feat,lastFail,r,p),lastFail))candidates.put(key(a),a);
        Candidate best=null;int i=0,total=candidates.size();
        for(int[]a:candidates.values()) {
            Candidate c=evaluate(records,feat,a,champion,pm);
            if(best==null || compareCandidate(c,best)>0)best=c;
            i++; if(progress!=null && (i==total||i%50==0))progress.update("Refinando grupos de falha " + i + "/" + total,65+(int)(30.0*i/Math.max(1,total)));
        }
        return best;
    }

    private static int compareCandidate(Candidate a,Candidate b) {
        int c=Double.compare(a.score,b.score);if(c!=0)return c;
        c=Integer.compare(a.perimeter.p14,b.perimeter.p14);if(c!=0)return c;
        c=Integer.compare(a.perimeter.p13,b.perimeter.p13);if(c!=0)return c;
        c=Integer.compare(a.perimeter.p12,b.perimeter.p12);if(c!=0)return c;
        c=Double.compare(a.pattern,b.pattern);if(c!=0)return c;
        return Double.compare(a.individual,b.individual);
    }

    public static AnalysisResult analyze(List<Record> records, Progress progress) {
        if(records==null||records.size()<MIN_HIST)throw new IllegalArgumentException("Histórico insuficiente.");
        if(progress!=null)progress.update("Iniciando backtest cego...",3);
        List<BacktestLine> bt=backtest(records,progress);
        String champion=bt.get(0).profile;
        if(progress!=null)progress.update("Calculando padrão, Pareto e influências atuais...",62);
        Feature[] feat=buildFeatures(records);
        RepetitionModel rm=learnRepetition(records);
        Candidate best=buildFinal(records,feat,champion,rm.top,progress);
        if(best==null)throw new IllegalStateException("Nenhum grupo final foi encontrado.");
        AnalysisResult r=new AnalysisResult();
        r.lastContest=records.get(records.size()-1).contest;r.failures=best.failures;r.game=best.game;r.repeatedFailures=Integer.bitCount(mask(best.failures)&records.get(records.size()-1).failMask);r.repetitionPrincipal=rm.principal;r.topRepetitions=rm.top;r.repetitionCounts.putAll(rm.counts);r.championProfile=champion;r.backtest=bt;r.finalScore=best.score;r.perimeter=best.perimeter;r.props=best.props;r.report=buildReport(records,r,feat);
        if(progress!=null)progress.update("Análise concluída.",100);
        return r;
    }

    private static String buildReport(List<Record> records,AnalysisResult r,Feature[] f) {
        StringBuilder s=new StringBuilder();
        s.append("LOTOFACIL - MOTOR DE FALHAS APK V1\n");
        s.append("PADRAO + PARETO + INFLUENCIA + BACKTEST CEGO\n");
        s.append("============================================================\n");
        s.append("Concursos carregados: ").append(records.size()).append('\n');
        s.append("Primeiro: ").append(records.get(0).contest).append(" | Ultimo: ").append(records.get(records.size()-1).contest).append("\n\n");
        s.append("ULTIMOS 3 CONCURSOS\n");
        for(int i=records.size()-3;i<records.size();i++)s.append("Concurso ").append(records.get(i).contest).append(" | Falhas: ").append(fmt(records.get(i).failures)).append('\n');
        s.append("\nBACKTEST CEGO\n");
        for(BacktestLine l:r.backtest)s.append(String.format(Locale.US,"%-11s | media=%.3f | 15=%d | 14=%d | 13=%d | 12=%d | 11=%d\n",l.profile,l.stats.media,l.stats.dist[15],l.stats.dist[14],l.stats.dist[13],l.stats.dist[12],l.stats.dist[11]));
        s.append("\nPERFIL CAMPEAO: ").append(r.championProfile).append('\n');
        s.append("Repeticao principal das falhas: ").append(r.repetitionPrincipal).append('\n');
        s.append("Top repeticoes: ").append(Arrays.toString(r.topRepetitions)).append("\n\n");
        s.append("10 FALHAS PROJETADAS:\n").append(fmt(r.failures)).append("\n\n");
        s.append("JOGO COMPLEMENTAR DE 15:\n").append(fmt(r.game)).append("\n\n");
        s.append("Falhas repetidas do ultimo: ").append(r.repeatedFailures).append('\n');
        s.append(String.format(Locale.US,"Score final: %.6f\n",r.finalScore));
        s.append(String.format(Locale.US,"Perimetro: media=%.3f | 12+=%d | 13+=%d | 14+=%d | 15=%d\n",r.perimeter.media,r.perimeter.p12,r.perimeter.p13,r.perimeter.p14,r.perimeter.p15));
        s.append("Padrao jogo15: pares=").append(r.props.pares).append(" primos=").append(r.props.primos).append(" fib=").append(r.props.fib).append(" miolo=").append(r.props.miolo).append(" soma=").append(r.props.soma).append(" rep=").append(r.props.rep).append('\n');
        s.append("\nCONVERSAO\n10 falhas certas=15 pontos | 9=14 | 8=13 | 7=12 | 6=11 | 5=10 | 4=9 | 3=8 | 2=7\n");
        s.append("\nEstudo estatistico; nao garante premiacao.\n");
        return s.toString();
    }
}
