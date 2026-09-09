# Fanorona com sockets TCP

Jogo desenvolvido para a disciplina de Programação Paralela e Distribuída do IFCE, no semestre 2026.2. Dois jogadores se conectam a um servidor e disputam uma partida de Fanorona, com tabuleiro gráfico, chat e acompanhamento do resultado.

O projeto usa Java 25, Swing para as janelas e sockets TCP para a comunicação. O servidor controla as regras e envia o estado da partida aos dois jogadores.

## O que você precisa

- JDK 25 ou superior para compilar e executar.
- VS Code com suporte a Java, caso queira executar diretamente pelo editor.
- Maven, se optar por compilar com ele. Também há um script PowerShell que usa apenas o JDK.

Para conferir o Java do terminal, execute `java -version` e `javac -version`. Ambos devem indicar a versão 25 ou superior. No VS Code, o comando **Java: Configure Java Runtime** permite conferir o JDK usado pelo projeto.

## Executar pelo VS Code

Abra a pasta **PPD - 2026.2** no VS Code e aguarde o carregamento do projeto Java.

1. Abra `sockets/src/main/java/br/com/victorsfec/fanorona/server/FanoronaServer.java` e clique em **Run** acima do método `main`.
2. Na janela do servidor, escolha a porta, por padrão `12345`, e clique em **Iniciar Servidor**.
3. Abra `sockets/src/main/java/br/com/victorsfec/fanorona/client/FanoronaClient.java` e execute o `main` duas vezes, uma para cada jogador.
4. Informe nomes diferentes nos clientes. Para jogar na mesma máquina, use `localhost` e a porta escolhida no servidor.

O primeiro jogador identificado recebe P1, joga com as peças brancas e começa. O segundo recebe P2 e joga com as pretas.

**Parar Servidor** encerra as conexões. Fechar a janela do servidor faz o mesmo. Depois, é possível iniciar o servidor novamente pelo painel.

## Compilar e executar pelo terminal

Os comandos abaixo devem ser executados na pasta `sockets`.

Com Maven:

```powershell
mvn clean package
```

No VS Code, também é possível executar **Lifecycle → package** na seção Maven. O primeiro uso pode baixar os plugins necessários.

Com PowerShell, usando o JDK indicado em `JAVA_HOME`:

```powershell
.\build.ps1
```

Se precisar informar o caminho do JDK:

```powershell
.\build.ps1 -JdkHome 'C:\Program Files\Java\jdk-25.0.4.1'
```

Ajuste esse caminho conforme a instalação da sua máquina. O script compila o código, executa os testes e gera os JARs se tudo passar.

Para abrir o servidor e os clientes pelos JARs, execute cada comando em um terminal separado:

```powershell
# Servidor: abre o painel de controle
java -jar target/sockets-1.0-server.jar

# Execute em outros dois terminais, um para cada jogador
java -jar target/sockets-1.0-client.jar
```

Para iniciar o servidor sem janela, use `java -jar target/sockets-1.0-server.jar --console`. Uma porta diferente pode ser informada, por exemplo: `java -jar target/sockets-1.0-server.jar 15000 --console`. Nesse modo, Ctrl+C encerra o servidor. Em ambientes sem suporte gráfico, o modo terminal é usado automaticamente.

## Jogar em computadores diferentes

Inicie o servidor em um computador da rede e consulte seu IPv4 com `ipconfig`. Nos clientes, informe esse endereço e a porta do servidor. Use `localhost` somente quando o servidor estiver na mesma máquina do cliente.

Se a conexão não funcionar, confira se os computadores conseguem se comunicar e se o firewall permite a porta TCP escolhida. Os clientes precisam alcançar o servidor; não precisam se conectar diretamente entre si.

## Como jogar

O tabuleiro tem 5 linhas e 9 colunas. Cada jogador começa com 22 peças, e o ponto central fica vazio.

Clique em uma peça sua para ver os destinos disponíveis em verde. Depois, clique no destino. A peça se move para um ponto vizinho vazio pelas linhas do tabuleiro; as diagonais só valem onde há uma linha desenhada.

