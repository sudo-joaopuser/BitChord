# Spotify Core limpo — só API oficial, nada que viole

> DECISÃO 2026-10-28 (Joao delegou a escolha): o melhor caminho é
> **App Remote como playback primário no celular + Connect como
> fallback para outros dispositivos**, com Web API para biblioteca e
> catálogo. App Remote acorda o app oficial, dá qualidade cheia,
> estado em tempo real e checagem de Premium via capabilities; Connect
> cobre desktop e caixas. Librespot foi descartado de vez por violar
> termos e quebrar sem aviso. Implementado no commit `9eea38a`:
> `SpotifyAuthImpl` (PKCE), `SpotifyCatalogImpl` (Web API),
> `SpotifyConnectPlayer` (Player Connect) e `SpotifyLoginFlow`.
> O adaptador App Remote em si entra depois, atrás da interface
> `SpotifyAppRemotePlayer`, quando o AAR estiver em `app/libs/`.
>
> TELA DE LOGIN: pede o Client ID do Spotify Developer antes de
> qualquer coisa — campo de texto, validação de 32 caracteres, e o
> redirect URI (`bitchord-spotify://callback` por padrão) exibido para
> copiar e registrar no dashboard. Sem Client ID digitado, não abre o
> login do Spotify. Nada hardcoded.

Este documento substitui o plano anterior de núcleo Rust/librespot.
Motivo: o usuário pediu um Core limpo, sem nada que viole algo. Stream
direto do servidor por protocolo não oficial (librespot/psst) viola os
termos do Spotify, mesmo com Premium. O Core limpo, então, usa só o que
o Spotify oferece oficialmente para terceiros.

## A troca honesta que o Core limpo impõe

Não existe, na API oficial, um endpoint "me dá o áudio da faixa para eu
tocar dentro do meu app sem o app do Spotify". O que existe oficialmente:

1. **App Remote (Android SDK)** — o app BitChord manda comandos e recebe
   estado, e o áudio toca dentro do processo do app oficial do Spotify,
   via IPC. Exige: app oficial instalado e rodando, conta Premium para
   tocar faixa avulsa sob demanda, Client ID + fingerprint SHA-1 do
   pacote registrados no dashboard. O AAR é proprietário e NÃO pode ser
   redistribuído: quem builda baixa de `spotify/android-sdk` e coloca
   em `app/libs/`, como o projeto Lyra faz.
2. **Web API Player (Spotify Connect)** — o BitChord controla a reprodução
   num dispositivo ativo (`GET /me/player/devices`, `PUT /me/player/play`,
   pause, next, previous, seek, volume, shuffle, repeat, queue, transfer).
   O áudio sai no dispositivo escolhido (celular com app oficial,
   desktop, caixa Connect). Também exige Premium.

Ou seja: no Core limpo, o BitChord vira a interface bonita + biblioteca
+ controle, e o Spotify oficial continua sendo o tocador. Se a exigência
fosse "tocar sem o app oficial instalado", ela conflitaria com "não
violar nada" — e aí o pedido limpo teria que ser abandonado, não
disfarçado.

## Auth: só OAuth com PKCE

- Fluxo Authorization Code com PKCE, sem client secret dentro do app.
- Redirect URI registrado no dashboard (ex.: `http://127.0.0.1:8888/login`
  para teste local, ou o esquema do app para release, registrado igual).
- Tokens guardados em storage criptografado (o `EncryptedPrefs` que o
  BitChord já tem), refresh antes de expirar (access token dura 1 h).
- Scopes mínimos, só os que a função usa:
  `user-read-private user-library-read playlist-read-private
  playlist-read-collaborative user-read-playback-state
  user-modify-playback-state user-read-currently-playing
  user-read-recently-played user-top-read app-remote-control streaming`
- Sem senha do usuário no app, sem cookie `sp_dc`, sem WebView de login
  raspando sessão.

## Ecossistema coberto pelo Core (Web API v1)

- Perfil: `GET /me`
- Biblioteca: `GET /me/playlists`, `GET /playlists/{id}/items`,
  `GET /me/tracks` (Liked Songs), `GET /me/albums`, `GET /me/shows`
