# Fanorona com Java RMI

Projeto 2 de Programação Paralela e Distribuída, IFCE 2026.2. Versão independente do projeto `sockets`, preservando Swing, regras, chat, resultados, empate, reinício por consentimento e demonstração `/final`. Toda comunicação entre processos ocorre por Java RMI.

## Compilar

Requer JDK 25. Na pasta `rmi`, execute:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -JdkHome 'C:\Program Files\Java\jdk-25.0.4.1'
```

Também é possível usar `mvn clean package` com Maven e JDK 25. O build executa testes das regras e chamadas reais ao serviço obtido pelo registro RMI, e gera os executáveis em `target`.

## Executar

Abra o servidor e dois clientes em terminais separados, na pasta `rmi`:

```powershell
java -jar target/rmi-1.0-server.jar
java -jar target/rmi-1.0-client.jar
java -jar target/rmi-1.0-client.jar
```

No painel do servidor, clique em **Iniciar Servidor**. Nos clientes, informe nome, `localhost` e porta `12345`. O primeiro jogador recebe P1, usa as brancas e começa. O segundo recebe P2 e usa as pretas. Também é possível executar os métodos `main` de `FanoronaServerRMI` e `FanoronaClientRMI` no VS Code.

Servidor sem janela, com porta opcional:

```powershell
java -jar target/rmi-1.0-server.jar 12345 --console
```

O registro RMI é criado pelo servidor: não execute `rmiregistry` separadamente. Parar o servidor despublica o serviço e encerra o registro; os clientes detectam a falha na próxima consulta.

## Computadores diferentes

Consulte o IPv4 do servidor com `ipconfig`. Antes de iniciar o servidor, informe esse endereço na propriedade RMI (substitua o exemplo pelo IP real):

```powershell
java -Djava.rmi.server.hostname=192.168.1.100 -jar target/rmi-1.0-server.jar
```

Nos dois clientes, use o mesmo IP e a porta escolhida. Registro e objeto remoto compartilham a porta `12345` por padrão. Permita essa porta TCP no firewall do servidor. RMI usa TCP internamente; o projeto não implementa transporte próprio com `Socket` ou `ServerSocket`. Os clientes não exportam callbacks, portanto não precisam receber conexões de entrada.

## Jogar

Clique em uma peça destacada e em um destino permitido. Se houver captura, ela é obrigatória no início do turno. Aproximação captura a linha adversária à frente do destino; afastamento captura atrás da origem. Se ambos forem possíveis, escolha o modo na janela. A sequência usa a mesma peça, sem revisitar pontos ou repetir a direção anterior. **Encerrar sequência** permite parar as capturas adicionais.

Vence quem elimina todas as peças adversárias ou imobiliza o adversário. **Desistir** concede vitória ao oponente; **Empate** propõe ou aceita empate. O chat aceita até 500 caracteres e permanece disponível após o resultado. **Reiniciar partida** exige aceite do adversário, restaura o tabuleiro e zera estatísticas, mantendo nomes e chat. `/final` no chat prepara uma captura final C3 → D3 para demonstração.

## Arquitetura e interface de operações

`shared/FanoronaServiceRMI.java` estende `Remote`; seus métodos declaram `RemoteException`. `FanoronaServerRMI` exporta a implementação usando `UnicastRemoteObject` e publica o stub como `Fanorona`. O cliente obtém o stub por `LocateRegistry.getRegistry(...).lookup(...)`.

| Método remoto | Operação |
|---|---|
| `connect(name)` | Valida nome, retorna token e entra na fila de pareamento |
| `poll(token)` | Retorna e remove os eventos pendentes; mantém a sessão ativa |
| `move(token, revision, from, to, mode)` | Valida e executa movimento/captura |
| `endTurn(token, revision)` | Encerra sequência opcional |
| `chat(token, text)` | Envia texto à dupla |
| `forfeit(token)` | Desiste da partida |
| `draw(token)` | Propõe/aceita empate |
| `restart(token, revision)` | Solicita reinício |
| `replyRestart(token, offer, accept)` | Responde à solicitação vigente |
| `disconnect(token)` | Libera sessão e informa o adversário |

O token aleatório identifica o jogador no servidor; o cliente não escolhe seu número P1/P2. O servidor é a autoridade das regras. `GameSessionRMI` sincroniza as alterações por partida e rejeita revisões antigas. Duplas diferentes têm tabuleiros e caixas de eventos separados.

O cliente consulta eventos a cada 200 ms e serializa chamadas em um executor fora da thread Swing. Atualizações visuais usam `SwingUtilities.invokeLater`. Os eventos são uma lista serializada pelo RMI, com strings compatíveis com a interface reaproveitada; o adaptador local converte os comandos da interface em métodos remotos específicos. Não há leitura de linhas de rede nem envio de comandos por sockets.

Cada caixa comporta 256 eventos. Se ficar cheia, o servidor remove o cliente. Sem chamadas por 15 segundos, a sessão expira; a verificação roda a cada segundo. Fechar normalmente chama `disconnect`; encerramento abrupto é detectado por expiração. O polling evita conexões reversas e permite acompanhar o chat mesmo fora do turno.

## Diferenças entre RMI e sockets

As duas versões mantêm as regras do Fanorona, a interface Swing, o chat, as estatísticas, a demonstração `/final` e o reinício por consentimento. A principal mudança está na comunicação entre cliente e servidor.

| Aspecto | Implementação com sockets | Implementação com RMI |
|---|---|---|
| Transporte | A aplicação abre `ServerSocket` e `Socket` e lê/escreve nos streams TCP. | A infraestrutura Java RMI transporta chamadas e resultados; a aplicação usa uma referência remota (stub). RMI também usa TCP internamente. |
| Contrato | Comandos textuais, como `MOVE\|revisao\|origem\|destino\|modo`, definidos em `Protocol`. | Interface `FanoronaServiceRMI`, que estende `Remote` e declara métodos como `move(token, revision, from, to, mode)` com `RemoteException`. |
| Localização do serviço | O cliente conecta diretamente ao IP e à porta do servidor. | O servidor cria o registro e publica o serviço como `Fanorona`; o cliente faz `lookup` para obter o stub. |
| Identificação | O cliente envia `HELLO` com o nome; a identidade fica associada ao socket e ao `ClientHandler`. | `connect(name)` retorna um token UUID, usado nas chamadas seguintes para localizar o jogador e sua sessão. |
| Formato dos dados | Linhas UTF-8 terminadas em LF, com campos separados por `\|` e textos em Base64; limite de 8192 caracteres por linha. | Argumentos tipados e resultados serializados pelo RMI. Os eventos continuam como strings com `\|` e Base64 dentro de `List<String>`; não há leitura de linhas TCP na aplicação. |
| Recebimento de eventos | O servidor escreve os eventos no socket; uma thread do cliente permanece lendo as mensagens. | O cliente chama `poll(token)` para retirar os eventos pendentes, com atraso fixo de 200 ms após cada consulta. Não há callbacks remotos. |
| Concorrência no servidor | Cada `ClientHandler` possui uma thread de leitura e outra de escrita. | O runtime RMI despacha as chamadas; `ClientHandlerRMI` guarda a identidade e a caixa de eventos, sem threads próprias de transporte. |
| Concorrência no cliente | Um executor envia comandos e uma thread separada recebe eventos. | Um único executor agendado faz as ações e o polling; uma chamada demorada pode atrasar ambos. Nas duas versões, a interface é atualizada na EDT. |
| Filas de eventos | Fila de 256 mensagens por cliente, consumida pela thread de escrita; fila cheia fecha o socket. | Caixa de 256 eventos por cliente, esvaziada por `poll`; caixa cheia agenda a remoção do jogador. |
| Detecção de saída | Fechamento, fim do stream ou erro de comunicação. Há timeout de 15 s para `HELLO`, retirado após a identificação. | `disconnect(token)` na saída normal; ausência de chamadas por mais de 15 s leva à expiração, verificada a cada segundo. |
| Encerramento do servidor | Fecha o `ServerSocket` e os sockets dos jogadores. | Remove o nome do registro, desexporta o serviço e o registro e encerra a manutenção. |
| Uso entre máquinas | Exige IP acessível e porta TCP liberada no servidor. | Além do IP e da porta liberada, o stub deve anunciar um endereço acessível, configurável por `java.rmi.server.hostname`. Neste projeto, registro e serviço compartilham a porta. |
| Testes de comunicação | Exercitam comandos e eventos pela conexão TCP. | Exercitam o contrato remoto por lookup e chamadas reais ao stub, incluindo tokens inválidos, expiração e reabertura do registro. |

Na versão RMI, `FanoronaClientRMI.invoke` converte os comandos locais da interface em métodos remotos. O servidor converte essas chamadas em comandos internos para a sessão, preservando o formato dos eventos e a lógica do jogo. O tabuleiro continua sendo um objeto local no servidor, sem exportação remota.

Em ambas as versões, o servidor valida turno, revisão e regras e publica o estado autoritativo. No RMI, o retorno normal de `move` não confirma que a jogada foi aceita: a confirmação ou rejeição chega por `STATE` ou `ERROR` em uma consulta posterior. O formato completo está em [docs/PROTOCOLO.md](docs/PROTOCOLO.md); o protocolo da versão sockets está em [../sockets/docs/PROTOCOLO.md](../sockets/docs/PROTOCOLO.md).

## Validação e limites

`AppTestRMI` verifica regras de captura, sequências, turnos, conservação de peças, pareamento via stub RMI, sincronização de jogadas, revisão antiga, chat, isolamento de partidas, tokens inválidos, desistência, empate, desconexão normal e por expiração, demonstração, reinícios aceitos/recusados e reabertura do registro. Os testes usam a porta local `23456`, que deve estar livre. A simulação de encerramento abrupto aguarda cerca de 16 segundos.

Uma falha de rede encerra o cliente; para retornar, abra uma nova conexão. Não há persistência, reconexão automática ou criptografia/autenticação de usuários. O token identifica apenas a sessão em memória. A execução entre duas máquinas exige endereço anunciado e firewall corretos; os testes locais não substituem a verificação na rede da apresentação.
