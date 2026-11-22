*non est reditus*

# Deploy e rilasci ASD

## Sito (Vercel)

- URL: https://asd-eight-rust.vercel.app
- Progetto Vercel `asd` (`prj_CqMaj8OnZuF7C1fVqzJ5jX0OXDSC`), scope `max1969phps-projects`
  (`team_iKS9kN30n9GZRqtQTvCM65oQ`), stesso account di rubik e accordi.
- Collegato a GitHub `max1969php/asd`, branch di produzione `main`, **Root Directory `site`**,
  framework nessuno, nessun comando di build. Push su `main` → deploy di produzione.
- La GitHub App di Vercel (installazione `19957084`) ha accesso solo ai repo scelti: per un repo
  nuovo va aggiunto a mano da https://github.com/settings/installations/19957084 (né l'API di
  Vercel né il token di `gh` possono farlo).
- CLI: non installata, si usa `pnpm dlx vercel@latest …` (lenta al primo avvio, ~2 min); token in
  `~/.local/share/com.vercel.cli/auth.json`. Un deploy a mano da CLI va fatto **dentro `site/`**
  (`--scope max1969phps-projects`), mai dalla radice del repo: caricherebbe anche i file non
  versionati (es. `client_secret*.json`). Di norma non serve: basta il push.
- Primo deploy (2026-10-22, `96f7e76`) fatto da CLI; dal commit `e82e258` i deploy partono da Git
  (verificato: deployment `source: git`, READY, in produzione).

## Tre versioni (minimal e full dalla 0.4.0, tube dalla 0.5.0, 2026-10-23)

