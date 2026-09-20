# -*- coding: utf-8 -*-
"""
LOTOFÁCIL - MOTOR DE FALHAS V2
PADRÃO + PARETO + INFLUÊNCIA + BACKTEST CEGO DOS 3 ÚLTIMOS

CONCEITO CORRETO
================
Na Lotofácil:
- universo = 25 dezenas
- saem 15
- ficam 10 no pote = FALHAS

O motor projeta 10 FALHAS.
O jogo final é o COMPLEMENTO dessas 10 falhas, portanto tem 15 dezenas.

Se o motor acertar:
10 falhas = jogo faz 15 pontos
 9 falhas = jogo faz 14 pontos
 8 falhas = jogo faz 13 pontos
 7 falhas = jogo faz 12 pontos
 6 falhas = jogo faz 11 pontos
 5 falhas = jogo faz 10 pontos
 4 falhas = jogo faz  9 pontos
 3 falhas = jogo faz  8 pontos
 2 falhas = jogo faz  7 pontos

O V2:
1) lê todo o histórico;
2) usa os 3 últimos concursos como estado atual;
3) estuda padrões F/S da própria dezena;
4) estuda influência de falhas com lag 1, 2 e 3;
5) estuda duplas das falhas do último concurso;
6) mede frequência recente, tendência e sequência de falha;
7) monta CAMADAS DE PARETO com vários critérios;
8) aprende quantas falhas costumam REPETIR do concurso anterior;
9) faz BACKTEST CEGO walk-forward, sem olhar o concurso-alvo;
10) testa vários perfis e escolhe o perfil campeão;
11) monta várias candidatas de 10 falhas;
12) mede padrão estrutural do jogo complementar de 15;
13) mede perímetro/talo histórico da candidata;
14) escolhe uma única projeção final de 10 falhas;
15) gera o jogo complementar de 15 dezenas.

Estudo estatístico. Não garante resultado futuro.
"""

import os
import re
import math
from itertools import combinations
from collections import Counter, defaultdict

# ============================================================
# CONFIGURAÇÃO
# ============================================================

DOWNLOADS = [
    "/storage/emulated/0/Download",
    "/storage/emulated/0/Downloads",
    os.path.expanduser("~/Download"),
    ".",
]

UNIVERSO = tuple(range(1, 26))
UNIVERSO_SET = set(UNIVERSO)
UNIVERSO_MASK = (1 << 25) - 1

PRIMOS = {2, 3, 5, 7, 11, 13, 17, 19, 23}
FIB = {1, 2, 3, 5, 8, 13, 21}
MIOLO = {7, 8, 9, 12, 13, 14, 17, 18, 19}
MOLDURA = UNIVERSO_SET - MIOLO

MAX_BACKTEST = 160
MIN_HIST_BACKTEST = 60

STATE_LOOKBACK = 600
INFLUENCE_LOOKBACK = 500
PAIR_LOOKBACK = 250
PATTERN_LOOKBACK = 180
PERIMETER_LOOKBACK = 140
REPEAT_LOOKBACK = 180

TOP_CANDIDATES_TO_REFINE = 250
ARQ_SAIDA = "LOTOFACIL_FALHAS_V2_PARETO_BACKTEST.txt"

# ============================================================
# PERFIS DE ESTRATÉGIA
# ============================================================

PROFILES = {
    "PARETO": {
        "estado": 0.22,
        "influencia": 0.22,
        "duplas": 0.16,
        "recente": 0.14,
        "tendencia": 0.10,
        "historica": 0.08,
        "pareto": 0.08,
    },
    "PADRAO": {
        "estado": 0.36,
        "influencia": 0.14,
        "duplas": 0.10,
        "recente": 0.13,
        "tendencia": 0.12,
        "historica": 0.08,
        "pareto": 0.07,
    },
    "INFLUENCIA": {
        "estado": 0.15,
        "influencia": 0.34,
        "duplas": 0.25,
        "recente": 0.09,
        "tendencia": 0.07,
        "historica": 0.05,
        "pareto": 0.05,
    },
    "RECENTE": {
        "estado": 0.13,
        "influencia": 0.14,
        "duplas": 0.09,
        "recente": 0.30,
        "tendencia": 0.20,
        "historica": 0.08,
        "pareto": 0.06,
    },
    "CONSENSO": {
        "estado": 0.20,
        "influencia": 0.20,
        "duplas": 0.15,
        "recente": 0.16,
        "tendencia": 0.12,
        "historica": 0.09,
        "pareto": 0.08,
    },
}

# ============================================================
# UTILIDADES
# ============================================================

def hr():
    print("=" * 96)


def fmt(nums):
    return " ".join(f"{n:02d}" for n in sorted(nums))


def bit(n):
    return 1 << (n - 1)


def mask_nums(nums):
    m = 0
    for n in nums:
        m |= bit(n)
    return m


def nums_from_mask(mask):
    return tuple(n for n in UNIVERSO if mask & bit(n))


def clamp(v, a=0.0, b=1.0):
    return max(a, min(b, v))


def mean(vals):
    return sum(vals) / len(vals) if vals else 0.0


def stdev(vals):
    if len(vals) < 2:
        return 0.0
    m = mean(vals)
    return math.sqrt(sum((x - m) ** 2 for x in vals) / len(vals))


def laplace(sucessos, total, alpha=1.0, beta=1.0):
    return (sucessos + alpha) / (total + alpha + beta)


