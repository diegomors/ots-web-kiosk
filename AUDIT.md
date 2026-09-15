# Auditoria técnica do ots-web-kiosk

Data: 2026-09-15

## Resultado

O aplicativo não contém cliente HTTP próprio, telemetria, analytics, crash reporting, SDK de anúncios, ativação remota ou validação de licença. A única comunicação de rede em runtime é a navegação normal do WebView para a Home Page configurada e para os recursos e redirecionamentos solicitados por essa página.

A licença MIT é somente um arquivo legal local. Não existe código que consulte servidor de licença ou que possa bloquear o aplicativo por expiração, assinatura ou indisponibilidade de um serviço do projeto original.

## Dados que podem sair do dispositivo

- O WebView acessa a Home Page e os recursos HTTPS utilizados por ela.
- Cookies, formulários, JavaScript e armazenamento web seguem o comportamento normal do site carregado.
- Se a Home Page solicitar geolocalização e o usuário conceder a permissão Android, a origem HTTPS configurada poderá receber a localização fornecida pelo Android.
- O fluxo de build acessa Gradle, Google Maven, Maven Central e GitHub para dependências e releases; isso não faz parte do runtime instalado.

Não há ping para Google, health check externo, chamada de telemetria ou integração oculta. Navegações em texto claro são recusadas pelo código e pelo Network Security Config.

## Problemas corrigidos

- O overlay específico de TV que cobria o WebView e consumia touch/click/scroll foi removido.
- A rotação visual do `RotatedWebView`, que trocava medidas e desalinhava o touch, foi removida; WebView e tela física agora usam as mesmas coordenadas do Android.
- O retry deixou de recriar WebViews continuamente e passou a usar backoff limitado, sem overflow.
- Uma mudança real de rede dispara nova tentativa, mas não bloqueia navegação por ping ou teste externo.
- Falha do processo de renderização do WebView agora recria o componente com segurança.
- Geolocation foi implementada com permissão em runtime e limitada à origem HTTPS da Home Page.
- Back curto é consumido; Back físico por aproximadamente dois segundos abre o PIN.
- PIN passou a usar PBKDF2, salt aleatório, comparação em tempo constante e backup desabilitado.
- A derivação/verificação do PIN foi movida para thread de trabalho para não congelar a UI.
- Settings e Exit Kiosk somente ficam disponíveis depois da validação do PIN.
- Primeiro uso agora exige URL, orientação e criação/confirmação do PIN antes do kiosk.
- Inicialização no boot e recuperação de foreground respeitam opções independentes.
- A recuperação de foreground faz no máximo uma tentativa por saída do app e oferece retorno manual pela notificação; não há relaunch infinito.
- O serviço de foreground é suspenso durante PIN, permissão e Settings.
- Debug remoto do WebView fica habilitado somente em builds debug.
- Acesso a arquivos/conteúdo local, conteúdo misto e HTTP foram desabilitados.
- Backup e transferência de dados do app foram desabilitados para proteger o hash do PIN.
- A configuração de URL é validada também na camada persistente, evitando HTTP por preferência corrompida.
- A sobrescrita artificial de viewport foi removida; a página usa o viewport responsivo normal do Android System WebView.
- O workflow de release não altera nem faz push no README a partir de uma tag.
- Setup, Settings, PIN e Admin Menu foram redesenhados com o tema OTS Cyber Minimal Dark e controles nativos acessíveis a touch, teclado e controle remoto.
- Setup, Settings, Change PIN, PIN/Menu administrativo, WebView, teclado e overlays de loading/erro agora acompanham a orientação real selecionada.
- Em Android TV/Google TV, a permissão local `WRITE_SETTINGS` é usada somente para travar o sensor e aplicar `USER_ROTATION`; sem ela, o app evita o modo letterbox que desloca as coordenadas de touch.
- Diálogos administrativos deixaram de usar uma janela Android separada e todas as telas compartilham o mesmo sistema de coordenadas do display.
- Ícone, foreground adaptativo, banner de TV e interface agora usam a marca cúbica fornecida pela OnTimeStack.
- URL e PIN agora têm validação obrigatória por campo, mensagens específicas, confirmação exata do PIN e foco/scroll automático para o primeiro campo inválido.
- As ações IME usam Next entre campos relacionados e Done fecha o teclado; o login administrativo também diferencia PIN vazio de PIN incorreto.

## Simplificações

- Uma única implementação de configurações com SharedPreferences substitui DataStore + SharedPreferences duplicados.
- Foram removidos controle de brilho ocioso, pedido desnecessário de notificação, permissão de overlay, desbloqueio por cinco toques, overlays de input de TV, intervalo configurável do serviço e campos avançados.
- Foram removidas dependências de TV Material, Leanback UI, DataStore, tooling/testes não utilizados e recursos antigos sem uso.
- A tela de configurações expõe somente Home Page, orientação, PIN, boot e foreground.

## Identidade final

- Projeto e artefatos: `ots-web-kiosk`
- Application ID e namespace: `com.ontimestack.webkiosk`
- Nome instalado: `OTS Kiosk`
- Home Page padrão: `https://ontimestack.com`

As referências restantes a Screenlite existem somente no crédito de origem e na licença MIT, que devem ser preservados.

## Validação executada