Existem duas formas de capturar:

- **Aproximação:** ao mover, captura as peças adversárias em sequência à frente do destino.
- **Afastamento:** ao mover, captura as peças adversárias em sequência atrás da origem.

A captura para ao encontrar um espaço vazio, uma peça aliada ou a borda. Se os dois tipos forem possíveis, uma janela pede que você escolha um deles.

Quando existe alguma captura disponível, ela é obrigatória no primeiro movimento do turno. Caso contrário, é permitido um movimento simples, chamado *paika*.

Depois de capturar, você pode continuar com a mesma peça. Na sequência, não pode voltar a um ponto já visitado nem repetir a direção do movimento anterior. Para parar, use **Encerrar sequência**. Se não houver outra captura, a vez muda automaticamente.

Vence quem elimina todas as peças adversárias ou deixa o adversário sem movimentos. O botão **Desistir** concede a vitória ao outro jogador. **Empate** permite propor ou aceitar um empate; uma jogada cancela a proposta pendente.

O chat aceita até 500 caracteres por mensagem, incluindo acentos, e continua disponível após o resultado.

### Reiniciar com os mesmos jogadores

Clique em **Reiniciar partida** e confirme a solicitação. O adversário poderá escolher **Aceitar reinício** ou **Recusar reinício**. A partida só reinicia quando os dois concordam.

O reinício restaura o tabuleiro, zera as estatísticas e mantém os nomes, as conexões e o histórico do chat. P1 começa novamente. Isso funciona durante a partida e depois do resultado, desde que os dois continuem conectados.

Uma jogada, mudança de tabuleiro, término da partida ou desconexão cancela uma solicitação pendente. Se alguém desconectar, será necessário conectar uma nova dupla.

### Preparar uma jogada final para demonstração

Durante a partida, qualquer jogador pode digitar **`/final`** no chat, mesmo fora da sua vez. Esse comando substitui o tabuleiro por duas peças, zera as estatísticas e passa a vez a quem o enviou.

O comando afeta apenas a partida dessa dupla. Para repetir depois da vitória, solicite o reinício, aguarde o aceite do adversário e envie `/final` novamente. Uma mensagem como `/final explicado` é tratada como chat normal.

### Regras adotadas

A continuação das capturas é opcional, conforme o enunciado do trabalho. A derrota por imobilização e o empate por acordo também fazem parte desta implementação. Não há empate automático por repetição ou por limite de jogadas, nem a variante de revanche Vela.

## Onde encontrar cada parte do código

Os arquivos Java principais ficam em `sockets/src/main/java/br/com/victorsfec/fanorona/`.

| Pasta | Responsabilidade |
|---|---|
| `server` | Receber conexões, formar duplas e controlar as partidas |
| `client` | Exibir as janelas e enviar as ações dos jogadores |
| `game` | Representar o tabuleiro e validar movimentos e capturas |
| `shared` | Definir o formato das mensagens trocadas pela rede |

## Comunicação e concorrência

O servidor usa `ServerSocket` para aceitar conexões. Cada cliente se comunica por um `Socket` e tem uma thread de leitura. Uma fila e uma thread de saída cuidam do envio das respostas.

Cada dupla tem sua própria `GameSession`. O método que processa as ações é `synchronized`, evitando que duas threads alterem o mesmo tabuleiro ao mesmo tempo. Partidas diferentes podem avançar de forma independente.

As mensagens terminam com uma quebra de linha. Nomes e textos do chat são codificados em Base64 sobre UTF-8 para preservar acentos e separadores.

Depois de uma alteração, o servidor envia o estado do tabuleiro aos dois clientes. Cada estado tem um número de revisão, usado para rejeitar ações baseadas em um tabuleiro antigo. A interface só confirma o movimento ao receber a resposta do servidor.

No cliente, a comunicação acontece fora da thread gráfica. As atualizações das janelas passam por `SwingUtilities.invokeLater`, mantendo o Swing responsivo enquanto a rede trabalha.