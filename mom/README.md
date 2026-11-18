# Contagem paralela de palavras com MOM

Projeto 3 de Programação Paralela e Distribuída, IFCE 2026.2. Implementação em **Java 25, Swing e Jakarta JMS com Apache ActiveMQ Classic**.

O cliente solicita o arquivo e as palavras-chave, inicia dois leitores concorrentes (linhas pares e ímpares) e três workers por padrão. Os leitores enviam linhas a uma fila real do ActiveMQ; os workers competem por essas mensagens e publicam contagens em um tópico. O dashboard assina o tópico e soma os resultados conforme chegam.

## Executar a entrega pronta

Requer **JDK 25**. Na pasta `mom`, execute:

```powershell
java -jar target/mom-1.0-client.jar
```

Se `java -version` apontar uma versão antiga, use o executável do JDK 25 diretamente:

```powershell
& 'C:\Program Files\Java\jdk-25\bin\java.exe' -jar target/mom-1.0-client.jar
```

1. Selecione `examples/entrada.txt` ou outro arquivo de texto.
2. Informe `java, paralelo, distribuída` como palavras-chave.
3. Mantenha UTF-8, três workers e **Iniciar broker local**.
4. Clique em **Iniciar contagem**. Uma segunda janela abre o dashboard.

O dashboard mostra os totais, o progresso e quantas linhas cada worker processou. Em arquivos pequenos, as atualizações podem terminar antes do primeiro intervalo visual de 150 ms.

**Manter as pastas `lib/` e `target/` juntas.** Os quatro JARs usam as bibliotecas em `../lib`; não basta copiar somente um JAR. A entrega compactada inclui os executáveis, as bibliotecas e todos os fontes, permitindo executar sem Maven, Docker ou download de dependências.

## Compilar e testar

Na pasta `mom`, com JDK 25:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -JdkHome 'C:\Program Files\Java\jdk-25'
```

O script compila os fontes e testes, executa `AppTestMOM` e gera:

```text
target/mom-1.0-client.jar
target/mom-1.0-broker.jar
target/mom-1.0-worker.jar
target/mom-1.0-dashboard.jar
```

O script baixa do Maven Central apenas as bibliotecas ausentes em `lib/`. Também é possível usar Maven 3.9 com JDK 25:

```powershell
mvn clean package
```

O Maven executa as mesmas verificações pelo `exec-maven-plugin`, copia as dependências para `lib/` e produz os quatro executáveis. Os testes são programas Java com asserções explícitas, no padrão dos módulos anteriores; o resumo relevante é a mensagem `OK: ... verificações`, emitida por `AppTestMOM`.

## Broker e workers em processos separados

Para demonstrar que o transporte é MOM, é possível executar o broker e cada worker em uma JVM diferente. Abra terminais na pasta `mom`.

Inicie o broker:

```powershell
java -jar target/mom-1.0-broker.jar
```

Abra o cliente, desmarque **Iniciar broker local** e mantenha `tcp://localhost:61616`. Com três workers locais, somente o broker ficará em outro processo. Ao concluir a contagem com um broker externo, o dashboard mantém a assinatura ativa até que sua janela seja fechada.

Para usar exclusivamente workers externos, configure **Workers locais = 0**. Copie o UUID exibido no cliente antes de iniciar a contagem; substitua o exemplo abaixo pelo UUID real. Inicie um worker em cada terminal:

```powershell
java -jar target/mom-1.0-worker.jar tcp://localhost:61616 11111111-1111-4111-8111-111111111111 worker-externo-1
java -jar target/mom-1.0-worker.jar tcp://localhost:61616 11111111-1111-4111-8111-111111111111 worker-externo-2
java -jar target/mom-1.0-worker.jar tcp://localhost:61616 11111111-1111-4111-8111-111111111111 worker-externo-3
```

Depois clique em **Iniciar contagem**. Cada worker recebe as palavras-chave nas próprias mensagens, portanto não precisa de acesso ao arquivo. Use nomes diferentes para distinguir os workers no dashboard. Workers externos permanecem disponíveis para aquele UUID; encerre-os com Ctrl+C após a demonstração. Cada nova execução deve ter um UUID novo.

Um segundo dashboard pode acompanhar a mesma execução. Inicie-o **antes de clicar em Iniciar contagem**, com o UUID e as mesmas palavras, na mesma ordem, do cliente:

```powershell
java -jar target/mom-1.0-dashboard.jar tcp://localhost:61616 11111111-1111-4111-8111-111111111111 'java,paralelo,distribuída'
```

Assim, dois subscribers recebem todos os resultados, enquanto os workers repartem o consumo da fila. O dashboard adicional não substitui a assinatura criada pelo cliente.

Para usar máquinas diferentes, inicie o broker com um endereço de escuta acessível:

```powershell
java -jar target/mom-1.0-broker.jar tcp://0.0.0.0:61616
```

No cliente e nos workers, use `tcp://IP_DO_BROKER:61616` e libere a porta TCP 61616 na rede da apresentação. O arquivo precisa existir somente na máquina do cliente. A configuração padrão escuta apenas na máquina local. O projeto é para demonstração em rede confiável e não configura autenticação ou TLS.

