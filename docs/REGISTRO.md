*non est reditus*

# Registro ASD

Ciò che git non vede: decisioni, prove, sistemi esterni. Una riga per voce, più recente in alto.

2026-11-22  prodotto   volume uniforme: obiettivo da -18 a -14 LUFS e pezzi deboli alzati (LoudnessEnhancer, max +12 dB); dal titolare: ASD a volume 9 = Spotify a 5 e alcuni file restavano bassi (→ ROADMAP.md)
2026-11-22  documenti  motto in cima ai documenti; Struttura e "Fuori dall'MVP" del README allineati al codice; DEPLOY: build una variante alla volta
2026-11-08  build      assembleFullRelease: "Kotlin daemon terminated unexpectedly on startup" (memoria), Gradle ha compilato senza demone, build ok
2026-11-08  build      l'errore "Unresolved reference TubeDownloader" della full non si riproduce (tube poi full, con modifica ad AppContainer): probabile cache Kotlin rovinata da una build uccisa per memoria (exit 137); rimedio se torna: cancellare app/build/kotlin/compileFull*Kotlin
2026-11-08  ambiente   WSL2 vede ~7,4 GB di RAM (metà dei 16 GB del PC): build sempre una alla volta
2026-11-08  prodotto   fasi 2, 3 e 4 nella stessa 0.7.0 della fase 1 (mai rilasciata da sola) (→ ROADMAP.md)
2026-11-07  prodotto   fase 1 nella 0.7.0; regola numeri allargata (solo numeri di traccia si tolgono, "883" resta) (→ ROADMAP.md)
2026-11-07  build      testFullDebugUnitTest falliva con "Unresolved reference TubeDownloader": cache Kotlin incrementale vecchia, risolto cancellando app/build/kotlin/compileFullDebugKotlin
2026-11-07  prodotto   scartati anche loop di cartella/mixtape (c'è repeat) e mixtape .m3u ("sembra social")
2026-11-07  prodotto   scelte sulle 23 proposte di miglioramento: fasi e dettagli (→ ROADMAP.md)
2026-11-07  test       0.6.0 (ROBA per cartelle) provata dal titolare sul telefono: "tutto funziona correttamente"
2026-11-03  traduzioni asd-sito-da-tradurre-it.json non più nei Download; tabs.roba in asd-sito-da-tradurre-2-it.json (→ TRADUZIONI.md)
2026-10-23  prodotto   tre versioni: full solo per il titolare (Download di Windows), minimal e tube sul sito; progetto Google per utente abbandonato (→ DEPLOY.md)
