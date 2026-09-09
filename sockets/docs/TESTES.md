# Registro de validação

## Painel do servidor e Maven — 09/09/2026

O painel Swing abre com o servidor parado, permite escolher a porta, iniciar, parar e reiniciar. O modo `--console` mantém a operação pelo terminal. Fechar a janela encerra os clientes e libera a porta.

Validações concluídas com JDK 25.0.4.1:

- Build PowerShell: 40 verificações de regras e TCP aprovadas e JARs regenerados.
- `ServerFrameTest`: operação dos botões reais na EDT, rejeição de porta inválida, erro de porta ocupada, início com resposta TCP ao cliente, parada com desconexão, reinício na mesma porta e fechamento da janela com cliente conectado. A porta pôde ser reutilizada após o fechamento.
- Capturas da própria interface nos estados parado e online renderizadas e inspecionadas em `target/server-ui`.
- Maven 3.6.3 localizado em `.m2/wrapper/dists`: `mvn -B -f sockets/pom.xml package` concluiu com **BUILD SUCCESS**, usando JDK 25.0.4.1 e gerando os JARs. O Maven emitiu avisos de APIs antigas de suas próprias dependências, sem falhar.
- O `JAVA_HOME` persistido no Windows foi confirmado como `C:\Program Files\Java\jdk-25.0.4.1`. A sessão do agente ainda herdava o valor anterior e foi ajustada somente nos processos de validação.

Para repetir o teste gráfico, após compilar, na pasta `sockets`:

```powershell
java -cp 'target/classes;target/test-classes' br.com.victorsfec.fanorona.server.ServerFrameTest
```

Esse teste exige ambiente gráfico e é executado separadamente da suíte de regras e TCP. Os testes foram automatizados; não houve partida manual entre duas máquinas. Os registros abaixo descrevem etapas anteriores.


## Migração para JDK 25 — 09/09/2026

Ambiente: Windows, Oracle JDK 25.0.4.1+1-LTS-5, instalado em `C:\Program Files\Java\jdk-25.0.4.1`.

Comando executado na raiz `PPD_2026.2`:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\sockets\build.ps1 -JdkHome 'C:\Program Files\Java\jdk-25.0.4.1'
```

Resultado: **40 verificações de regras e TCP aprovadas**, fontes e testes compilados com `--release 25`, JARs de servidor e cliente regenerados. O build concluiu fora do sandbox; no ambiente restrito, o compilador não localizou as classes principais durante a compilação dos testes. O parâmetro `ExecutionPolicy Bypass` vale somente para o processo de validação, sem alterar a política global.

Maven não está instalado nesta máquina; o `pom.xml` foi atualizado para release 25, mas o caminho Maven não foi executado. O `JAVA_HOME` global ainda aponta para JDK 21; use `-JdkHome` ou configure o ambiente do terminal conforme o README.

O registro abaixo, o PDF `Relatorio_Projeto1.pdf` e o arquivo ZIP original preservam a entrega anterior. O gerador do relatório foi atualizado para Java 25, mas o PDF não foi regenerado nesta migração (Python/ReportLab indisponíveis no PATH).

## Validação anterior — JDK 21

Data: 06/09/2026. Ambiente: Windows, Eclipse Temurin JDK 21.0.12.1+1 portátil.

## Execução

Comando executado na raiz `sockets`:

```powershell
.\build.ps1 -JdkHome 'C:\Users\victo\Documents\GIT\.tools\jdk21\jdk-21.0.12.1+1'
```

Resultado: **40 verificações aprovadas**, compilação concluída e dois JARs gerados. A suíte também percorre até 300 passos em cada uma de 12 partidas com sementes fixas, verificando conservação de peças. As partidas geradas são verificações de invariantes, não prova exaustiva das regras.

O compilador precisou ser executado fora do sandbox do agente devido ao erro `Fatal Error: Cannot close compiler resources` no ambiente restrito. Fora dele, o mesmo script concluiu normalmente. Isso não exigiu alteração de permissões do repositório nem configuração global do Git.

## Verificações realizadas

- 22 peças por jogador, centro vazio e P1 iniciando.
- Linhas ortogonais, diagonais permitidas e limites de borda.
- Captura inicial obrigatória, turno correto e rejeição sem mutação.
- Aproximação, afastamento, múltiplas peças e parada em vazio ou aliado.
- Escolha exclusiva entre captura A e W.
- Continuação com a mesma peça, sem voltar à origem nem repetir direção.
- Encerramento opcional de sequência e proibição de passar turno.
- Movimento simples, vitória por eliminação e vitória por imobilização.
- Rejeição de modo inexistente, peça adversária e jogo encerrado.
- UTF-8, separadores em textos e limites de chat.
- Identificação e pareamento enquanto outro socket permanece sem HELLO.
- Snapshots idênticos recebidos pelos dois clientes após início e jogada.
- Rejeição de turno incorreto, número inválido e revisão antiga pela rede.
- Chat entre os clientes durante a partida e depois do resultado.
- Desistência, desconexão por EOF e empate por acordo.
- Isolamento de chat entre duas sessões e continuidade da segunda sessão.
- Encerramento do servidor e liberação do laço de accept.

## Revisão visual e empacotamento

A interface foi renderizada a partir do próprio Swing pelo utilitário `UiRender`, incluindo o tabuleiro inicial, o chat, os rótulos e os botões. O relatório foi renderizado e suas seis páginas foram inspecionadas para verificar legibilidade, tabelas, margens e paginação. Os manifestos dos JARs foram conferidos: apontam para FanoronaServer e FanoronaClient.

## Limites da validação

O teste TCP usa conexões reais em loopback. Não foi realizada uma partida manual completa nem um teste entre duas máquinas físicas. Não foram realizados ensaios de carga com 100 clientes, perdas de rede prolongadas, saturação de filas ou auditoria de segurança. O caminho Maven foi fornecido, mas a compilação validada foi pelo script PowerShell com JDK. Antes da apresentação, ensaie a interface e a conectividade na rede que será utilizada.
