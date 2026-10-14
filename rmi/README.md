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

### Aviso de arquivo fora do projeto no VS Code

Se aparecer `is a non-project file, only syntax errors are reported`, a extensão Java ainda não associou o arquivo ao projeto Maven. Com a pasta `PPD - 2026.2` aberta, pressione `Ctrl+Shift+P` e execute **Java: Import Java projects in workspace**. Aguarde o projeto `rmi` aparecer na seção **JAVA PROJECTS** antes de executar `FanoronaClientRMI.java` ou `FanoronaServerRMI.java`.

Se o aviso persistir, execute **Java: Clean Java Language Server Workspace** e confirme a reinicialização quando solicitada pelo VS Code. Essa operação reconstrói o índice Java. Como alternativa, abra diretamente a pasta `rmi` por **File → Open Folder**, contendo o `pom.xml`, e aguarde a importação.

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

## Validação e limites

`AppTestRMI` verifica regras de captura, sequências, turnos, conservação de peças, pareamento via stub RMI, sincronização de jogadas, revisão antiga, chat, isolamento de partidas, tokens inválidos, desistência, empate, desconexão normal e por expiração, demonstração, reinícios aceitos/recusados e reabertura do registro. Os testes usam a porta local `23456`, que deve estar livre. A simulação de encerramento abrupto aguarda cerca de 16 segundos.

Uma falha de rede encerra o cliente; para retornar, abra uma nova conexão. Não há persistência, reconexão automática ou criptografia/autenticação de usuários. O token identifica apenas a sessão em memória. A execução entre duas máquinas exige endereço anunciado e firewall corretos; os testes locais não substituem a verificação na rede da apresentação.
