# BitChord só com Spotify — fork

Base: `kushagrasinghx/BitChord` em `04a4a83`, branch local `spotify-only`.
Objetivo: um app só com Spotify, só para conta Premium, sem adblock e sem
nenhum truque de YouTube. Não é um tema por cima do BitChord: é trocar a
fonte de áudio.

## O que o BitChord já faz com Spotify (e o que falta)

Hoje o Spotify no BitChord é só metadado: `SpotifyLibrary` (GraphQL em
`api-partner.spotify.com`, com cookie `sp_dc`) e `SpotifyImporter` (embed
do `open.spotify.com`) listam playlists e Liked Songs, e cada faixa é casada
com um `videoId` do YouTube e tocada pelo YouTube Music. Falta exatamente
a parte difícil: tocar o áudio que vem do servidor do Spotify.

## Decisão do motor (corrigida com evidência)

Primeira hipótese era librespot-java, por ser JVM e encaixar no Kotlin.
O próprio README dele diz o contrário: **não está mais em desenvolvimento
ativo**, o esforço migrou para o go-librespot. Usar uma lib parada contra
um protocolo que o Spotify muda (ex.: a mudança de acesso de fev/2026)
é pedir para o app parar de tocar do nada.

Decisão: **núcleo Rust (linhagem librespot / psst-core) compilado para
Android via cargo-ndk, com ponte JNI**. Referências que provam o caminho:

- `psst-core`: `librespot-protocol 0.7.1`, login5, Mercury, resolve no CDN,
  chave de áudio, descriptografia e decode Ogg Vorbis via Symphonia.
  Só que a GUI dele é Druid, só desktop — o que se reaproveita é o core,
  não o app.
- `spotlet`: prova Rust + Android na prática — `cargo ndk -t arm64-v8a`
  gerando `.so` em `app/src/main/jniLibs`, com JNI e AudioTrack.
- A Web API oficial sozinha NÃO basta: dá metadados/busca/playlists, mas
  só prévia de 30 s. Web Playback SDK é só navegador. App Remote exige o
  app oficial instalado. Stream completo direto do servidor = protocolo
  librespot, que exige Premium (o psst retorna erro "premium account
  required" para conta free).

## Por que não é só mais um MusicSource

O `MusicSource` do BitChord (`data/sources/MusicSource.kt`) devolve um
`SourceStream` com uma URL que o Media3/ExoPlayer abre. O Spotify via
librespot não devolve URL: devolve áudio descriptografado/PCM. Enfiar
isso numa URL quebraria o modelo. Por isso o backend do Spotify é um
caminho de playback próprio, que alimenta o áudio (AudioTrack ou um sink
custom do Media3), e não um `YouTubeSource` trocado.

## O que fica e o que sai (em fases, para não quebrar tudo de uma vez)

Fica, pelo menos na fase 1: UI em Compose, telas de biblioteca/playlist,
fila, letras, scrobbling, sessão de mídia em foreground e a maior parte
do `PlaybackService` como casca — o que muda é de onde vem o áudio.

Sai no fork final, sem volta:

- `data/innertube/` (app + shared) e `data/sources/YouTubeSource.kt`
- `SourceKind.YOUTUBE` e o seed dele no `SourceRegistry`
- Todo o caminho de login Google / YouTube Music
- `PoTokenWebView` e o resolver de stream do YouTube
- JioSaavn e módulos/addons de outras fontes (fork é só Spotify)
- Qualquer coisa de adblock: não existe módulo de adblock no BitChord
  hoje (só um disclaimer no README), então não há o que remover — só
  não criar nada disso, e conta Premium já não tem anúncio

Ordem: primeiro o playback do Spotify funciona lado a lado, depois o
YouTube é apagado. Apagar 115 arquivos que citam innertube/YouTube antes
de ter o Spotify tocando deixaria um app que não toca nada e sem
referência funcionando para comparar.

## Fases

1. Protótipo: login OAuth com Client ID próprio + tocar 1 faixa Spotify
   (PCM via JNI -> AudioTrack), sem mexer na UI principal.
2. Integrar fila, play/pause/seek/próxima, capa e metadados com o
   `PlaybackService` e a sessão de mídia existentes.
3. Trocar biblioteca/busca/playlists para a Web API com o Client ID
   próprio (a partir de fev/2026 o psst exige isso para busca e biblioteca).
4. Apagar o YouTube e as outras fontes, limpar login Google, telas e
   strings que só faziam sentido para o YouTube.
5. Build de release, teste no aparelho com conta Premium, e só aí
   considerar pronto.

## O que falta do usuário (nada de senha no chat)

- Client ID do Spotify Developer (dashboard do Spotify, redirect
  `http://127.0.0.1:8888/login`, como o psst usa) — sem ele, busca e
  biblioteca da Web API não funcionam no modelo atual.
- Um fork no GitHub (ex.: `sudo-joaopuser/BitChord-Spotify`) + deploy key
  se quiser que eu suba isso, no mesmo padrão usado no garnet. Por
  enquanto o fork é só local, nesta pasta, branch `spotify-only`.
- Teste no aparelho com a conta Premium dele quando o protótipo da
  fase 1 existir. Aqui nesta VM ainda não há JDK nem Android SDK, então
  o build do APK ainda não foi conferido — isso precisa ser instalado
  antes de prometer um APK.

## Status

- [x] Fork local criado, branch `spotify-only`
- [x] Motor decidido: Rust librespot via JNI, não librespot-java
- [ ] Fase 1: protótipo de playback
- [ ] Fases 2–5: integração, limpeza do YouTube, build e teste