- Catálogo: `GET /search`, `GET /albums/{id}`, `GET /artists/{id}`,
  `GET /artists/{id}/top-tracks`, `GET /artists/{id}/albums`,
  `GET /shows/{id}/episodes`, `GET /episodes/{id}`
- Histórico e gosto: `GET /me/player/recently-played`,
  `GET /me/top/tracks`, `GET /me/top/artists`
- Playback e dispositivos: os endpoints de Player listados acima
- Biblioteca do usuário: salvar/remover faixa e álbum, criar playlist
  (`POST /me/playlists`), seguir artista/playlist — só com scope
  explícito para cada ação

## Regras do Core (o que ele NUNCA faz)

- Não baixa, grava ou guarda arquivo de áudio; não faz cache de áudio.
- Não descriptografa, não burla DRM, não usa protocolo interno.
- Não raspa página (`open.spotify.com/embed`), não usa GraphQL de
  parceiro (`api-partner.spotify.com`), não usa cookie de sessão.
- Não usa conteúdo do Spotify para treinar modelo, nem agrega catálogo
  além do permitido para exibir na hora.
- Não tem adblock, não pula anúncio, não promete burlar nada: conta
  Premium já não tem anúncio, e conta free é barrada no playback
  (o servidor responde 403 / `PREMIUM_REQUIRED`).
- Respeita rate limit: ao receber 429, espera o `Retry-After`, sem
  retry em loop. Não faz polling agressivo do player.

## Limites de fevereiro de 2026 que afetam a distribuição

- Dono do app em Development Mode precisa ter Premium.
- 1 Client ID por desenvolvedor, no máximo 5 usuários autorizados por
  app em Development Mode, busca limitada a 10 resultados por request
  em apps novos, e vários endpoints restritos.
- Para distribuir para muita gente é preciso Extended Quota Mode, com
  revisão do Spotify — ou seja: para uso pessoal do Joao, o Core limpo
  funciona; para publicar para todo mundo, há um pedido de revisão no
  caminho, e isso não depende de código.

## O que sai do BitChord atual por ser não oficial

- `auth/SpotifyLoginScreen.kt`: login por WebView que captura o cookie
  `sp_dc` — sai, entra OAuth PKCE.
- `data/spotify/SpotifyLibrary.kt`: GraphQL de parceiro com bearer +
  client token de web player — sai, entra Web API v1.
- `data/spotify/SpotifyImporter.kt`: raspagem do embed público — sai.
- `data/spotify/SpotifyCanvas*` e `CanvasRepository` onde dependem de
  token de web player — revisar, manter só o que vier da Web API.
- Todo o YouTube/Innertube, PoToken, login Google, JioSaavn e módulos
  de outras fontes: o fork é só Spotify, e o `compileSdk 37` que só
  existia por causa do AAR do InnerTubeX deixa de ser obrigatório
  (o SDK 36, que já está instalado aqui, passa a bastar para o build).

## Estrutura do esqueleto

`app/src/main/java/com/music/bitchord/spotifycore/SpotifyCore.kt`:
modelos puros + interfaces `SpotifyAuth`, `SpotifyCatalog`,
`SpotifyPlayer` (Connect) e `SpotifyAppRemotePlayer`. Nenhuma classe
ali importa SDK do Spotify, librespot ou cookie. A implementação com
o AAR do App Remote entra depois, atrás da interface, quando o AAR
estiver em `app/libs/` — e sem ele o app compila, só não conecta o
playback do App Remote.

## Login direto no app + Spotify como Source

A pedido do Joao, nada é pedido no chat: a tela
`ui/screens/SpotifyCoreLoginScreen.kt` pede o Client ID dentro do
app, mostra o redirect URI para registrar no dashboard, abre o
Spotify no navegador e volta por `bitchord-spotify://callback`
(intent-filter no Manifest, `auth/SpotifyLoginLink.kt`, troca de
código em `MainActivity`). Spotify também virou `SourceKind.SPOTIFY`
(seedado no `SourceRegistry`, ícone na tela Sources, toque abre o
login) via `data/sources/SpotifySource.kt` — busca lista do Web API,
stream retorna null de propósito, porque playback limpo não tem URL:
vai pelo App Remote/Connect.