def pontos_do_jogo_por_falhas(qtd_falhas_acertadas):
    return 5 + qtd_falhas_acertadas


# ============================================================
# DOWNLOAD / ARQUIVO
# ============================================================

def localizar_download():
    for pasta in DOWNLOADS:
        if os.path.isdir(pasta):
            return pasta
    raise RuntimeError("Pasta Download não encontrada.")


def escolher_arquivo():
    pasta = localizar_download()

    arquivos = []
    for nome in os.listdir(pasta):
        caminho = os.path.join(pasta, nome)
        if os.path.isfile(caminho) and nome.lower().endswith((".txt", ".csv")):
            arquivos.append(nome)

    arquivos.sort(key=str.lower)

    if not arquivos:
        raise RuntimeError(
            "Não encontrei TXT/CSV na pasta Download. "
            "Coloque o arquivo de resultados da Lotofácil na pasta Download."
        )

    hr()
    print("ARQUIVOS TXT/CSV DA PASTA DOWNLOAD")
    hr()

    for i, nome in enumerate(arquivos, 1):
        print(f"{i:03d} - {nome}")

    while True:
        s = input("\nDIGITE O NÚMERO DO ARQUIVO DA LOTOFÁCIL: ").strip()
        try:
            op = int(s)
            if 1 <= op <= len(arquivos):
                nome = arquivos[op - 1]
                caminho = os.path.join(pasta, nome)
                print("\nARQUIVO ESCOLHIDO:", nome)
                return pasta, caminho, nome
        except:
            pass

        print("Opção inválida.")


# ============================================================
# PARSER DO HISTÓRICO
# ============================================================

def extrair_resultado(linha, sequencial):
    nums = [int(x) for x in re.findall(r"\d+", linha)]

    if len(nums) < 15:
        return None

    validos = []

    # Procura blocos de 15 números únicos entre 01 e 25.
    for i in range(0, len(nums) - 14):
        bloco = nums[i:i + 15]
        if (
            len(bloco) == 15
            and all(1 <= n <= 25 for n in bloco)
            and len(set(bloco)) == 15
        ):
            validos.append((i, tuple(sorted(bloco))))

    if not validos:
        return None

    # Em bases de loteria, as dezenas normalmente ficam mais ao final da linha.
    inicio, dezenas = validos[-1]

    concurso = sequencial

    anteriores = nums[:inicio]
    for n in reversed(anteriores):
        if n > 25:
            concurso = n
            break

    if concurso == sequencial and len(nums) >= 16 and nums[0] > 25:
        concurso = nums[0]

    return concurso, dezenas


def carregar(caminho):
    linhas = None

    for codificacao in ("utf-8-sig", "utf-8", "cp1252", "latin-1"):
        try:
            with open(caminho, "r", encoding=codificacao, errors="ignore") as arq:
                linhas = arq.readlines()
            break
        except:
            linhas = None

    if linhas is None:
        raise RuntimeError("Não foi possível ler o arquivo.")

    registros = []
    vistos = set()

    for linha in linhas:
        r = extrair_resultado(linha, len(registros) + 1)

        if r is None:
            continue

        concurso, dezenas = r

        chave = (concurso, dezenas)
        if chave in vistos:
            continue

        vistos.add(chave)

        sorteadas = set(dezenas)
        falhas = tuple(n for n in UNIVERSO if n not in sorteadas)

        if len(falhas) != 10:
            continue

        registros.append({
            "concurso": concurso,
            "dezenas": dezenas,
            "draw_mask": mask_nums(dezenas),
            "falhas": falhas,
            "fail_mask": mask_nums(falhas),
        })

    registros.sort(key=lambda x: x["concurso"])

    if len(registros) < MIN_HIST_BACKTEST + 5:
        raise RuntimeError(
            f"Foram encontrados apenas {len(registros)} concursos válidos. "
            f"Para este V2, use pelo menos {MIN_HIST_BACKTEST + 5} concursos."
        )

    return registros


# ============================================================
# MÉTRICAS INDIVIDUAIS
# ============================================================

def taxa_falha(registros, dezena, janela=None):
    sub = registros[-janela:] if janela else registros
    if not sub:
        return 0.0
    b = bit(dezena)
    return sum(1 for r in sub if r["fail_mask"] & b) / len(sub)


def sequencia_falha(registros, dezena):
    b = bit(dezena)
    s = 0
    for r in reversed(registros):
        if r["fail_mask"] & b:
            s += 1
        else:
            break
    return s


def estado3_prob(registros, dezena):
    b = bit(dezena)

    atual = tuple(
        1 if (r["fail_mask"] & b) else 0
        for r in registros[-3:]
    )

    inicio = max(2, len(registros) - STATE_LOOKBACK)
    total = 0
    sucesso = 0

    for i in range(inicio, len(registros) - 1):
        estado = (
            1 if registros[i - 2]["fail_mask"] & b else 0,
            1 if registros[i - 1]["fail_mask"] & b else 0,
            1 if registros[i]["fail_mask"] & b else 0,
        )

        if estado == atual:
            total += 1
            if registros[i + 1]["fail_mask"] & b:
                sucesso += 1

    base = taxa_falha(registros, dezena)

    p = laplace(sucesso, total, 2.0, 3.0) if total else base

    texto = "".join("F" if x else "S" for x in atual)

    return p, total, texto


