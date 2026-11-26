# Teclado Unicode Android

> **Projeto pessoal de código aberto** — Teclado virtual completo para Android, leve e sem dependências externas, combinando a ergonomia do **Google Gboard** com recursos exclusivos de personalização, fontes Unicode e temas RGB.

## Capturas de Tela

*(Nenhuma captura de tela foi adicionada ainda. Para exibir prints aqui, salve as imagens em `docs/screenshots/` e adicione-as com `![Descrição](docs/screenshots/nome-do-arquivo.png)`.)*

## Recursos Principais

- **Teclado padrão QWERTY PT-BR (`a–z` / `A–Z`)**: acentos deslizantes no toque longo (`á, ã, â, à, é, ê, í, ó, õ, ô, ú, ü, ç, ñ`), linha de números opcional, **8 Fontes Unicode ao digitar (`Aa`)** e **4 Fontes visuais nas teclas**.
- **Modo Anti-Erro Dinâmico + Dicionário de 27.500 palavras**: corrige automaticamente palavras sem acento ou com erros ortográficos complexos (ex.: `refrerencia` → `referência`, `inpessa` → `impeça`, `tanbem` → `também`), configurável nas **Configurações (`⚙️`)**.
- **Temas avançados (`🎨`)**:
  - **PC Gamer Mecânico RGB**: chassi preto profundo (`#05060A`), teclas mecânicas 3D que afundam fisicamente ao tocar, clique de switch e onda **LED RGB individual por tecla, ícones e menus** em tempo real.
  - **Cores Dinâmicas (*Material You*)**, *Preto OLED*, *Claro*, *Escuro*, *Verde-água*, *Vermelho Rubi* e *Verde Floresta*.
- **1.898 Emojis em 10 categorias oficiais**: `🕒 Recentes`, Carinhas, Pessoas, Animais, Comida, Viagens, Atividades, Objetos, Símbolos e Bandeiras, além de busca em português e emoticons clássicos (`:-)`).
- **Área de Transferência com Cadeado (`🔒`) e Exclusão (`🗑️`)**: trave itens importantes na seção *Fixados* para impedir qualquer exclusão acidental.
- **Seção Anti-Friendzone (`💘`)**: área de mensagens rápidas dividida em 5 categorias (*Gelo*, *Flertar*, *Sair*, *Assunto* e *Minhas frases*).
- **Google Tradutor integrado (`G文`)**: tradução em tempo real usando API gratuita (7 idiomas) + **Texto para Fala (`🔊` TTS)** e **Voz (`🎤`)**.

## Tecnologias

- **Linguagem:** Java (Android SDK)
- **Build:** Gradle (`compileSdk 35`, `minSdk 23`, `targetSdk 35`)
- **CI/CD:** GitHub Actions (`.github/workflows/build-apk.yml`)

## Como Compilar

```bash
./gradlew assembleDebug
```

O APK gerado ficará em `app/build/outputs/apk/debug/`.

## Roadmap

Veja também [SUGESTOES_E_IDEIAS.md](SUGESTOES_E_IDEIAS.md) para o roadmap de próximas evoluções.

## Licença

Este é um projeto pessoal de código aberto. Sinta-se à vontade para estudar, modificar e contribuir.