## Execução pelo console

Com broker local iniciado automaticamente:

```powershell
java -jar target/mom-1.0-client.jar --console --local --file examples/entrada.txt --words 'java,paralelo,distribuída' --workers 3
```

Com broker já aberto:

```powershell
java -jar target/mom-1.0-client.jar --console --file examples/entrada.txt --words 'java,paralelo,distribuída' --broker tcp://localhost:61616
```

Opções adicionais: `--charset windows-1252`, `--job UUID` e `--workers 0` para processos externos. `--help` mostra a sintaxe. O modo console segue o mesmo workflow JMS e imprime os totais finais.

## Organização

```text
mom/
  pom.xml
  build.ps1
  examples/entrada.txt
  docs/PROTOCOLO.md
  docs/EXPLICACAO.md
  lib/                         # ActiveMQ e logging
  src/main/java/br/com/victorsfec/mom/
    broker/MomBroker.java
    client/MomClient.java
    client/ClientFrameMOM.java
    client/WorkflowMOM.java
    reader/LineReaderMOM.java
    worker/WordWorkerMOM.java
    worker/WordCounterMOM.java
    dashboard/DashboardFrameMOM.java
    dashboard/ResultModelMOM.java
    dashboard/TopicSubscriberMOM.java
    shared/ProtocolMOM.java
  src/main/resources/log4j2.properties
  src/test/java/br/com/victorsfec/mom/AppTestMOM.java
  src/test/java/br/com/victorsfec/mom/UiRenderMOM.java
  target/                      # Classes e executáveis gerados
```

## Regras e limites

- As linhas são numeradas a partir de 1. Linhas vazias também são mensagens e contam no progresso. Um terminador de linha ao final do arquivo não cria uma linha extra.
- Cada leitor percorre o arquivo inteiro em streaming e envia apenas sua paridade. Isso realiza duas leituras do arquivo; o conteúdo completo não é carregado em memória. O arquivo deve permanecer inalterado durante a execução.
- A contagem considera palavras inteiras formadas por letras Unicode, marcas de acento, números e `_`. Ignora maiúsculas/minúsculas, normaliza Unicode NFC e preserva acentos: `ação` corresponde a `AÇÃO`, mas difere de `acao`. `java` não corresponde a `JavaScript` nem a `java2`. Frases não são aceitas como palavras-chave.
- Palavras repetidas são removidas após normalização. É possível pesquisar até 100 palavras. Os totais usam `long`.
- A leitura guarda a linha atual e até 100 mensagens por lote. O agregador guarda os totais e um bitmap de linhas recebidas, aproximadamente um bit por número de linha, além do custo dos blocos. Portanto, a memória depende do maior tamanho de linha e do número de linhas, sem guardar todo o texto.
- O broker possui limite de memória de 64 MiB e armazenamento temporário de 512 MiB. Há controle de fluxo e timeout de envio de 10 s; linhas ou lotes excepcionalmente grandes podem exigir ajuste desses limites e do lote.
- A conclusão exige os dois eventos de término de leitura e um resultado para cada linha publicada. Os leitores terminarem não implica que os workers já terminaram.
- A interface permanece responsiva: leitura, broker e JMS executam fora da EDT. Fechar o dashboard não cancela o trabalho; use **Cancelar** no cliente. Fechar o cliente encerra o trabalho ativo.
- Erros de leitura, codificação ou conexão interrompem a execução e preservam os totais parciais identificados como interrompidos. Após 120 segundos sem eventos, o cliente reporta timeout; isso também detecta ausência de workers. Não há reconexão automática.
- O broker usa mensagens não persistentes e o tópico usa assinaturas não duráveis. É obrigatório assinar antes da publicação. Reiniciar o broker ou o dashboard não recupera uma execução. As transações protegem consumo/publicação enquanto o broker está ativo, sem prometer recuperação após queda do broker.
- Ao usar um broker externo, filas de execuções canceladas podem permanecer nele. Use UUIDs novos e um broker dedicado à demonstração; reiniciá-lo limpa seu estado em memória. Workers externos devem ser encerrados após a execução.

## Validação

`AppTestMOM` verifica palavras inteiras e Unicode, entrada inválida, arquivo vazio, uma linha, quantidades pares/ímpares, linhas vazias, 3.001 linhas em múltiplos lotes, paralelismo, resultados fora de ordem, deduplicação, isolamento de execuções simultâneas, três JVMs de workers externos, dois subscribers, rollback, codificação inválida, cancelamento e queda do broker. O broker de teste abre uma porta local livre automaticamente.

`UiRenderMOM` renderiza cliente e dashboard em PNG por Java2D para inspeção de layout, sem capturar o desktop. Após compilar, na pasta `mom`:

```powershell
java -cp 'target/classes;target/test-classes;lib/*' br.com.victorsfec.mom.UiRenderMOM target/ui
```

No Linux/macOS, use `:` como separador do classpath. Os testes locais validam JMS/TCP e processos separados; a configuração de rede entre máquinas deve ser verificada no ambiente da apresentação.
