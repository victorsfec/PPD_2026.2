# Protocolo TCP do Fanorona

Cada mensagem termina em LF; CR é ignorado. A codificação é UTF-8. Campos são separados por `|`; campos de texto são Base64 de UTF-8, sem quebras de linha. O servidor limita cada linha a 8192 caracteres. Índices do tabuleiro vão de 0 a 44, com `índice = linha * 9 + coluna`, ambos começando em zero.

## Cliente para servidor

| Mensagem | Significado |
|---|---|
| `HELLO|nome64` | Primeira mensagem, nome de 1 a 30 caracteres; timeout de 15 s |
| `MOVE|revisao|origem|destino|modo` | Movimento; modo `A` (aproximação), `W` (afastamento), `P` (simples) |
| `END_TURN|revisao` | Encerra uma sequência de capturas já iniciada |
| `CHAT|texto64` | Texto de 1 a 500 caracteres; `/final` prepara a demonstração durante a partida |
| `RESTART|revisao` | Solicita reinício à dupla conectada; exige aceite do adversário |
| `RESTART_REPLY|oferta|ACCEPT` | Adversário aceita a oferta vigente |
| `RESTART_REPLY|oferta|DECLINE` | Adversário recusa a oferta vigente |
| `FORFEIT` | Desistência, independentemente do turno |
| `DRAW` | Propõe empate ou aceita a oferta do outro jogador |

O ID do jogador é derivado da conexão; o cliente não pode escolher agir pelo adversário. `MOVE`, `END_TURN` e `RESTART` exigem a revisão atual. Textos em branco ou com controles são rejeitados. Um comando inválido não altera o tabuleiro e recebe `ERROR`. Handshake inválido, mensagem acima do limite e falha de transporte fecham a conexão. Chat na espera recebe erro; está disponível durante e depois da partida.

## Servidor para cliente

| Mensagem | Campos |
|---|---|
| `INFO|texto64` | Espera, oferta de empate ou aviso |
| `WELCOME|id|nomeP1_64|nomeP2_64` | Identifica os participantes; P1 inicia |
| `STATE|revisao|turno|sequencia|matriz|jogadas` | Estado autoritativo completo |
| `CHAT|id|texto64` | Chat entregue a ambos os jogadores |
| `RESTART_OFFER|oferta|solicitante` | Identifica a solicitação pendente e quem deve aguardar |
| `RESTART_CANCELLED|oferta|motivo64` | Recusa ou cancelamento por mudança de estado/desconexão |
| `RESTARTED|revisao` | Confirma o reinício; INFO e STATE vêm em seguida |
| `PEER_LEFT|id` | Informa desconexão e desabilita o reinício |
| `ERROR|texto64` | Rejeição entregue ao emissor |
| `OVER|vencedor|motivo64|m1|m2|c1|c2|i1|i2` | Vencedor 1/2 ou 0 empate; movimentos, capturas e comandos inválidos |

Na matriz, os 45 dígitos são `0` vazio, `1` branca e `2` preta, em ordem de linhas. `sequencia` é -1 fora de sequência ou o índice da peça que deve continuar. `jogadas` é uma lista separada por `;`, cada entrada `origem,destino,modo`; campo vazio significa ausência de movimentos. A revisão começa em zero e aumenta em cada movimento, encerramento explícito de sequência ou preparação por `/final`. `OVER` encerra as jogadas, mas mantém chat e RESTART disponíveis enquanto a dupla estiver conectada. Somente RESTART_REPLY com ACCEPT enviado pelo adversário da oferta vigente restaura o tabuleiro inicial, zera estatísticas, cancela empate e sequência, mantém nomes/IDs e incrementa a revisão; P1 começa. RESTARTED libera a interface para o STATE da nova partida.

## Exemplo e fluxo

`HELLO|QW5h` identifica o nome Ana. Um cliente pode receber `WELCOME|1|QW5h|QnJ1bm8=` (Ana e Bruno). A jogada efetiva deve ser escolhida na lista do `STATE`; não se deve assumir que um exemplo fixo continua válido em qualquer posição.

Fluxo: conexão → HELLO → INFO de espera → WELCOME → STATE → MOVE/CHAT/END_TURN → STATE ou ERROR → OVER. A fila de escrita de cada cliente preserva a ordem de publicação da sessão. TCP não fornece recuperação de sessão após reconexão. A ausência de TLS e autenticação limita o uso a demonstrações em rede confiável.

## Comando de demonstração

O texto `/final` (sem distinguir maiúsculas e após remover espaços nas extremidades) em CHAT é tratado no servidor. P1 ou P2 pode solicitá-lo durante uma partida ativa. O servidor coloca uma peça do solicitante no índice 20 (C3), uma adversária no 22 (E3), atribui a vez ao solicitante, zera estatísticas e cancela sequência e oferta de empate. Publica INFO explicativo e STATE com revisão incrementada; MOVE da revisão anterior é rejeitado. O movimento 20,21,A captura a última peça e gera OVER. Após OVER, `/final` recebe ERROR e o chat comum continua disponível.

Cada solicitação de reinício tem ID crescente, independente da revisão do tabuleiro. Repetir RESTART não equivale a aceitar. Autoaceite e respostas a ofertas antigas são rejeitados. Recusa preserva a partida; jogada, /final, término ou desconexão cancelam ofertas pendentes. Chat comum não cancela a oferta.