def construir_influencia_lags(registros):
    """
    Cria P(Y falhar em t+gap | X falhou em t), gaps 1, 2 e 3.
    Usa apenas a parte recente limitada por INFLUENCE_LOOKBACK.
    """

    inicio = max(0, len(registros) - INFLUENCE_LOOKBACK - 3)

    totais = {
        1: [0] * 26,
        2: [0] * 26,
        3: [0] * 26,
    }

    xy = {
        1: [[0] * 26 for _ in range(26)],
        2: [[0] * 26 for _ in range(26)],
        3: [[0] * 26 for _ in range(26)],
    }

    for gap in (1, 2, 3):
        for i in range(inicio, len(registros) - gap):
            origem = registros[i]["falhas"]
            destino = registros[i + gap]["falhas"]

            for x in origem:
                totais[gap][x] += 1
                linha = xy[gap][x]
                for y in destino:
                    linha[y] += 1

    return totais, xy


def influencia_de_origem(origem, alvo, gap, totais, xy, baseline):
    vals = []

    for x in origem:
        total = totais[gap][x]
        if total <= 0:
            continue

        p = laplace(xy[gap][x][alvo], total, 1.0, 1.5)
        vals.append(p)

    if not vals:
        return baseline

    vals.sort(reverse=True)

    geral = mean(vals)
    top = mean(vals[:min(5, len(vals))])

    return 0.65 * geral + 0.35 * top


def construir_influencia_duplas(registros, pares_atuais):
    """
    Para cada dupla das falhas do último concurso:
    mede P(Y falhar no próximo | a dupla falhou junta).
    """

    inicio = max(0, len(registros) - PAIR_LOOKBACK - 1)

    pair_total = {par: 0 for par in pares_atuais}
    pair_next = {
        par: [0] * 26
        for par in pares_atuais
    }

    pair_masks = {
        par: bit(par[0]) | bit(par[1])
        for par in pares_atuais
    }

    for i in range(inicio, len(registros) - 1):
        fm = registros[i]["fail_mask"]
        prox = registros[i + 1]["falhas"]

        for par in pares_atuais:
            pm = pair_masks[par]

            if fm & pm == pm:
                pair_total[par] += 1
                linha = pair_next[par]
                for y in prox:
                    linha[y] += 1

    return pair_total, pair_next


def prob_duplas(alvo, pares_atuais, pair_total, pair_next, baseline):
    vals = []

    for par in pares_atuais:
        total = pair_total[par]

        if total < 3:
            continue

        p = laplace(
            pair_next[par][alvo],
            total,
            1.0,
            1.5
        )

        vals.append((p, par, total))

    if not vals:
        return baseline, []

    vals.sort(reverse=True)

    top = vals[:min(7, len(vals))]

    p_final = mean([x[0] for x in top])

    return p_final, top[:5]


# ============================================================
# PARETO
# ============================================================

def pareto_layers(feature_rows):
    """
    Recebe dict:
      dezena -> tuple de objetivos, todos para MAXIMIZAR.

    Retorna:
      dezena -> camada Pareto
      0 = primeira fronteira, 1 = segunda, etc.
    """

    restantes = set(feature_rows.keys())
    camada = {}
    nivel = 0

    while restantes:
        frente = []

        for a in list(restantes):
            va = feature_rows[a]
            dominado = False

            for b in restantes:
                if a == b:
                    continue

                vb = feature_rows[b]

                nao_pior = all(x >= y for x, y in zip(vb, va))
                melhor_em_algum = any(x > y for x, y in zip(vb, va))

                if nao_pior and melhor_em_algum:
                    dominado = True
                    break

            if not dominado:
                frente.append(a)

        if not frente:
            # segurança
            for a in restantes:
                camada[a] = nivel
            break

        for a in frente:
            camada[a] = nivel
            restantes.remove(a)

        nivel += 1

    return camada


# ============================================================
# CONSTRUIR TODAS AS FEATURES
# ============================================================

def construir_features(registros):
    ultimo = registros[-1]
    f1 = registros[-1]["falhas"]
    f2 = registros[-2]["falhas"]
    f3 = registros[-3]["falhas"]

    totais, xy = construir_influencia_lags(registros)

    pares_atuais = list(combinations(sorted(f1), 2))
    pair_total, pair_next = construir_influencia_duplas(
        registros,
        pares_atuais
    )

    dados = {}

    for d in UNIVERSO:
        hist = taxa_falha(registros, d)

        f10 = taxa_falha(registros, d, 10)
        f20 = taxa_falha(registros, d, 20)
        f50 = taxa_falha(registros, d, 50)
        f100 = taxa_falha(registros, d, 100)

        p_estado, casos_estado, estado_txt = estado3_prob(
            registros,
            d
        )

        p1 = influencia_de_origem(
            f1, d, 1, totais, xy, hist
        )

        p2 = influencia_de_origem(
            f2, d, 2, totais, xy, hist
        )

        p3 = influencia_de_origem(
            f3, d, 3, totais, xy, hist
        )

        p_influencia = (
            p1 * 0.56
            + p2 * 0.29
            + p3 * 0.15
        )

        p_duplas, top_duplas = prob_duplas(
            d,
            pares_atuais,
            pair_total,
            pair_next,
            hist
        )

        recente = (
            f20 * 0.50
            + f50 * 0.30
            + f100 * 0.20
        )

        streak = sequencia_falha(registros, d)

        tendencia = clamp(
            0.40
            + (f10 - f50) * 0.85
            + (f20 - f100) * 0.45
            + min(streak, 4) * 0.025
        )

        dados[d] = {
            "dezena": d,
            "estado": p_estado,
            "casos_estado": casos_estado,
            "estado_txt": estado_txt,
            "lag1": p1,
            "lag2": p2,
            "lag3": p3,
            "influencia": p_influencia,
            "duplas": p_duplas,
            "recente": recente,
            "tendencia": tendencia,
            "historica": hist,
            "f10": f10,
            "f20": f20,
            "f50": f50,
            "f100": f100,
            "streak": streak,
            "top_duplas": top_duplas,
        }

    objs = {
        d: (
            dados[d]["estado"],
            dados[d]["influencia"],
            dados[d]["duplas"],
            dados[d]["recente"],
            dados[d]["tendencia"],
        )
        for d in UNIVERSO
    }

    camadas = pareto_layers(objs)

    maior = max(camadas.values()) if camadas else 0

    for d in UNIVERSO:
        nivel = camadas[d]

        # Primeira fronteira = 1.0, últimas aproximam de 0.
        pareto_score = (
            1.0
            if maior == 0
            else 1.0 - (nivel / (maior + 1.0))
        )

        dados[d]["pareto_layer"] = nivel
        dados[d]["pareto"] = pareto_score

    return dados


