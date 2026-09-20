# Lotofácil Falhas V1 - Android / GitHub

Aplicativo Android baseado no motor Python **Lotofácil Falhas V2 - Padrão + Pareto + Influência + Backtest Cego**.

## Conceito

- Universo 01-25.
- 15 dezenas saem.
- 10 dezenas ficam no pote = FALHAS.
- O motor projeta 10 falhas.
- O complemento das 10 falhas é o jogo final de 15 dezenas.
- Conversão correta: 10 falhas certas = 15 pontos; 9 = 14; 8 = 13; 7 = 12; 6 = 11; etc.

## Motor preservado

A versão Android traduz para Java a mesma lógica do V2:

- análise especial dos 3 últimos concursos;
- padrão F/S dos últimos 3;
- influência lag 1, 2 e 3;
- duplas das falhas do último;
- frequência 10/20/50/100;
- tendência e sequência de falha;
- camadas de Pareto;
- aprendizado da quantidade de falhas repetidas;
- backtest cego walk-forward;
- perfis PARETO, PADRAO, INFLUENCIA, RECENTE e CONSENSO;
- padrão estrutural do jogo complementar de 15;
- perímetro/talo histórico;
- refinamento de candidatas 1x1 preservando a quantidade de repetidas.

O arquivo `REFERENCIA_PYTHON_V2.py` fica no projeto como referência da versão Python.

## Tela

1. Selecionar o TXT/CSV de resultados.
2. Conferir os 3 últimos concursos e suas falhas.
3. Tocar em **ANALISAR FALHAS E GERAR JOGO DE 15**.
4. Acompanhar barra de progresso do backtest e refinamento.
5. Ver as 10 falhas projetadas e o jogo final de 15.

## PDF automático na pasta Download

Ao terminar a análise, o aplicativo cria automaticamente:

`LOTOFACIL_FALHAS_CONCURSO_<numero>.pdf`

na pasta **Download** do Android.

No volante:

- VERDE = as 15 dezenas do jogo final;
- VERMELHO = as 10 falhas projetadas.

O PDF também mostra perfil campeão, repetição de falhas e perímetro do jogo.

## Compilar no GitHub

O workflow está em:

`.github/workflows/build-apk.yml`

Artefato gerado:

`LOTOFACIL-FALHAS-V1-APK`

## Validação feita antes de empacotar

`MotorCore.java` foi compilado em Java 17 e executado com histórico sintético de 260 concursos. O teste confirmou:

- exatamente 10 falhas;
- exatamente 15 dezenas no jogo final;
- união falhas + jogo = todas as 25 dezenas sem repetição.

A compilação Android completa é feita pelo GitHub Actions, porque o ambiente local desta montagem não possui o Android SDK completo.
