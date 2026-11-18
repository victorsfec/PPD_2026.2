# Contrato de mensagens MOM

O transporte é **Jakarta JMS sobre ActiveMQ Classic/OpenWire por TCP**. Não há protocolo próprio implementado com `Socket`, `ServerSocket` ou RMI. `ProtocolMOM` centraliza nomes, propriedades, validação de UUID e palavras-chave.

## Destinos e isolamento

| Destino | Tipo | Uso |
|---|---|---|
| `mom.linhas.<UUID>` | Queue | Linhas de uma execução, distribuídas entre consumidores concorrentes |
| `mom.palavras` | Topic | Resultados e términos dos leitores, replicados para cada assinatura ativa |

Todos os eventos incluem `jobId` (String UUID canônico) e `kind` (String). O subscriber utiliza o seletor JMS `jobId = '<UUID>'`. A validação do UUID impede inserir expressões arbitrárias no seletor. Cada execução usa sua própria fila e um UUID novo.

## LINE: leitor → fila

`TextMessage` com o texto da linha no corpo, sem o terminador. Propriedades:

| Propriedade | Tipo JMS | Significado |
|---|---|---|
| `jobId` | String | Identificador da execução |
| `kind` | String | `LINE` |
| `line` | long | Número da linha, iniciando em 1 |
| `keywordCount` | int | Quantidade de palavras-chave, de 1 a 100 |
| `word0` ... `wordN` | String | Palavras normalizadas, em ordem estável |

Os dois leitores abrem o mesmo arquivo e sessões diferentes. Paridade 0 publica linhas 2, 4, 6...; paridade 1 publica 1, 3, 5... O leitor confirma lotes de 100 linhas para possibilitar consumo durante a leitura. A palavra-chave é enviada com cada linha, permitindo adicionar workers sem uma etapa de configuração remota.

## RESULT: worker → tópico

`Message` sem corpo. Propriedades `jobId`, `kind = RESULT`, `line`, `keywordCount`, `word0...`, além de:

| Propriedade | Tipo JMS | Significado |
|---|---|---|
| `worker` | String | Nome do worker que processou a linha |
| `count0` ... `countN` | long | Ocorrências da palavra de índice correspondente, inclusive zero |

Cada worker tem sua própria conexão e sessão transacional. Na **mesma sessão**, consome uma linha da fila, produz um resultado no tópico e chama `commit()`. Se a operação falhar, chama `rollback()` e sinaliza o erro. Não é usado um canal direto de retorno ao dashboard.

O prefetch da fila é 1 para distribuir trabalho entre consumidores. A entrega global dos resultados pode ser fora de ordem: não existe dependência de ordenação entre workers diferentes. O dashboard deduplica pelo número da linha e soma os totais, evitando dupla contagem de um resultado repetido.

## READER_DONE: leitor → tópico

`Message` sem corpo:

| Propriedade | Tipo JMS | Significado |
|---|---|---|
| `jobId` | String | Identificador da execução |
| `kind` | String | `READER_DONE` |
| `parity` | int | 0 para linhas pares; 1 para ímpares |
| `lines` | long | Quantidade de linhas publicadas por aquele leitor |

O evento é publicado e confirmado junto com o último lote daquele leitor, após atingir o EOF. Arquivo vazio gera os dois eventos com `lines = 0`.

## Conclusão e falha

```text
recebeu READER_DONE de ambas as paridades
E
resultados distintos recebidos = linhas do leitor par + linhas do leitor ímpar
```

Somente nessa condição o dashboard marca a execução como concluída. Eventos de leitores e workers podem intercalar-se livremente.

A assinatura do cliente é criada antes dos produtores. É não durável e usa `AUTO_ACKNOWLEDGE`; o callback valida e agrega o resultado de forma sincronizada. Um segundo dashboard deve assinar antes do início para obter o histórico completo daquela execução. Um dashboard que entrar no meio receberá apenas as mensagens futuras e não terá totais completos.

Erros locais são informados ao modelo e ao cliente. `ExceptionListener` detecta falhas nas conexões JMS; um período de 120 segundos sem eventos também interrompe a espera. Cancelamento fecha as conexões e interrompe as tarefas, devolvendo mensagens não confirmadas ao broker enquanto ele permanece ativo. Não existe recuperação automática de uma execução após falha.

As mensagens usam `NON_PERSISTENT`. O broker embarcado usa armazenamento em memória, com possível uso temporário de disco para controle de fluxo. A implementação evita serialização Java de objetos de domínio e utiliza exclusivamente `TextMessage`, `Message` e propriedades JMS tipadas.