# ============================================================
# SCORE POR PERFIL
# ============================================================

def score_perfil(dado, profile_name):
    w = PROFILES[profile_name]

    return (
        dado["estado"] * w["estado"]
        + dado["influencia"] * w["influencia"]
        + dado["duplas"] * w["duplas"]
        + dado["recente"] * w["recente"]
        + dado["tendencia"] * w["tendencia"]
        + dado["historica"] * w["historica"]
        + dado["pareto"] * w["pareto"]
    )


# ============================================================
# APRENDER REPETIÇÃO DAS FALHAS
# ============================================================

def aprender_repeticao_falhas(registros):
    """
    Mede quantas das 10 falhas de um concurso repetiram
    como falha no concurso seguinte.

    Dá mais peso aos casos recentes.
    """

    inicio = max(0, len(registros) - REPEAT_LOOKBACK - 1)

    pesos = defaultdict(float)
    contagem = Counter()
    serie = []

    trecho = list(range(inicio, len(registros) - 1))
    total = max(1, len(trecho))

    for pos, i in enumerate(trecho):
        a = registros[i]["fail_mask"]
        b = registros[i + 1]["fail_mask"]

        rep = (a & b).bit_count()

        # peso crescente para o histórico mais recente
        peso = 1.0 + 2.0 * ((pos + 1) / total)

        pesos[rep] += peso
        contagem[rep] += 1
        serie.append(rep)

    if not pesos:
        return 4, [4], contagem, serie

    ranking = sorted(
        pesos.items(),
        key=lambda x: (x[1], contagem[x[0]], -abs(x[0] - 4)),
        reverse=True
    )

    principal = ranking[0][0]

    melhores = [x[0] for x in ranking[:3]]

    return principal, melhores, contagem, serie


# ============================================================
# PADRÃO ESTRUTURAL DO JOGO DE 15
# ============================================================

def props_jogo15(nums, ultimo_draw=None):
    s = set(nums)

    pares = sum(n % 2 == 0 for n in s)
    primos = len(s & PRIMOS)
    fib = len(s & FIB)
    miolo = len(s & MIOLO)
    moldura = len(s & MOLDURA)
    soma = sum(s)

    linhas = [
        sum(1 for n in s if a <= n <= b)
        for a, b in (
            (1, 5),
            (6, 10),
            (11, 15),
            (16, 20),
            (21, 25),
        )
    ]

    colunas = [
        sum(1 for n in s if ((n - 1) % 5) == c)
        for c in range(5)
    ]

    seq_max = 0
    seq = 0
    anterior = None

    for n in sorted(s):
        if anterior is not None and n == anterior + 1:
            seq += 1
        else:
            seq = 1

        seq_max = max(seq_max, seq)
        anterior = n

    repetidas = (
        len(s & set(ultimo_draw))
        if ultimo_draw is not None
        else 0
    )

    return {
        "pares": pares,
        "primos": primos,
        "fib": fib,
        "miolo": miolo,
        "moldura": moldura,
        "soma": soma,
        "linhas": linhas,
        "colunas": colunas,
        "seq": seq_max,
        "rep": repetidas,
    }


def aprender_padrao_jogo(registros):
    sub = registros[-PATTERN_LOOKBACK:]

    chaves = [
        "pares",
        "primos",
        "fib",
        "miolo",
        "soma",
        "seq",
    ]

    vals = {k: [] for k in chaves}
    vals["rep"] = []

    for i, r in enumerate(sub):
        ultimo_draw = sub[i - 1]["dezenas"] if i > 0 else None
        p = props_jogo15(r["dezenas"], ultimo_draw)

        for k in chaves:
            vals[k].append(p[k])

        if i > 0:
            vals["rep"].append(p["rep"])

    modelo = {}

    for k, serie in vals.items():
        modelo[k] = {
            "mean": mean(serie),
            "std": max(stdev(serie), 0.75),
        }

    # Distribuição por linha/coluna.
    todas_linhas = []
    todas_colunas = []

    for r in sub:
        p = props_jogo15(r["dezenas"])
        todas_linhas.extend(p["linhas"])
        todas_colunas.extend(p["colunas"])

    modelo["linha_mean"] = mean(todas_linhas)
    modelo["linha_std"] = max(stdev(todas_linhas), 0.75)
    modelo["col_mean"] = mean(todas_colunas)
    modelo["col_std"] = max(stdev(todas_colunas), 0.75)

    return modelo


