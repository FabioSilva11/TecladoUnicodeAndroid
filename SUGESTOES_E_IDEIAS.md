# Sugestões de Melhorias e Novas Funções Úteis para o Teclado

Este documento reúne ideias práticas, diferenciais criativos e evoluções técnicas para transformar o projeto em um teclado Android ainda mais completo, rápido e único.

---

## 1. Evoluções para o Modo **Anti-Friendzone (`💘`)**

1. **Analisador de Tom e Resposta Inteligente (IA Opcional)**
   - Permitir colar a última mensagem recebida da pessoa (ex.: *"haha verdade"* ou *"tô meio ocupada essa semana"*) e sugerir **3 respostas calibradas** com perfis diferentes:
     - **🔥 Direto / Confiante** (cria tensão e evita cair no papo de amigo)
     - **😏 Bem-humorado / Provocativo** (quebra respostas secas com leveza)
     - **🎯 Convite Casual** (converte conversa boa em encontro real)
2. **Detector de "Mensagem Morna" Antes de Enviar**
   - Um indicador discreto na barra superior que avisa quando a frase digitada parece genérica demais (ex.: *"oi tudo bem?"*, *"bom dia princesa"*, *"oq vc faz de bom?"*) e sugere na hora uma alternativa mais autêntica e interessante.
3. **Variáveis Dinâmicas nos Templates (`{nome}`, `{dia}`, `{hora}`)**
   - Permitir criar frases rápidas na aba **⭐ Minhas** como `"Ei {nome}, bora tomar um café na {dia}?"`, preenchendo automaticamente a saudação (*bom dia / boa tarde / boa noite*) pelo horário do celular.
4. **Pacotes Temáticos Importáveis**
   - Permitir exportar e importar pacotes JSON de mensagens rápidas (ex.: *Primeiro Contato*, *Instagram Stories Reply*, *Pós-Encontro*, *Networking Profissional*, *Vendas Rápidas no WhatsApp*).

---

## 2. Evoluções para o Tema **PC Gamer Mecânico RGB**

1. **Perfis de Switches Mecânicos Reais (Áudio Sintetizado / Samples `.ogg` leves)**
   - Adicionar escolha do tipo de switch nas Configurações:
     - **Cherry MX Blue**: clique agudo duplo audível (*clicky*)
     - **Cherry MX Brown / Holy Panda**: som grave aveludado (*thocky / tactile*)
     - **Cherry MX Red Linear**: batida suave silenciosa
     - **Máquina de Escrever Retrô**: som clássico de tecla + sino no botão Enter (`↵`)
2. **Modos de Iluminação RGB Customizáveis**
   - Além do modo **Onda Arco-Íris (Rainbow Wave)** atual, adicionar:
     - **Reativo Puro (Reactive Matrix)**: teclado todo apagado no escuro OLED e só a tecla tocada acende em neon e espalha uma onda radial pelas teclas vizinhas.
     - **Respiração (Breathing)** em duas cores escolhidas pelo usuário (ex.: Ciano + Magenta Cyberpunk).
     - **Velocímetro de Digitação (WPM Heat)**: as teclas mudam de azul frio → roxo → vermelho fogo conforme você digita mais rápido!

---

## 3. Gestos Avançados de Produtividade (Estilo Pro)

1. **Trackpad de Cursor na Barra de Espaço**
   - Deslizar o dedo para a esquerda/direita em cima da barra de espaço move o cursor letra por letra com vibração suave a cada caractere (sem precisar abrir o painel *Edição de texto*).
2. **Deslizar na Tecla Apagar (`⌫`) para Excluir Palavras Inteiras**
   - Tocar e arrastar para a esquerda a partir do `⌫` seleciona palavras inteiras para trás e apaga tudo ao soltar (igual ao gesto avançado do Gboard).
3. **Digitação por Deslize (*Glide / Swipe Typing*)**
   - Desenhar uma trilha luminosa sobre as letras sem tirar o dedo da tela e cruzar o traço geométrico com o dicionário `pt_br_words.txt` de 27.500 palavras para adivinhar a palavra inteira instantaneamente.

---

## 4. Utilidades Inteligentes na Barra de Sugestões

1. **Calculadora Instantânea Inline**
   - Ao digitar uma conta como `145*3=` ou `250/4=` em qualquer conversa (WhatsApp, Notas, Instagram), a barra de sugestões exibe **`435`** ou **`62,5`** para inserir o resultado com 1 toque sem sair do chat!
2. **Atalhos Expansores de Texto (Text Snippets)**
   - Configurar gatilhos curtos no Dicionário Pessoal, como:
     - `@pix` → insere sua chave PIX completa
     - `@end` → insere seu endereço completo
     - `@email` → insere seu e-mail principal
3. **Conversor Rápido de Maiúsculas/Minúsculas na Seleção**
   - Quando um texto estiver selecionado na tela, tocar na tecla **Shift (`⇧`)** alterna o texto selecionado entre `minúsculas` → `Primeira Maiúscula` → `TUDO MAIÚSCULO`!
4. **Leitura Inteligente por Voz (TTS) com Controle de Velocidade e Idioma**
   - No recurso de **Texto para Fala (`🔊`)**, permitir ouvir também textos traduzidos na pronúncia nativa (ex.: ouvir como se pronuncia a frase traduzida em *Inglês* ou *Japonês* antes de mandar!).

---

## 5. Privacidade, Desempenho e Backup

1. **Modo Incógnito Automático**
   - Detectar abas anônimas de navegadores (`IME_FLAG_NO_PERSONALIZED_LEARNING`) para pausar automaticamente o histórico da Área de Transferência e de Emojis Recentes.
2. **Exportar / Restaurar Configurações Localmente**
   - Salvar um único arquivo `.backup` contendo seus Temas com foto recortada, itens com cadeado (`🔒`) da Área de Transferência, frases do Anti-Friendzone e Dicionário Pessoal para levar para outro celular facilmente.