- **full** (`com.asd.player`, "ASD+"): Drive con l'account del telefono (client OAuth Android del
  progetto del titolare, SHA-1 della chiave debug) e TUBO, che salva nel telefono e su Drive.
  Solo per il titolare: **non** va su GitHub Releases né sul sito; l'APK universale si copia in
  `C:\Users\massi\Downloads\` (`app-full-universal-release.apk`).
  Dalla 0.4.1 R8 non tocca il codice della full (`proguard-ytdlp.pro`, prima `proguard-full.pro`: niente shrink/rinomina/
  ottimizzazione, ~45 MB): con R8 attivo il TUBO andava in crash al download, mentre la build non
  ottimizzata da 56 MB funzionava.
- **minimal** (`com.asd.player.minimal`, "ASD"): solo cartella del telefono, niente permesso
  INTERNET, ~3 MB. È quella del sito (Release GitHub, asset `asd.apk`) e quella destinata a Play Store.
- **tube** (`com.asd.player.tube`, "ASD Tubo"): cartella del telefono + TUBO come la full, ma senza
  Drive: il TUBO salva solo nel telefono. Permesso INTERNET, ~45 MB universale, stesso R8 della
  full. Sul sito (asset `asd-tubo.apk`), non per Play Store (scaricare da YouTube è contro le regole).
- La prova del "progetto Google dell'utente" (client App desktop, login nel browser con server di
  loopback) è stata abbandonata il 2026-10-23 in favore delle due versioni; il codice è nella storia
  git (commit e1c8f2d, 5f20c9f, c42a191).

## ASD Desktop (GitHub Releases)

- Asset `asd-desktop.deb` nella stessa Release degli APK (bottone del sito
  `releases/latest/download/asd-desktop.deb`): `./gradlew :desktop:packageAsdDeb`, poi copia con
  quel nome. Versione propria (`desktopVersion` in `desktop/build.gradle.kts`, 0.2.0).
- Il `.deb` è costruito da `desktop/packaging/make-deb.sh` con `dpkg-deb --root-owner-group`
  (il `packageDeb` di Compose vorrebbe fakeroot e non permette di dichiarare le dipendenze).
  Dipendenze: `gstreamer1.0-plugins-base`, `-good`, `gstreamer1.0-libav` (AAC); consigliato `zenity`.

## APK (GitHub Releases)

- Release con `asd.apk` (minimal universale) e, dalla 0.5.0, `asd-tubo.apk` (tube universale).
  I bottoni del sito usano `releases/latest/download/<nome>`: i nomi degli asset non vanno cambiati.
- Build una alla volta: tre flavor release nello stesso `gradlew` sono stati uccisi per memoria
  (exit 137).
- Release vecchie: v0.1.0, v0.2.0 (APK per CPU), v0.3.0 (con anche l'universale) contengono la
  versione con Drive e TUBO; pre-release v0.3.0-test e branch `drive-own` della prova Drive.
- Galaxy A13 SM-A137F del titolare: Android a 32 bit, rifiuta gli APK solo arm64 ("non compatibile").
  Per la full usare l'universale o armv7.
- Firma: chiave debug di WSL, SHA-1 `98:D5:A1:C8:C7:A7:95:2C:74:F4:66:A3:5E:04:56:54:10:64:7D:0C`
  (quella registrata nel client OAuth Android di Google Cloud).
- **Attenzione, PC Ubuntu**: la prima build (2026-11-22) ha creato una chiave debug nuova,
  `~/.android/debug.keystore` con SHA-1 `28:A4:E8:02:04:5F:30:86:BB:BE:6F:C4:3F:24:68:D5:E9:AC:B1:3D`.
  Gli APK firmati così **non si installano sopra** quelli già sul telefono e nella full **Drive
  non va**: non rilasciarli. Rimedio: copiare il `debug.keystore` di WSL
  (`\\wsl$\<distro>\home\<utente>\.android\debug.keystore` da Windows) al posto di quello nuovo e
  ricompilare; in alternativa registrare la nuova SHA-1 come altro client Android su Google Cloud
  (ma gli aggiornamenti sopra le versioni vecchie restano impossibili). Verifica con
  `apksigner verify --print-certs` (serve `JAVA_HOME/bin` nel PATH).
- `gh` autenticato come `max1969php` (scope `repo`, `workflow`).

## Play Store (minimal) — da fare

- Serve una **chiave di firma propria** (non la debug) e l'AAB: `./gradlew bundleMinimalRelease`.
  Decidere prima di pubblicare sul sito APK firmati con quella chiave: chi installa dal sito un APK
  firmato con la chiave debug non potrà aggiornare da Play Store (firma diversa), e viceversa.
  Con Play App Signing si può caricare la propria chiave di firma, così sito e Play coincidono.
- `targetSdk` oggi è 34: Play chiede almeno 35 per le app nuove (verificare il requisito in vigore).
- Servono una privacy policy raggiungibile da URL (può stare sul sito: "nessun dato raccolto,
  nessuna rete") e la scheda "Sicurezza dei dati" (nessun dato raccolto né condiviso).
- `applicationId` `com.asd.player.minimal` diventa definitivo al primo caricamento su Play.

## Build

Dal 2026-11-22 si compila sul PC Ubuntu 26.04 (14 GB di RAM, prima WSL2 con ~7,4 GB).
JDK 17 (Temurin) e Android SDK (cmdline-tools, platform 34, build-tools 34.0.0, platform-tools) in
`~/.local/share/asd-build-tools`, installati a mano (licenze SDK accettate):

    S=$HOME/.local/share/asd-build-tools; export JAVA_HOME=$(ls -d $S/jdk-17*) ANDROID_HOME=$S/sdk
    ./gradlew --no-daemon -q testMinimalDebugUnitTest   # poi testTubeDebugUnitTest, testFullDebugUnitTest
    ./gradlew --no-daemon -q assembleMinimalRelease     # poi assembleTubeRelease, assembleFullRelease

Una variante per volta: tutte insieme (`testDebugUnitTest assembleRelease`) finiscono la memoria
di WSL (exit 137, vedi REGISTRO).

## Sviluppi futuri: traffico molto forte

Oggi gli APK si scaricano da **GitHub Releases** (i bottoni del sito puntano a
`releases/latest/download/…`), non da Vercel: su Vercel passano solo le pagine e gli script, pochi
KB a visita. Piano Vercel Hobby: 100 GB/mese di traffico; oltre il limite non si paga (niente
consumo a pagamento sull'Hobby, nessun metodo di pagamento sull'account): il sito viene rallentato
e poi messo in pausa. GitHub non pubblica limiti di banda per gli asset delle release (solo 2 GiB
per file).

Se i download crescessero molto, l'alternativa valutata è **Cloudflare R2** (object storage
compatibile S3, **egress gratuito**). Stime dell'utente (2026-10-22), con un APK da ~25 MB:

| Download/mese | Traffico | Soluzione | Costo stimato |
|---|---|---|---|
| 1.000 | ~25 GB | GitHub Releases o R2 | 0 € |
| 10.000 | ~250 GB | R2 | ~0,36 €/mese |
| 100.000 | ~2,5 TB | R2 | < 1 €/mese (~0,40–0,50 €) |
| 1.000.000 | ~25 TB | R2 (o store alternativi) | ~1,35–2 €/mese |

- R2: piano gratuito con 10 GB di storage; si pagano solo le letture (operazioni Class B, ~0,36 $
  per milione). Su AWS S3 gli stessi 2,5 TB di egress costerebbero centinaia di dollari.
- R2 va reso pubblico collegando un dominio (o con un Worker davanti).
- Su GitHub, volumi molto alti potrebbero essere visti come uso del repo come CDN di file pesanti
  (termini di uso accettabile). Le soglie citate nelle stime (es. "gratis per i primi 1.000–3.000
  download") **non** sono documentate da GitHub: da verificare prima di decidere.
- Per passare a R2: caricare gli APK nel bucket con gli stessi nomi e cambiare i link
  `releases/latest/download/…` del sito (bottone in `site/index.html`, link armv7 nei testi
  `i18n/*.json`), più `site/README.md`.