def score_padrao_jogo15(jogo15, ultimo_draw, modelo):
    p = props_jogo15(jogo15, ultimo_draw)

    z = 0.0

    for k in ("pares", "primos", "fib", "miolo", "soma", "seq", "rep"):
        m = modelo[k]["mean"]
        sd = modelo[k]["std"]
        z += abs(p[k] - m) / sd

    for x in p["linhas"]:
        z += 0.35 * abs(x - modelo["linha_mean"]) / modelo["linha_std"]

    for x in p["colunas"]:
        z += 0.35 * abs(x - modelo["col_mean"]) / modelo["col_std"]

    # 1.0 = muito aderente ao padrão histórico.
    score = 1.0 / (1.0 + z / 10.0)

    return score, p


# ============================================================
# ESCOLHA RÁPIDA DE 10 FALHAS
# ============================================================

def montar_grupo_base(
    features,
    ultimo_fail_mask,
    repetidas,
    profile_name
):
    dentro = []
    fora = []

    for d in UNIVERSO:
        sc = score_perfil(features[d], profile_name)

        item = (
            sc,
            -features[d]["pareto_layer"],
            features[d]["estado"],
            features[d]["influencia"],
            features[d]["duplas"],
            -d,
            d
        )

        if ultimo_fail_mask & bit(d):
            dentro.append(item)
        else:
            fora.append(item)

    dentro.sort(reverse=True)
    fora.sort(reverse=True)

    repetidas = max(0, min(10, repetidas))
    novas = 10 - repetidas

    if repetidas > len(dentro) or novas > len(fora):
        raise RuntimeError("Quantidade de repetidas incompatível.")

    escolhidas = [x[-1] for x in dentro[:repetidas]]
    escolhidas += [x[-1] for x in fora[:novas]]

    return tuple(sorted(escolhidas))


# ============================================================
# BACKTEST CEGO
# ============================================================

def criar_stats():
    return {
        "testes": 0,
        "soma_pontos": 0,
        "dist": Counter(),
        "falhas_certas": Counter(),
    }


def registrar(stats, pred_falhas, alvo):
    pm = mask_nums(pred_falhas)
    k = (pm & alvo["fail_mask"]).bit_count()
    pontos = pontos_do_jogo_por_falhas(k)

    stats["testes"] += 1
    stats["soma_pontos"] += pontos
    stats["dist"][pontos] += 1
    stats["falhas_certas"][k] += 1


def score_backtest(stats):
    n = max(1, stats["testes"])
    d = stats["dist"]
    media = stats["soma_pontos"] / n

    # Prioriza 15/14/13, mas também exige consistência.
    return (
        d[15] * 500.0
        + d[14] * 120.0
        + d[13] * 28.0
        + d[12] * 7.0
        + d[11] * 1.5
        + media * n * 0.20
    )


def backtest_cego(registros):
    inicio = max(
        MIN_HIST_BACKTEST,
        len(registros) - MAX_BACKTEST
    )

    indices = list(range(inicio, len(registros)))

    resultados = {
        nome: criar_stats()
        for nome in PROFILES
    }

    print()
    hr()
    print("BACKTEST CEGO WALK-FORWARD")
    print("Cada alvo usa SOMENTE concursos anteriores.")
    hr()

    total = len(indices)

    for pos, indice in enumerate(indices, 1):
        prefixo = registros[:indice]
        alvo = registros[indice]

        features = construir_features(prefixo)

        rep_principal, _, _, _ = aprender_repeticao_falhas(prefixo)

        ultimo_fail = prefixo[-1]["fail_mask"]

        for nome in PROFILES:
            pred = montar_grupo_base(
                features,
                ultimo_fail,
                rep_principal,
                nome
            )

            registrar(
                resultados[nome],
                pred,
                alvo
            )

        if (
            pos == 1
            or pos == total
            or pos % 10 == 0
        ):
            print(
                f"\rBacktest: {pos}/{total} "
                f"| {100.0 * pos / total:5.1f}%",
                end=""
            )

    print()

    ranking = []

    for nome, stats in resultados.items():
        stats["media"] = (
            stats["soma_pontos"] / stats["testes"]
            if stats["testes"]
            else 0.0
        )

        stats["score_bt"] = score_backtest(stats)

        ranking.append(
            (
                stats["score_bt"],
                stats["dist"][15],
                stats["dist"][14],
                stats["dist"][13],
                stats["dist"][12],
                stats["media"],
                nome,
            )
        )

    ranking.sort(reverse=True)

    return ranking, resultados


# ============================================================
# PERÍMETRO / TALO DA CANDIDATA
# ============================================================

