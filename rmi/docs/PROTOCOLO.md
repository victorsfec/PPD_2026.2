# Protocolo RMI do Fanorona

O cliente obtém o serviço `Fanorona` no registro RMI, na porta `12345` por padrão. As ações são chamadas a métodos de `FanoronaServiceRMI`; os eventos são strings retornadas por `poll(token)` em uma `List<String>`. Campos dos eventos são separados por `|`; campos de texto são Base64 de UTF-8, sem quebras de linha. Índices do tabuleiro vão de 0 a 44, com `índice = linha * 9 + coluna`, ambos começando em zero.

## Cliente para servidor

| Método remoto | Significado |
|---|---|
| `connect(nome)` | Primeira chamada, nome de 1 a 30 caracteres; retorna token UUID e entra na fila de pareamento |
| `poll(token)` | Retorna e remove os eventos pendentes da caixa do jogador |
| `move(token, revisao, origem, destino, modo)` | Movimento; modo `A` (aproximação), `W` (afastamento), `P` (simples) |
| `endTurn(token, revisao)` | Encerra uma sequência de capturas já iniciada |
| `chat(token, texto)` | Texto de 1 a 500 caracteres; `/final` prepara a demonstração durante a partida |
| `restart(token, revisao)` | Solicita reinício à dupla conectada; exige aceite do adversário |
| `replyRestart(token, oferta, true)` | Adversário aceita a oferta vigente |
| `replyRestart(token, oferta, false)` | Adversário recusa a oferta vigente |
| `forfeit(token)` | Desistência, independentemente do turno |
| `draw(token)` | Propõe empate ou aceita a oferta do outro jogador |
| `disconnect(token)` | Remove a identidade e informa a saída ao adversário |

O ID do jogador é derivado do token; o cliente não pode escolher agir pelo adversário. `move`, `endTurn` e `restart` exigem a revisão atual. Nome e chat são enviados como texto comum nos argumentos remotos; a aplicação faz a codificação Base64 dos eventos. Textos em branco ou com controles são rejeitados. Uma jogada inválida não altera o tabuleiro e recebe `ERROR` por uma consulta posterior. Token inválido ou expirado produz `RemoteException`; falhas de transporte também podem produzir essa exceção. O retorno normal de uma ação não confirma sua aceitação: o cliente acompanha `STATE` ou `ERROR`. Chat na espera recebe erro; está disponível durante e depois da partida.

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

Na matriz, os 45 dígitos são `0` vazio, `1` branca e `2` preta, em ordem de linhas. `sequencia` é -1 fora de sequência ou o índice da peça que deve continuar. `jogadas` é uma lista separada por `;`, cada entrada `origem,destino,modo`; campo vazio significa ausência de movimentos. A revisão começa em zero e aumenta em cada movimento, encerramento explícito de sequência ou preparação por `/final`. `OVER` encerra as jogadas, mas mantém chat e RESTART disponíveis enquanto a dupla estiver conectada. Somente `replyRestart(token, oferta, true)` enviado pelo adversário da oferta vigente restaura o tabuleiro inicial, zera estatísticas, cancela empate e sequência, mantém nomes/IDs e incrementa a revisão; P1 começa. RESTARTED libera a interface para o STATE da nova partida.

## Exemplo e fluxo

`connect("Ana")` retorna o token de Ana. Uma consulta pode retornar `WELCOME|1|QW5h|QnJ1bm8=` (Ana e Bruno). A jogada efetiva deve ser escolhida na lista do `STATE`; não se deve assumir que um exemplo fixo continua válido em qualquer posição.

Fluxo: lookup de Fanorona → connect → poll com INFO de espera → WELCOME → STATE → move/chat/endTurn → poll com STATE ou ERROR → OVER. O cliente consulta eventos com atraso fixo de 200 ms após cada consulta, fora da thread Swing. Cada caixa preserva a ordem dos eventos e comporta 256 entradas; caixa cheia provoca remoção do cliente. A manutenção verifica a cada segundo jogadores sem chamadas por mais de 15 segundos e agenda sua desconexão.

`poll` remove os eventos antes de retornar; uma resposta perdida não tem retransmissão na aplicação. Não há recuperação de sessão após reconexão. A ausência de TLS e autenticação de usuários limita o uso a demonstrações em rede confiável; Base64 não é criptografia.

## Comando de demonstração

O texto `/final` (sem distinguir maiúsculas e após remover espaços nas extremidades) em `chat(token, texto)` é tratado no servidor. P1 ou P2 pode solicitá-lo durante uma partida ativa. O servidor coloca uma peça do solicitante no índice 20 (C3), uma adversária no 22 (E3), atribui a vez ao solicitante, zera estatísticas e cancela sequência e oferta de empate. Publica INFO explicativo e STATE com revisão incrementada; MOVE da revisão anterior é rejeitado. O movimento 20,21,A captura a última peça e gera OVER. Após OVER, `/final` recebe ERROR e o chat comum continua disponível.

Cada solicitação de reinício tem ID crescente, independente da revisão do tabuleiro. Repetir `restart` não equivale a aceitar. Autoaceite e respostas a ofertas antigas são rejeitados. Recusa preserva a partida; jogada, /final, término ou desconexão cancelam ofertas pendentes. Chat comum não cancela a oferta.
