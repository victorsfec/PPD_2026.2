# Validação da entrega

Verificação realizada em 17/11/2026, com Windows e Oracle JDK 25.

## Compilação e testes

- `build.ps1 -JdkHome 'C:\Program Files\Java\jdk-25'`: compilação, testes de integração e geração dos quatro executáveis.
- `mvn clean package`, com Maven 3.9.9 e JDK 25: concluído com código de saída 0.
- `AppTestMOM`: **34 verificações aprovadas**, usando conexões JMS/TCP reais com ActiveMQ e três JVMs separadas para workers externos.
- Casos cobertos: contagem Unicode, palavras inteiras, entrada inválida, arquivos vazios, uma linha, paridades, linhas vazias, 3.001 linhas, distribuição entre workers, mensagens fora de ordem e repetidas, execuções simultâneas isoladas, dois subscribers, rollback, codificação inválida, cancelamento e queda do broker.

## Executável e exemplo

Comando executado na pasta `mom`:

```powershell
java -jar target/mom-1.0-client.jar --console --local --file examples/entrada.txt --words 'java,paralelo,distribuída' --workers 3
```

Saída conferida:

```text
java = 4
paralelo = 3
distribuída = 4
Linhas: 5/5
```

Os três workers processaram linhas. A distribuição exata varia conforme o escalonamento.

## Interface

Cliente e dashboard renderizados pelos componentes Swing usando Java2D, e imagens inspecionadas visualmente. Os dados do dashboard vieram de uma execução JMS real do exemplo. As imagens estão em [interface/cliente.png](interface/cliente.png) e [interface/dashboard.png](interface/dashboard.png).

Essa inspeção confirma o layout renderizado. A automação interativa da janela nativa não ficou disponível na sessão, portanto não é apresentada como teste automatizado de cliques. A execução entre máquinas físicas também não foi testada; depende do IP e das regras de firewall do ambiente da apresentação.