def avaliar_perimetro_falhas(registros, falhas):
    pm = mask_nums(falhas)

    # Exclui o último concurso do perímetro estático para
    # não premiar artificialmente semelhança com o estado atual.
    base = registros[:-1]
    sub = base[-PERIMETER_LOOKBACK:]

    ks = []
    pts = []

    for r in sub:
        k = (pm & r["fail_mask"]).bit_count()
        ks.append(k)
        pts.append(5 + k)

    dist = Counter(pts)

    if not pts:
        return {
            "media": 0.0,
            "p12mais": 0,
            "p13mais": 0,
            "p14mais": 0,
            "p15": 0,
            "score": 0.0,
        }

    media_p = mean(pts)

    p12 = sum(1 for x in pts if x >= 12)
    p13 = sum(1 for x in pts if x >= 13)
    p14 = sum(1 for x in pts if x >= 14)
    p15 = sum(1 for x in pts if x >= 15)

    n = len(pts)

    score = clamp(
        (
            (media_p - 5.0) / 10.0 * 0.45
            + (p12 / n) * 0.25
            + (p13 / n) * 0.18
            + (p14 / n) * 0.09
            + (p15 / n) * 0.03
        )
    )

    return {
        "media": media_p,
        "p12mais": p12,
        "p13mais": p13,
        "p14mais": p14,
        "p15": p15,
        "score": score,
        "dist": dist,
    }


# ============================================================
# SINERGIA DAS 10 FALHAS
# ============================================================

def score_sinergia_falhas(registros, falhas):
    """
    Mede com que frequência as duplas internas da candidata
    falharam juntas recentemente.
    """

    sub = registros[-min(180, len(registros)):]
    if not sub:
        return 0.0

    pairs = list(combinations(sorted(falhas), 2))

    if not pairs:
        return 0.0

    vals = []

    masks = [r["fail_mask"] for r in sub]

    for a, b in pairs:
        pm = bit(a) | bit(b)
        c = sum(1 for fm in masks if fm & pm == pm)
        vals.append(c / len(masks))

    # Duas falhas aleatórias têm uma coocorrência-base.
    # Aqui só usamos para comparar candidatas.
    return clamp(mean(vals) / 0.25)


# ============================================================
# CANDIDATAS E REFINAMENTO
# ============================================================

def score_individual_grupo(features, falhas, profile_name):
    vals = [
        score_perfil(features[d], profile_name)
        for d in falhas
    ]
    return mean(vals)


def score_pareto_grupo(features, falhas):
    vals = [features[d]["pareto"] for d in falhas]
    return mean(vals)


def avaliar_candidata(
    registros,
    features,
    falhas,
    profile_name,
    modelo_padrao
):
    falhas = tuple(sorted(falhas))
    jogo15 = tuple(sorted(UNIVERSO_SET - set(falhas)))

    individual = score_individual_grupo(
        features,
        falhas,
        profile_name
    )

    pareto = score_pareto_grupo(
        features,
        falhas
    )

    per = avaliar_perimetro_falhas(
        registros,
        falhas
    )

    padrao, props = score_padrao_jogo15(
        jogo15,
        registros[-1]["dezenas"],
        modelo_padrao
    )

    sinergia = score_sinergia_falhas(
        registros,
        falhas
    )

    score = (
        individual * 0.42
        + per["score"] * 0.22
        + padrao * 0.16
        + pareto * 0.10
        + sinergia * 0.10
    )

    return {
        "falhas": falhas,
        "jogo15": jogo15,
        "score": score,
        "individual": individual,
        "pareto": pareto,
        "perimetro": per,
        "padrao": padrao,
        "props": props,
        "sinergia": sinergia,
    }


def gerar_vizinhos_preservando_repeticao(
    base,
    ultimo_fail_mask
):
    base_set = set(base)

    dentro_sel = sorted(
        d for d in base
        if ultimo_fail_mask & bit(d)
    )

    fora_sel = sorted(
        d for d in base
        if not (ultimo_fail_mask & bit(d))
    )

    dentro_disp = sorted(
        d for d in UNIVERSO
        if (ultimo_fail_mask & bit(d))
        and d not in base_set
    )

    fora_disp = sorted(
        d for d in UNIVERSO
        if not (ultimo_fail_mask & bit(d))
        and d not in base_set
    )

    vistos = {tuple(sorted(base))}
    yield tuple(sorted(base))

    # Trocas 1x1 preservando a quantidade de repetidas.
    for sai in dentro_sel:
        for entra in dentro_disp:
            novo = tuple(sorted((base_set - {sai}) | {entra}))
            if novo not in vistos:
                vistos.add(novo)
                yield novo

    for sai in fora_sel:
        for entra in fora_disp:
            novo = tuple(sorted((base_set - {sai}) | {entra}))
            if novo not in vistos:
                vistos.add(novo)
                yield novo


def montar_final(
    registros,
    features,
    champion_profile,
    melhores_repeticoes
):
    modelo_padrao = aprender_padrao_jogo(registros)
    ultimo_fail_mask = registros[-1]["fail_mask"]

    candidatos = {}

    # Usa até 3 quantidades de repetição mais fortes aprendidas.
    for rep in melhores_repeticoes[:3]:
        for profile_name in PROFILES:
            base = montar_grupo_base(
                features,
                ultimo_fail_mask,
                rep,
                profile_name
            )

            for cand in gerar_vizinhos_preservando_repeticao(
                base,
                ultimo_fail_mask
            ):
                candidatos[cand] = None

    print()
    print(
        f"Candidatas únicas para refinamento: "
        f"{len(candidatos):,}".replace(",", ".")
    )

    avaliadas = []

    total = len(candidatos)

    for i, cand in enumerate(candidatos, 1):
        ev = avaliar_candidata(
            registros,
            features,
            cand,
            champion_profile,
            modelo_padrao
        )

        avaliadas.append(ev)

        if i % 100 == 0 or i == total:
            print(
                f"\rRefinando: {i}/{total} "
                f"| {100.0 * i / total:5.1f}%",
                end=""
            )

    print()

    avaliadas.sort(
        key=lambda x: (
            x["score"],
            x["perimetro"]["p14mais"],
            x["perimetro"]["p13mais"],
            x["perimetro"]["p12mais"],
            x["padrao"],
            x["individual"],
        ),
        reverse=True
    )

    return avaliadas[0], avaliadas[:TOP_CANDIDATES_TO_REFINE]