- `assembleDebug`: aprovado.
- `assembleRelease` e `lintVitalRelease`: aprovados; APK release assinado e instalado no AVD.
- `lintDebug`: aprovado, zero erros.
- `testDebugUnitTest`: aprovado, incluindo o mapeamento 0°/90°/180°/270° para orientações da Activity e rotações do sistema.
- Auditoria estática de URLs, logs, permissões, dependências e SDKs de rede: concluída.
- Manifesto e APK inspecionados para package ID, label e permissões.
- AVD atual `OTS_Kiosk_Google_TV_API_36` criado com Google TV Android 16 ARM64, 1920×1080, multitouch e Android System WebView 143.
- A URL de ativação do Mizz carregou, enviou a credencial de dispositivo e redirecionou para a rota final do kiosk no AVD API 36; nenhuma credencial foi incorporada ao app ou ao repositório.
- APK instalado no AVD; primeiro uso, persistência do PIN, PIN incorreto, menu administrativo e Settings validados.
- Back curto validado sem sair. Back longo foi validado por duração real e pelo sinal nativo `isLongPress`; o build final usa 2 segundos.
- A navegação por tap no Mizz WebView foi validada no APK release sem transformação intermediária de coordenadas.
- A rotação real do display e da tela Settings foi validada por interação no AVD em 0°, 90°, 180° e 270°, sem recriar nem perder o estado do formulário.
- O teclado em retrato foi validado a 90°; sem `WRITE_SETTINGS`, a TV permanece em tela cheia na orientação nativa e exibe o aviso de provisionamento em vez de entrar no modo letterbox com touch deslocado.
- `localStorage` e `sessionStorage` passaram em write/read/remove; IndexedDB passou em open/delete dentro do WebView em execução.
- `fetch`, WebSocket, cookies, Web Crypto, Service Worker, geolocation e `mediaDevices` foram detectados no contexto HTTPS do WebView.
- Validação manual do formulário confirmou erros de PIN obrigatório, confirmação obrigatória e PIN divergente, com foco/scroll para o campo correspondente; Next move o foco e Done fecha o teclado.
- Queda e retorno do Wi-Fi foram simulados; a mensagem de erro saiu e a Home Page recarregou automaticamente.
- Reboot validou a recepção de `BOOT_COMPLETED` e a inicialização do foreground service.

## Limitações e validação pendente

Não havia Xiaomi TV Box S 2nd Gen nem Dell P2424HT conectado à máquina de build. Portanto, touch USB HID, drag, inputs, eventos JavaScript, geolocalização real, rotação 180°, boot e recuperação de foreground ainda precisam ser homologados no hardware indicado no README.

No AVD Android 12, o sistema entregou `BOOT_COMPLETED` e iniciou o serviço, mas bloqueou a abertura automática da Activity. O mesmo bloqueio ocorreu ao tentar retornar do launcher, inclusive ao mover a task existente para a frente. Android e Google TV restringem abertura de Activity em segundo plano; portanto, boot e foreground são best effort sem Device Owner, root, Accessibility Service ou hacks agressivos, exatamente como exigido pelo escopo. A notificação persistente permite reabrir o app manualmente. Consulte as documentações Android sobre [restrições de foreground service](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [broadcasts](https://developer.android.com/develop/background-work/background-tasks/broadcasts) e [`AppTask.moveToFront()`](https://developer.android.com/reference/android/app/ActivityManager.AppTask#moveToFront()).

A imagem Google TV API 31 legada contém Android System WebView 91. O JavaScript atual da página de ativação do Mizz usa blocos estáticos de inicialização de classe, sintaxe que esse WebView antigo não interpreta. A hidratação para antes de enviar a ativação, fazendo a página parecer quebrada. No novo AVD API 36 com WebView 143, a mesma ativação foi concluída e houve redirecionamento para a rota final do kiosk. O dispositivo final deve manter Android System WebView/Chrome atualizado antes da homologação.

## Diagnóstico da integração Mizz

A mensagem de falha ao iniciar o autoatendimento era gerada pelo `mizz-ui` quando `supabase.auth.signInAnonymously()` era rejeitado. O armazenamento local do WebView foi validado como funcional e não era a causa.

A configuração de produção foi corrigida em 2026-09-15. Anonymous Sign-Ins foi habilitado somente depois da aplicação de guardrails no banco e nas Server Actions. Um Auth Hook permite exclusivamente a criação anônima necessária ao kiosk e mantém cadastro permanente por autoatendimento bloqueado. Usuários anônimos não podem enumerar perfis, criar estabelecimentos, entrar em fluxos de equipe/admin ou usar uploads; checkout de kiosk continua exigindo a credencial revogável do dispositivo e o limite de requisições do servidor. O fluxo completo foi revalidado no AVD API 36/WebView 143 sem a mensagem de erro.

## Arquivos alterados

- `.github/workflows/release-signed-apk.yml`
- `README.md`
- `AUDIT.md`
- `settings.gradle.kts`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/ontimestack/webkiosk/**`
- `app/src/main/res/values*/**`
- `app/src/main/res/xml/**`
- `app/src/main/res/mipmap*/**`
- `fastlane/metadata/android/en-US/**`

Todo o código Kotlin foi movido de `org/screenlite/webkiosk` para `com/ontimestack/webkiosk`. Classes, traduções e imagens antigas removidas aparecem como exclusões no diff Git e estão preservadas no histórico original.
