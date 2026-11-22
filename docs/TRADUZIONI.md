*non est reditus*

# Traduzioni ASD

- Sorgente: `app/src/main/res/values/strings.xml` (italiano). Ogni lingua è `values-<tag>/strings.xml`
  (`zh-CN` → `values-zh-rCN`). Stesse chiavi, stessi segnaposto (`%1$s`, `%1$d`, `%%`).
- Generatore: `~/.local/share/asd-build-tools/i18n/gen.py` legge `tr/<tag>.txt` (`chiave=valore`,
  `\n` per a capo, `slogan_1..8` per l'array), controlla chiavi e segnaposto, scrive gli XML e
  rigenera `res/xml/locales_config.xml`. In alternativa si copiano dentro gli XML già tradotti.
- Dopo aver aggiunto una lingua: metterla in `L10n.SUPPORTED`.

## Stato (2026-10-22)

App e sito: tutte le 38 lingue (it più am ar bg cs da de el en es et fa fi fr ga ha hi hr hu ig lt lv
mt nb nl pl pt ro sk sl so sv sw ti uk wo yo zh-CN). Le 23 nuove dell'app e i JSON del sito vengono
dal servizio esterno (cartelle `asd-strings-24-lingue/` e `istruzioni-i18/`, non versionate); il
loro nb ha sostituito il precedente (correggeva refusi).

Da far rivedere a un madrelingua, se possibile: wo, ha, yo, ig, am, ti, so. Sul sito la chiave
`yt.title` ("// yt-dlp SUL COMPUTER") mancava nei file del servizio ed è tradotta da Claude in
tutte le lingue: da ricontrollare per le stesse lingue africane.

Nomi delle lingue nella select dell'app: tabella `L10n.NAMES` (uguali a `LANGUAGES` in
`site/i18n.js`); una lingua nuova va aggiunta in entrambe.

## File dal servizio esterno

Arrivano in `trads/asd-strings-<codice>.xml` (non versionata). Prima di copiarli in
`values-<codice>/strings.xml`: togliere eventuali recinti markdown (```` ```xml ````), controllare chiavi e
segnaposto (`%1$d` resta `%1$d`, non `%1$s`), poi aggiungere il codice a `L10n.SUPPORTED` e rilanciare
`gen.py` per `locales_config.xml`. Norvegese: il servizio lo chiama `no`, Android vuole `values-nb`.

## Da tradurre (2026-10-23)

- App, versione minimal: `app/src/minimal/res/values*/strings.xml` (intro, manifesto, volume,
  libreria vuota senza Drive/TUBO) li genera `~/.local/share/asd-build-tools/i18n/minimal_strings.py
  <repo>` dalle traduzioni di `src/main` più tre frasi nuove tradotte da Claude ("Niente rete…",
  "ASD non cancella mai i tuoi file: per toglierne uno usa il telefono", "Scegli UNA cartella del
  telefono…"): da rivedere almeno wo, ha, yo, ig, am, ti, so. Se cambiano quei testi in `src/main`,
  rilanciare lo script.
  Lo stesso script scrive anche `app/src/tube/res/values*/strings.xml` (versione tube: manifesto
  con la rete per il TUBO); il nome "ASD Tubo" sta a parte in `src/tube/res/values/app_name.xml`.
- App e sito: "e nessuno deve spiarti mentre li senti" (`onboarding_intro`, `hero.lead`) tradotto da
  Claude: da rivedere almeno wo, ha, yo, ig, am, ti, so.
- Sito: il sito ora descrive solo la minimal. Chiavi cambiate o nuove solo in `it.json` (nelle altre
  lingue sono state tolte e si vedono in italiano): `asd.intro`, `start.text`, `tabs.roba`,
  `tabs.setup`, `rules.nodelete`, `yt.intro`, `scripts.output`, `nav.phone`, `phone.*`; dalla
  0.5.0 anche `hero.download`, `hero.download_tube`, `hero.versions`, `tabs.tubo` (ASD Tubo). Elenco
  pronto per il servizio: `asd-sito-da-tradurre-it.json` nei Download di Windows. Tornati i file,
  vanno fusi nei `site/i18n/<lingua>.json` esistenti (non sostituiti).
- Sito, 0.7.0 (2026-11-08): `tabs.roba`, `tabs.setup` riscritte e `tabs.player` nuova, solo in
  `it.json` (tolte dalle altre lingue). File `asd-sito-da-tradurre-3-it.json` nei Download (il
  `-2` non c'era più).
- App, 0.7.0 (2026-11-08): 26 chiavi nuove delle fasi 2–4 (`info_*`, `lyrics_*`, `silence_*`,
  `eq_*`, `view_*`, `duplicates`) tradotte da Claude e inserite dopo `sleep_off` in tutti i
  `values-*/strings.xml`: da rivedere almeno wo, ha, yo, ig, am, ti, so.
- App, 0.7.0 (2026-11-07): `sleep_timer`, `sleep_minutes`, `sleep_end_of_song`, `sleep_off` tradotte da
  Claude e inserite a mano nei `values-*/strings.xml` (in `tr/` non ci sono più i sorgenti di
  `gen.py`): da rivedere almeno wo, ha, yo, ig, am, ti, so.
- Sito, 0.6.0 (2026-11-03): `tabs.roba` riscritta per le cartelle in ROBA, tolta dalle altre lingue.
  File a parte `asd-sito-da-tradurre-2-it.json` nei Download (il primo non c'era più).
- App e sito, 2026-11-22: `volume_hint` (app, main/minimal/tube) e `volume.text` (sito) riscritti in
  italiano e inglese (-14 LUFS, i pezzi deboli si alzano). Nelle altre lingue è cambiato solo il
  numero (-18 → -14 LUFS): la frase "i pezzi più forti vengono abbassati, quindi alza un po' il
  volume del telefono" è rimasta e va ritradotta dal testo italiano nuovo.
- Sito, 2026-11-22: chiavi nuove `hero.download_desktop` e `hero.versions_desktop` (ASD Desktop),
  solo in `it.json`: nelle altre lingue si vedono in italiano finché non vengono tradotte.
- ASD Desktop 0.3.0, 2026-11-22: usa gli stessi `values-*/strings.xml` dell'app (main, poi minimal),
  copiati nel jar a ogni build: una traduzione nuova dell'app arriva anche al desktop. Le chiavi
  solo desktop (`desktop_*`) stanno in `desktop/src/main/resources/i18n/desktop/values*/strings.xml`:
  quelle generali tradotte da Claude in tutte le 38 lingue (da far rivedere, come le altre); quelle
  del TUBO e `desktop_missing_aac` solo in it/en, nelle altre lingue si vedono in inglese.
  Attenzione: il `volume_hint` ancora da ritradurre (vedi sopra) si vede anche nel desktop.