# ============================================================
# RELATÓRIO
# ============================================================

def gerar_relatorio(
    nome_arquivo,
    registros,
    features,
    bt_ranking,
    bt_stats,
    champion_profile,
    rep_principal,
    rep_melhores,
    rep_contagem,
    melhor
):
    L = []
    A = L.append

    A("LOTOFÁCIL - MOTOR DE FALHAS V2")
    A("PADRÃO + PARETO + INFLUÊNCIA + BACKTEST CEGO")
    A("=" * 96)
    A(f"Arquivo: {nome_arquivo}")
    A(f"Concursos carregados: {len(registros)}")
    A(f"Primeiro: {registros[0]['concurso']}")
    A(f"Último:   {registros[-1]['concurso']}")
    A("")

    A("CONCEITO DE PONTUAÇÃO")
    A("-" * 96)
    for k in range(10, 1, -1):
        A(
            f"{k} falhas corretas => "
            f"{pontos_do_jogo_por_falhas(k)} pontos no jogo de 15"
        )
    A("")

    A("ÚLTIMOS 3 CONCURSOS")
    A("-" * 96)

    for r in registros[-3:]:
        A(
            f"Concurso {r['concurso']} | "
            f"SAÍRAM: {fmt(r['dezenas'])} | "
            f"FALHAS: {fmt(r['falhas'])}"
        )

    A("")
    A("REPETIÇÃO DAS FALHAS")
    A("-" * 96)
    A(f"Quantidade principal aprendida: {rep_principal}")
    A("Top quantidades: " + " / ".join(map(str, rep_melhores)))

    for k in sorted(rep_contagem):
        A(f"Repetiram {k}: {rep_contagem[k]} vezes")

    A("")
    A("BACKTEST CEGO - PERFIS")
    A("-" * 96)

    for item in bt_ranking:
        _, _, _, _, _, _, nome = item
        s = bt_stats[nome]
        d = s["dist"]

        A(
            f"{nome:11s} | "
            f"testes={s['testes']} | "
            f"média={s['media']:.3f} | "
            f"15={d[15]} | 14={d[14]} | "
            f"13={d[13]} | 12={d[12]} | "
            f"11={d[11]} | score={s['score_bt']:.2f}"
        )

    A("")
    A(f"PERFIL CAMPEÃO: {champion_profile}")
    A("")

    A("RANKING INDIVIDUAL ATUAL")
    A("-" * 96)

    ranking_ind = sorted(
        UNIVERSO,
        key=lambda d: (
            score_perfil(features[d], champion_profile),
            -features[d]["pareto_layer"],
            features[d]["estado"],
            features[d]["influencia"],
            features[d]["duplas"],
            -d,
        ),
        reverse=True
    )

    for i, d in enumerate(ranking_ind, 1):
        x = features[d]

        A(
            f"{i:02d}. DZ {d:02d} | "
            f"score={score_perfil(x, champion_profile)*100:6.2f} | "
            f"Pareto=L{x['pareto_layer']} | "
            f"EST3={x['estado_txt']} "
            f"P={x['estado']*100:5.1f}% "
            f"| INF={x['influencia']*100:5.1f}% "
            f"| DUP={x['duplas']*100:5.1f}% "
            f"| REC={x['recente']*100:5.1f}% "
            f"| TREND={x['tendencia']*100:5.1f}% "
            f"| seq={x['streak']}"
        )

    A("")
    A("PROJEÇÃO FINAL")
    A("=" * 96)
    A("10 FALHAS PROJETADAS:")
    A(fmt(melhor["falhas"]))
    A("")
    A("JOGO COMPLEMENTAR DE 15:")
    A(fmt(melhor["jogo15"]))
    A("")

    repetidas_falha = len(
        set(melhor["falhas"])
        & set(registros[-1]["falhas"])
    )

    A(f"Falhas repetidas do último: {repetidas_falha}")
    A(f"Score final: {melhor['score']:.6f}")
    A(f"Score individual: {melhor['individual']:.6f}")
    A(f"Pareto grupo: {melhor['pareto']:.6f}")
    A(f"Padrão jogo15: {melhor['padrao']:.6f}")
    A(f"Sinergia falhas: {melhor['sinergia']:.6f}")
    A("")

    per = melhor["perimetro"]

    A("PERÍMETRO / TALO DA CANDIDATA")
    A("-" * 96)
    A(f"Média histórica do jogo complementar: {per['media']:.3f}")
    A(f"12+ pontos: {per['p12mais']}")
    A(f"13+ pontos: {per['p13mais']}")
    A(f"14+ pontos: {per['p14mais']}")
    A(f"15 pontos: {per['p15']}")
    A("")

    p = melhor["props"]

    A("PADRÃO DO JOGO FINAL DE 15")
    A("-" * 96)
    A(f"Pares: {p['pares']}")
    A(f"Primos: {p['primos']}")
    A(f"Fibonacci: {p['fib']}")
    A(f"Miolo: {p['miolo']}")
    A(f"Moldura: {p['moldura']}")
    A(f"Soma: {p['soma']}")
    A(f"Maior sequência: {p['seq']}")
    A(f"Repetidas do último jogo: {p['rep']}")
    A("Linhas: " + str(p["linhas"]))
    A("Colunas: " + str(p["colunas"]))
    A("")

    A("OBSERVAÇÃO")
    A("-" * 96)
    A(
        "O backtest é walk-forward: cada concurso histórico é previsto "
        "somente com dados anteriores ao próprio concurso."
    )
    A(
        "Este é um estudo estatístico de falhas e padrões. "
        "Não existe garantia de premiação."
    )

    return "\n".join(L)


