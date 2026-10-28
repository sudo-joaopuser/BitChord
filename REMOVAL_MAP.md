# Mapa de remoção — BitChord só com Spotify, Core limpo

Levantado por grep no fork em 2026-10-28, base `04a4a83`. "Sai" não
significa apagar o arquivo inteiro sem olhar: onde o arquivo mistura
Spotify oficial com não oficial, sai só o caminho não oficial e o
restante vira o Core limpo (`spotifycore/`). A ordem é a que evita
deixar o app sem tocar nada no meio do caminho.

## 1. Spotify não oficial — sai primeiro, entra o Core limpo

Motivo: usa sessão de web player, não OAuth oficial.

- `app/src/main/java/com/music/bitchord/auth/SpotifyLoginScreen.kt`
  — login por WebView que captura o cookie `sp_dc`. Sai inteiro,
  entra OAuth PKCE.
- `shared/.../data/spotify/SpotifyLibrary.kt` — GraphQL de parceiro
  (`api-partner.spotify.com`) com bearer + client token de web player.
  Sai inteiro, entra Web API v1 (`/me/playlists`, `/me/tracks`, etc.).
- `shared/.../data/spotify/SpotifyImporter.kt` — raspagem do embed
  público (`open.spotify.com/embed/playlist/...`). Sai inteiro.
- `app/.../data/canvas/SpotifyToken.kt` — cunha bearer de web player
  a partir do cookie `sp_dc`, com WebView carregando `open.spotify.com`.
  Sai inteiro.
- `shared/.../data/canvas/SpotifyCanvasQuery.kt` — query GraphQL
  `canvas` do web player. Sai inteiro.
- `app/.../data/canvas/CanvasRepository.kt` e `SpotifyCanvas.kt` —
  dependem do token acima. Sem API oficial de Canvas, o recurso Canvas
  não tem versão limpa: sai junto, e a tela volta para capa estática.
- `shared/.../data/spotify/SpotifyLibraryTest.kt` — teste do caminho
  de parceiro. Sai com ele.
- Referências a espalhar e limpar depois: `BitChordApplication.kt`,
  `MainActivity.kt`, `MainViewModel.kt`, `AppSettings.kt`
  (campo do token `sp_dc`), `SpotifyCanvasAuthScreen.kt`,
  `SpotifyLibraryScreen.kt`, `SpotifyImportAlert.kt`,
  `AndroidPlayerHost.kt`, `DetailScreen.kt`, `SettingsSheet.kt`.
  Essas telas não somem: passam a ler do Core limpo, não do token.

## 2. YouTube / YouTube Music — sai em segundo, depois que o Spotify toca

- `app/.../data/innertube/` (AndroidStreamHooks, PlaybackTracker,
  `potoken/`) e `shared/.../data/innertube/` (Innertube, InnertubeParser,
  PlayerClient, StreamResolver, InnerTubeXResolver, UploadedTracks,
  `potoken/`) — 14 arquivos só nesses dois diretórios, 44 arquivos no
  total citam innertube/YtMusic/StreamResolver.
- `shared/.../data/YtMusicRepository.kt` — repositório do YouTube Music.
- `app/.../data/sources/YouTubeSource.kt` e `SourceKind.YOUTUBE`.
- `app/.../auth/YtMusicLoginScreen.kt`, `WebSession.kt` (na parte de
  sessão YouTube) e login Google em `MainActivity.kt`/`AuthStore.kt`/
  `OnboardingScreen.kt`.
- Dependências em `app/build.gradle.kts`:
  `innertubex-android:v0.7.0`, NewPipeExtractor (jar stripado) +
  nanojson, jsoup, protobuf-javalite, rhino — todas só para extração
  de stream do YouTube. Ao sair, some também o motivo do
  `compileSdk = 37`: o comentário no próprio build diz que o 37 é
  exigência do AAR do InnerTubeX. Sem ele, `compileSdk 36` (que já
  está instalado aqui) basta, e `targetSdk` continua 36.

## 3. Outras fontes — sai, o fork é só Spotify

- `app/.../data/sources/JioSaavnSource.kt` e `SourceKind.JIOSAAVN`.
- `app/.../data/sources/ModuleSource.kt` + `module/` (QuickJS, índice
  de módulos, SpineModule) e `AddonSource.kt` + `addon/` — fontes
  genéricas de terceiros, fora do ecossistema Spotify.
- `app/.../data/jiosaavn/` se existir como diretório de suporte.
- Dependência QuickJS (`quickjs-kt-android`) sai com os módulos.
- `SourceRegistry.kt` e `SourceResolver.kt`: não são apagados, são
  reescritos — sem eles não há de onde o player antigo puxar faixa,
  e o Core limpo assume esse papel via App Remote/Connect.

## 4. Rework, não remoção

Estes arquivos ficam, mas perdem o ramo YouTube:

- `playback/PlaybackService.kt`, `AudioCache.kt`, `PlaybackFallback.kt`,
  `Autoplay.kt` — hoje resolvem faixa via YouTube; passam a comandar
  o Core limpo e a refletir o estado que vem do App Remote/Connect.
- `playback/smart/TrackAnalyzer.kt`, `data/stats/*` — análise e
  estatísticas sobre faixa tocada, independem da fonte, mas hoje leem
  `videoId`; passam a ler `spotify:track:` URI.
- `ui/MainViewModel.kt`, `ui/screens/DetailScreen.kt`,
  `ui/screens/SourcesScreen.kt` — telas ficam, fonte muda.

## 5. Fica, com decisão do usuário depois (não é violação do Spotify)

- Scrobbling Last.fm / ListenBrainz: autorizado pelo usuário na API
  deles, não usa conteúdo do Spotify de forma não oficial. Pode ficar.
- Discord Rich Presence: lê o que está tocando e publica; não baixa
  áudio. Pode ficar, se o Joao quiser.
- Letras (LRCLIB e outros provedores): vêm de terceiros por
  artista/título, não do Spotify. Podem ficar, desde que não sejam
  apresentadas como conteúdo do Spotify.
- Música local, SMB, WebDAV, downloads próprios do usuário: fora do
  Spotify, mas não violam o Spotify. No fork "só Spotify" estrito,
  saem por escopo, não por violação — decisão de produto, não de
  conformidade.

## Ordem de execução

1. Core limpo funciona (auth + biblioteca + playback via App Remote).
2. Sai a seção 1, com as telas apontando para o Core.
3. Sai a seção 2 e a 3, com o build caindo para compileSdk 36.
4. Rework da seção 4 e build final conferido antes de qualquer entrega.