# ============================================================
# MAIN
# ============================================================

def main():
    hr()
    print("LOTOFÁCIL - MOTOR DE FALHAS V2")
    print("PADRÃO + PARETO + INFLUÊNCIA + BACKTEST CEGO")
    print("ANÁLISE ESPECIAL DOS 3 ÚLTIMOS CONCURSOS")
    hr()

    pasta, caminho, nome_arquivo = escolher_arquivo()

    print("\nCarregando histórico...")
    registros = carregar(caminho)

    print(
        f"Concursos carregados: {len(registros)} "
        f"| {registros[0]['concurso']} até {registros[-1]['concurso']}"
    )

    print()
    hr()
    print("ÚLTIMOS 3 CONCURSOS")
    hr()

    for r in registros[-3:]:
        print(
            f"Concurso {r['concurso']} "
            f"| Falhas: {fmt(r['falhas'])}"
        )

    # --------------------------------------------------------
    # BACKTEST
    # --------------------------------------------------------

    bt_ranking, bt_stats = backtest_cego(registros)

    print()
    hr()
    print("RESULTADO DO BACKTEST")
    hr()

    for pos, item in enumerate(bt_ranking, 1):
        _, _, _, _, _, _, nome = item
        s = bt_stats[nome]
        d = s["dist"]

        print(
            f"{pos}. {nome:11s} "
            f"| média {s['media']:.3f} "
            f"| 15={d[15]} "
            f"| 14={d[14]} "
            f"| 13={d[13]} "
            f"| 12={d[12]} "
            f"| 11={d[11]}"
        )

    champion_profile = bt_ranking[0][-1]

    print()
    print("PERFIL CAMPEÃO:", champion_profile)

    # --------------------------------------------------------
    # ESTADO ATUAL
    # --------------------------------------------------------

    print("\nCalculando features do estado atual...")

    features = construir_features(registros)

    (
        rep_principal,
        rep_melhores,
        rep_contagem,
        rep_serie
    ) = aprender_repeticao_falhas(registros)

    print(
        "Quantidade principal de falhas repetidas aprendida:",
        rep_principal
    )

    print(
        "Top quantidades de repetição:",
        rep_melhores
    )

    # --------------------------------------------------------
    # FINAL
    # --------------------------------------------------------

    melhor, top = montar_final(
        registros,
        features,
        champion_profile,
        rep_melhores
    )

    print()
    hr()
    print("PROJEÇÃO FINAL")
    hr()

    print("10 FALHAS PROJETADAS:")
    print(fmt(melhor["falhas"]))

    print()
    print("JOGO COMPLEMENTAR DE 15:")
    print(fmt(melhor["jogo15"]))

    repetidas_falha = len(
        set(melhor["falhas"])
        & set(registros[-1]["falhas"])
    )

    print()
    print(
        "Falhas repetidas do último concurso:",
        repetidas_falha
    )

    print(
        f"Score final: {melhor['score']:.6f}"
    )

    per = melhor["perimetro"]

    print(
        f"Perímetro: média={per['media']:.3f} "
        f"| 12+={per['p12mais']} "
        f"| 13+={per['p13mais']} "
        f"| 14+={per['p14mais']} "
        f"| 15={per['p15']}"
    )

    p = melhor["props"]

    print(
        "Padrão jogo15:",
        f"pares={p['pares']}",
        f"primos={p['primos']}",
        f"fib={p['fib']}",
        f"miolo={p['miolo']}",
        f"soma={p['soma']}",
        f"rep={p['rep']}"
    )

    # --------------------------------------------------------
    # RELATÓRIO
    # --------------------------------------------------------

    relatorio = gerar_relatorio(
        nome_arquivo,
        registros,
        features,
        bt_ranking,
        bt_stats,
        champion_profile,
        rep_principal,
        rep_melhores,
        rep_contagem,
        melhor
    )

    saida = os.path.join(
        pasta,
        ARQ_SAIDA
    )

    with open(
        saida,
        "w",
        encoding="utf-8"
    ) as arq:
        arq.write(relatorio)

    print()
    hr()
    print("RELATÓRIO SALVO EM:")
    print(saida)
    hr()
    print("CONCLUÍDO.")


if __name__ == "__main__":
    try:
        main()
    except Exception as erro:
        print()
        hr()
        print("ERRO:")
        print(str(erro))
        hr()
